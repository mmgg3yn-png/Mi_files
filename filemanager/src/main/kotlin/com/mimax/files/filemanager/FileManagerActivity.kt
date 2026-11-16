package com.mimax.files.filemanager

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.mimax.files.archive.ArchiveTools
import com.mimax.files.common.*
import java.io.File
import java.util.concurrent.Executors

class FileManagerActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var hidden = false
    private var sort = 0 // 0 name, 1 size, 2 date
    private lateinit var left: Pane
    private lateinit var right: Pane
    private lateinit var active: Pane
    private lateinit var tabLayout: TabLayout
    private val tabs = mutableListOf<Array<String>>()
    private var tabIdx = 0
    private var syncing = false

    private val mediaExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "mp3", "flac",
        "ogg", "opus", "wav", "aac", "m4a", "wma", "amr")
    private val romExt = setOf("img", "bin", "mtz")

    class VH(val row: LinearLayout, val icon: TextView, val name: TextView, val info: TextView) : RecyclerView.ViewHolder(row)

    inner class Pane(start: File) {
        var dir: File = start
        var items: List<File> = emptyList()
        val selected = linkedSetOf<File>()
        val pathView = TextView(this@FileManagerActivity).apply { setPadding(16, 12, 16, 12); textSize = 12f; maxLines = 1 }
        val rv = RecyclerView(this@FileManagerActivity).apply { layoutManager = LinearLayoutManager(context) }
        val box = LinearLayout(this@FileManagerActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(pathView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(rv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        val adapter = object : RecyclerView.Adapter<VH>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
                val c = p.context
                val row = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 14, 16, 14)
                    layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
                val icon = TextView(c).apply { textSize = 22f; setPadding(0, 0, 20, 0) }
                val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                val name = TextView(c).apply { textSize = 15f; maxLines = 1 }
                val info = TextView(c).apply { textSize = 11f; alpha = 0.7f }
                col.addView(name); col.addView(info)
                row.addView(icon); row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                return VH(row, icon, name, info)
            }
            override fun onBindViewHolder(h: VH, i: Int) {
                val f = items[i]
                h.icon.text = if (f.isDirectory) "📁" else "📄"
                h.name.text = f.name
                h.info.text = (if (f.isFile) Fmt.size(f.length()) + "  •  " else "") + Fmt.date(f.lastModified())
                h.row.setBackgroundColor(if (f in selected) 0x553399FF else 0)
                h.row.setOnClickListener { active = this@Pane; onItem(this@Pane, f) }
                h.row.setOnLongClickListener { active = this@Pane; toggle(f); true }
            }
        }
        init {
            rv.adapter = adapter
            pathView.setOnClickListener { active = this; highlight() }
            load(start)
        }
        private fun toggle(f: File) { if (!selected.add(f)) selected.remove(f); adapter.notifyDataSetChanged() }
        fun select(f: File) = toggle(f)
        fun load(d: File) {
            val list = d.listFiles()
            if (list == null) { toast(tr(this@FileManagerActivity, "Cannot read ${d.path}", "لا يمكن قراءة ${d.path}")); return }
            dir = d; selected.clear()
            val cmp: Comparator<File> = when (sort) {
                1 -> compareByDescending<File> { it.length() }
                2 -> compareByDescending<File> { it.lastModified() }
                else -> compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            items = list.filter { hidden || !it.name.startsWith(".") }
                .sortedWith(compareByDescending<File> { it.isDirectory }.then(cmp))
            pathView.text = d.path
            adapter.notifyDataSetChanged()
            onNavigated(this)
        }
        fun up() { dir.parentFile?.let { load(it) } }
        fun reload() = load(dir)
    }

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hidden = Prefs.hidden(this)
        requestStorage()
        val start = intent.getStringExtra("path")?.let { File(it) }?.takeIf { it.isDirectory }
            ?: Environment.getExternalStorageDirectory()
        left = Pane(start); right = Pane(start); active = left
        tabs.add(arrayOf(start.path, start.path))

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabLayout = TabLayout(this).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            addTab(newTab().setText(start.name.ifEmpty { "/" }))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(t: TabLayout.Tab) {
                    tabIdx = t.position; syncing = true
                    left.load(File(tabs[tabIdx][0])); right.load(File(tabs[tabIdx][1])); syncing = false
                }
                override fun onTabUnselected(t: TabLayout.Tab?) {}
                override fun onTabReselected(t: TabLayout.Tab?) {}
            })
        }
        root.addView(tabLayout, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(toolbar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val panes = LinearLayout(this).apply { orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val lp = { if (land) LinearLayout.LayoutParams(0, MATCH_PARENT, 1f) else LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f) }
        panes.addView(left.box, lp()); panes.addView(right.box, lp())
        right.box.visibility = View.GONE
        root.addView(panes, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        highlight()
    }

    private fun toolbar(): HorizontalScrollView {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun b(en: String, ar: String, f: () -> Unit) = r.addView(Ui.button(this, tr(this, en, ar), f))
        b("Places", "الأماكن") { places() }
        b("↑ Up", "↑ أعلى") { active.up() }
        b("New", "جديد") { newItem() }
        b("Copy", "نسخ") { transfer(false) }
        b("Move", "نقل") { transfer(true) }
        b("Rename", "إعادة تسمية") { rename() }
        b("Delete", "حذف") { delete() }
        b("Extract", "استخراج") { extract() }
        b("Compress", "ضغط") { compress() }
        b("Info", "خصائص") { info() }
        b("Search", "بحث") { search() }
        b("Sort", "فرز") { sortDialog() }
        b("Hidden", "المخفية") { hidden = !hidden; Prefs.setHidden(this, hidden); refreshAll() }
        b("Dual pane", "لوحتان") { right.box.visibility = if (right.box.visibility == View.GONE) View.VISIBLE else View.GONE }
        b("+ Tab", "+ تبويب") { addTab() }
        b("✕ Tab", "✕ تبويب") { closeTab() }
        return HorizontalScrollView(this).apply { addView(r) }
    }

    // ------------------------------------------------------------------ helpers
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun highlight() {
        val on = 0x33008899; 
        left.pathView.setBackgroundColor(if (active === left) on else 0)
        right.pathView.setBackgroundColor(if (active === right) on else 0)
    }
    private fun other() = if (active === left) right else left
    private fun refreshAll() { left.reload(); right.reload(); highlight() }

    private fun onNavigated(p: Pane) {
        if (!::tabLayout.isInitialized || syncing) return
        tabs[tabIdx][if (p === left) 0 else 1] = p.dir.path
        if (p === left) tabLayout.getTabAt(tabIdx)?.text = p.dir.name.ifEmpty { "/" }
    }
    private fun addTab() {
        val d = active.dir.path
        tabs.add(arrayOf(d, d))
        tabLayout.addTab(tabLayout.newTab().setText(active.dir.name.ifEmpty { "/" }), true)
    }
    private fun closeTab() {
        if (tabs.size <= 1) return
        val i = tabIdx
        tabs.removeAt(i); tabLayout.removeTabAt(i)
    }

    private fun sel(): List<File> {
        val l = active.selected.toList()
        if (l.isEmpty()) toast(tr(this, "Long-press items to select them", "اضغط مطولاً لتحديد العناصر"))
        return l
    }

    private fun work(title: String, job: () -> Unit) {
        val dlg = AlertDialog.Builder(this).setMessage(title).setCancelable(false).create()
        dlg.show()
        io.execute {
            var err: String? = null
            try { job() } catch (t: Throwable) { err = t.message ?: t.toString() }
            ui.post { dlg.dismiss(); err?.let { toast(it) }; refreshAll() }
        }
    }

    private fun ask(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); setSelectAllOnFocus(true) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton(android.R.string.ok) { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun unique(f: File): File {
        if (!f.exists()) return f
        var i = 1
        val base = f.nameWithoutExtension; val ext = f.extension.let { if (it.isEmpty()) "" else ".$it" }
        while (true) { val c = File(f.parentFile, "$base ($i)$ext"); if (!c.exists()) return c; i++ }
    }

    // ------------------------------------------------------------------ permissions
    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setMessage(tr(this, "Mi_files needs 'All files access' to manage storage.", "يحتاج Mi_files إلى صلاحية 'الوصول إلى جميع الملفات' لإدارة التخزين."))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                        } catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); refreshAll()
    }
    override fun onResume() { super.onResume(); if (::left.isInitialized) refreshAll() }

    // ------------------------------------------------------------------ actions
    private fun places() {
        val ps = listOf(
            tr(this, "Internal storage", "التخزين الداخلي") to Environment.getExternalStorageDirectory(),
            "Termux HOME" to File(filesDir, "home"),
            "Termux PREFIX (usr)" to File(filesDir, "usr"),
            tr(this, "App files", "ملفات التطبيق") to filesDir,
            "/" to File("/")
        )
        AlertDialog.Builder(this).setItems(ps.map { it.first }.toTypedArray()) { _, i ->
            val d = ps[i].second; d.mkdirs(); active.load(d)
        }.show()
    }

    private fun newItem() {
        AlertDialog.Builder(this).setItems(arrayOf(tr(this, "Folder", "مجلد"), tr(this, "File", "ملف"))) { _, w ->
            ask(tr(this, "Name", "الاسم"), "") { n ->
                if (n.isNotEmpty()) work("…") {
                    val f = File(active.dir, n)
                    if (w == 0) f.mkdirs() else f.createNewFile()
                }
            }
        }.show()
    }

    private fun transfer(move: Boolean) {
        val files = sel(); if (files.isEmpty()) return
        if (right.box.visibility == View.GONE) {
            right.box.visibility = View.VISIBLE
            toast(tr(this, "Choose the destination in the other pane, then press again", "اختر الوجهة في اللوحة الأخرى ثم اضغط مرة أخرى"))
            return
        }
        val dst = other().dir
        work("…") {
            files.forEach { f ->
                val t = unique(File(dst, f.name))
                if (f.isDirectory && t.canonicalPath.startsWith(f.canonicalPath + File.separator)) throw IllegalArgumentException("Cannot copy into itself")
                if (move && f.renameTo(t)) return@forEach
                if (f.isDirectory) f.copyRecursively(t, false) else f.copyTo(t)
                if (move) f.deleteRecursively()
            }
        }
    }

    private fun rename() {
        val f = sel().firstOrNull() ?: return
        ask(tr(this, "New name", "الاسم الجديد"), f.name) { n ->
            if (n.isNotEmpty()) work("…") { if (!f.renameTo(File(f.parentFile, n))) throw java.io.IOException("Rename failed") }
        }
    }

    private fun delete() {
        val files = sel(); if (files.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(tr(this, "Delete ${files.size} item(s)?", "حذف ${files.size} عنصر؟"))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                work("…") { files.forEach { if (!it.deleteRecursively()) throw java.io.IOException("Cannot delete ${it.name}") } }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun extract() {
        val files = sel().filter { ArchiveTools.detect(it) != null }
        if (files.isEmpty()) { toast(tr(this, "Select an archive", "حدد أرشيفاً")); return }
        val dir = active.dir
        work(tr(this, "Extracting…", "جارٍ الاستخراج…")) {
            files.forEach { f -> ArchiveTools.extract(f, unique(File(dir, f.nameWithoutExtension + "_extracted"))) }
        }
    }

    private fun compress() {
        val files = sel(); if (files.isEmpty()) return
        val fmts = ArchiveTools.creatable
        AlertDialog.Builder(this).setItems(fmts.map { it.label }.toTypedArray()) { _, i ->
            val fmt = fmts[i]
            val def = (if (files.size == 1) files[0].name else "archive") + "." + fmt.ext
            ask(tr(this, "Archive name", "اسم الأرشيف"), def) { n ->
                if (n.isNotEmpty()) work(tr(this, "Compressing…", "جارٍ الضغط…")) {
                    ArchiveTools.compress(files, unique(File(active.dir, n)), fmt)
                }
            }
        }.show()
    }

    private fun info() {
        val f = sel().firstOrNull() ?: return
        work("…") {
            val size = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
            val count = if (f.isDirectory) f.walkTopDown().count() - 1 else 0
            val text = buildString {
                appendLine("Path: ${f.absolutePath}")
                appendLine("Type: ${if (f.isDirectory) "Directory ($count items)" else f.extension.ifEmpty { "file" }}")
                appendLine("Size: ${Fmt.size(size)} ($size B)")
                appendLine("Modified: ${Fmt.date(f.lastModified())}")
                appendLine("Read/Write/Exec: ${f.canRead()}/${f.canWrite()}/${f.canExecute()}")
                appendLine("Hidden: ${f.isHidden || f.name.startsWith(".")}")
                runCatching { appendLine("Canonical: ${f.canonicalPath}") }
            }
            ui.post { AlertDialog.Builder(this).setTitle(f.name).setMessage(text).setPositiveButton(android.R.string.ok, null).show() }
        }
    }

    private fun search() {
        ask(tr(this, "Search in this folder", "ابحث في هذا المجلد"), "") { q ->
            if (q.isEmpty()) return@ask
            val root = active.dir
            work(tr(this, "Searching…", "جارٍ البحث…")) {
                val hits = root.walkTopDown().onEnter { true }.filter { it != root && it.name.contains(q, true) }.take(500).toList()
                ui.post {
                    if (hits.isEmpty()) toast(tr(this, "No results", "لا نتائج")) else
                        AlertDialog.Builder(this).setTitle("${hits.size}")
                            .setItems(hits.map { it.relativeTo(root).path }.toTypedArray()) { _, i ->
                                val h = hits[i]; if (h.isDirectory) active.load(h) else h.parentFile?.let { active.load(it) }
                            }.show()
                }
            }
        }
    }

    private fun sortDialog() {
        val n = arrayOf(tr(this, "Name", "الاسم"), tr(this, "Size", "الحجم"), tr(this, "Date", "التاريخ"))
        AlertDialog.Builder(this).setSingleChoiceItems(n, sort) { d, i -> sort = i; refreshAll(); d.dismiss() }.show()
    }

    // ------------------------------------------------------------------ open
    private fun onItem(p: Pane, f: File) {
        if (p.selected.isNotEmpty()) { p.select(f); return }
        if (f.isDirectory) { p.load(f); return }
        val ext = f.extension.lowercase()
        when {
            ext == "apk" || ext == "dex" -> AlertDialog.Builder(this).setItems(arrayOf("APK Tools", tr(this, "View as ZIP", "عرض كـ ZIP"))) { _, i ->
                if (i == 0 || ext == "dex") Nav.start(this, Nav.APK, f.path) else showArchive(f)
            }.show()
            ext in romExt && ext != "mtz" -> Nav.start(this, Nav.ROM, f.path)
            ArchiveTools.detect(f) != null -> showArchive(f)
            ext in mediaExt -> {
                val list = f.parentFile?.listFiles()?.filter { it.isFile && it.extension.lowercase() in mediaExt }?.sortedBy { it.name } ?: listOf(f)
                startActivity(Nav.intent(this, Nav.PLAYER).apply {
                    putExtra("paths", list.map { it.path }.toTypedArray()); putExtra("index", list.indexOf(f).coerceAtLeast(0))
                })
            }
            else -> openExternal(f)
        }
    }

    private fun showArchive(f: File) {
        AlertDialog.Builder(this).setTitle(f.name)
            .setItems(arrayOf(tr(this, "List contents", "عرض المحتويات"), tr(this, "Extract here", "استخراج هنا"),
                tr(this, "Open in ROM Tools", "فتح في أدوات ROM"))) { _, i ->
                when (i) {
                    0 -> work("…") {
                        val e = ArchiveTools.list(f)
                        val t = e.take(2000).joinToString("\n") { (if (it.isDir) "📁 " else "") + it.name + if (it.size >= 0 && !it.isDir) "  [" + Fmt.size(it.size) + "]" else "" }
                        ui.post { AlertDialog.Builder(this).setTitle("${e.size}").setMessage(t).setPositiveButton(android.R.string.ok, null).show() }
                    }
                    1 -> work(tr(this, "Extracting…", "جارٍ الاستخراج…")) {
                        ArchiveTools.extract(f, unique(File(f.parentFile, f.nameWithoutExtension + "_extracted")))
                    }
                    else -> Nav.start(this, Nav.ROM, f.path)
                }
            }.show()
    }

    private fun openExternal(f: File) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
        } catch (e: Exception) { toast(tr(this, "No app can open this file", "لا يوجد تطبيق لفتح هذا الملف")) }
    }
}
