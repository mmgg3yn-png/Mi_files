package com.mimax.files.apktools

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mimax.files.archive.ArchiveTools
import com.mimax.files.common.Ui
import com.mimax.files.common.tr
import java.io.File
import java.util.concurrent.Executors

class ApkToolsActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var path: EditText
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "APK Tools"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        path = EditText(this).apply {
            hint = tr(this@ApkToolsActivity, "Path to .apk / .dex (or open from File Manager)", "مسار ملف .apk / .dex (أو افتحه من مدير الملفات)")
            setText(intent.getStringExtra("path") ?: "/sdcard/")
        }
        root.addView(path, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun b(t: String, f: (File) -> String) = row.addView(Ui.button(this, t) { run(t, f) })
        b(tr(this, "Info", "معلومات")) { ApkInspector.info(this, it) }
        b("DEX") { if (it.extension.lowercase() == "dex") ApkInspector.dexInfoOfFile(it) else ApkInspector.dexInfo(it) }
        b("Manifest XML") { ApkInspector.decodeXml(this, it, "AndroidManifest.xml") }
        row.addView(Ui.button(this, tr(this, "Entries / XML", "المحتويات / XML")) { entries() })
        b(tr(this, "Extract all", "استخراج الكل")) {
            val d = File(it.parentFile, it.nameWithoutExtension + "_extracted")
            ArchiveTools.extract(it, d); "Extracted to ${d.path}"
        }
        root.addView(HorizontalScrollView(this).apply { addView(row) })
        out = Ui.mono(this)
        root.addView(ScrollView(this).apply { addView(out) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun file() = File(path.text.toString().trim())

    private fun run(label: String, f: (File) -> String) {
        val file = file()
        out.text = "…"
        io.execute {
            val r = try { f(file) } catch (t: Throwable) { "Error: ${t.message ?: t}" }
            runOnUiThread { out.text = r }
        }
    }

    private fun entries() {
        val apk = file()
        io.execute {
            try {
                val names = ApkInspector.entries(apk)
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("${names.size}")
                        .setItems(names.toTypedArray()) { _, i ->
                            val n = names[i]
                            if (n.endsWith(".xml")) run(n) { ApkInspector.decodeXml(this, apk, n) }
                            else run(n) {
                                val dir = File(apk.parentFile, apk.nameWithoutExtension + "_extracted")
                                "Extracted: " + ApkInspector.extractEntry(apk, n, dir).path
                            }
                        }.show()
                }
            } catch (t: Throwable) { runOnUiThread { out.text = "Error: ${t.message}" } }
        }
    }
}
