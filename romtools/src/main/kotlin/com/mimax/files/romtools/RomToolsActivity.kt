package com.mimax.files.romtools

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mimax.files.archive.ArchiveTools
import com.mimax.files.common.Ui
import com.mimax.files.common.tr
import java.io.File
import java.util.concurrent.Executors

class RomToolsActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var path: EditText
    private lateinit var out: TextView
    private lateinit var actions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "ROM Tools"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        path = EditText(this).apply {
            hint = tr(this@RomToolsActivity, "Path to image (boot.img, super.img, …)", "مسار الملف (boot.img, super.img, …)")
            setText(intent.getStringExtra("path") ?: "/sdcard/")
        }
        root.addView(path, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(Ui.button(this, tr(this, "Analyze", "تحليل")) { analyze() })
        actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply { addView(actions) })
        out = Ui.mono(this)
        root.addView(ScrollView(this).apply { addView(out) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        if (intent.hasExtra("path")) analyze()
    }

    private fun file() = File(path.text.toString().trim())

    private fun analyze() {
        val f = file(); out.text = "…"; actions.removeAllViews()
        io.execute {
            try {
                val kind = RomInspector.detect(f); val text = RomInspector.info(f)
                runOnUiThread {
                    out.text = text
                    val dir = File(f.parentFile, f.nameWithoutExtension + "_out")
                    when (kind) {
                        Kind.BOOT -> act(tr(this, "Extract boot", "استخراج boot")) { RomInspector.extractBoot(f, dir).joinToString("\n", "Output: ${dir.path}\n") }
                        Kind.VENDOR_BOOT -> act(tr(this, "Extract vendor_boot", "استخراج vendor_boot")) { RomInspector.extractVendorBoot(f, dir).joinToString("\n", "Output: ${dir.path}\n") }
                        Kind.SPARSE -> act(tr(this, "Convert to raw", "تحويل إلى raw")) { RomInspector.unsparse(f, File(f.parentFile, f.nameWithoutExtension + ".raw.img")) }
                        Kind.SUPER -> act(tr(this, "Extract partitions", "استخراج الأقسام")) { RomInspector.extractSuper(f, dir).joinToString("\n", "Output: ${dir.path}\n") }
                        Kind.ZIP -> act(tr(this, "Extract all", "استخراج الكل")) { ArchiveTools.extract(f, dir); "Extracted to ${dir.path}" }
                        else -> {}
                    }
                }
            } catch (t: Throwable) { runOnUiThread { out.text = "Error: ${t.message ?: t}" } }
        }
    }

    private fun act(label: String, job: () -> String) {
        actions.addView(Ui.button(this, label) {
            out.text = "…"
            io.execute {
                val r = try { job() } catch (t: Throwable) { "Error: ${t.message ?: t}" }
                runOnUiThread { out.text = r }
            }
        })
    }
}
