package com.mimax.files

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.mimax.files.common.Nav
import com.mimax.files.common.Ui
import com.mimax.files.common.tr

class MainActivity : AppCompatActivity() {

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) startActivity(Nav.intent(this, Nav.PLAYER).apply {
            putExtra("paths", uris.map { it.toString() }.toTypedArray()); putExtra("index", 0)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = { v: Int -> Ui.dp(this, v) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(32), dp(16), dp(16)) }
        root.addView(TextView(this).apply { text = "Mi_files"; textSize = 30f; setPadding(dp(8), 0, 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = tr(this@MainActivity, "Files · Terminal · Media · APK · ROM", "ملفات · طرفية · وسائط · APK · ROM")
            alpha = 0.7f; setPadding(dp(8), 0, 0, dp(16))
        })

        val cards = listOf(
            Triple("⌨", "Terminal / Termux", "Terminal / Termux") to { Nav.start(this, Nav.TERMINAL) },
            Triple("📁", "File Manager", "مدير الملفات") to { Nav.start(this, Nav.FILES) },
            Triple("▶", "Media Player", "مشغل الوسائط") to { pickMedia.launch(arrayOf("video/*", "audio/*")) },
            Triple("📦", "APK Tools", "أدوات APK") to { Nav.start(this, Nav.APK) },
            Triple("💽", "ROM Tools", "أدوات ROM") to { Nav.start(this, Nav.ROM) },
            Triple("⚙", "Settings", "الإعدادات") to { startActivity(android.content.Intent(this, SettingsActivity::class.java)) }
        )
        val cols = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        val grid = GridLayout(this).apply { columnCount = cols }
        cards.forEach { (t, action) ->
            val card = MaterialCardView(this).apply { radius = dp(16).toFloat(); isClickable = true; isFocusable = true; setOnClickListener { action() } }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(24), dp(8), dp(24)) }
            col.addView(TextView(this).apply { text = t.first; textSize = 36f; gravity = Gravity.CENTER })
            col.addView(TextView(this).apply { text = tr(this@MainActivity, t.second, t.third); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
            card.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f))
            lp.width = 0; lp.setMargins(dp(6), dp(6), dp(6), dp(6))
            grid.addView(card, lp)
        }
        root.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
