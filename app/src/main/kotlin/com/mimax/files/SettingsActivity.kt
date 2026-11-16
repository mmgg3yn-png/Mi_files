package com.mimax.files

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mimax.files.common.Prefs
import com.mimax.files.common.Ui
import com.mimax.files.common.tr
import com.mimax.files.termux.SelfTest
import com.mimax.files.termux.TermuxEnv
import java.io.File
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = { v: Int -> Ui.dp(this, v) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(20)) }
        fun head(t: String) = root.addView(TextView(this).apply { text = t; textSize = 18f; setPadding(0, dp(16), 0, dp(4)) })

        head(tr(this, "Theme", "المظهر"))
        val themes = listOf(tr(this, "System", "النظام"), tr(this, "Light", "فاتح"), tr(this, "Dark", "داكن"))
        val rg = RadioGroup(this)
        themes.forEachIndexed { i, n -> rg.addView(RadioButton(this).apply { text = n; id = 100 + i; isChecked = Prefs.theme(this@SettingsActivity) == i }) }
        rg.setOnCheckedChangeListener { _, id -> Prefs.setTheme(this, id - 100); MiApp.applyTheme(id - 100) }
        root.addView(rg)

        head(tr(this, "Language", "اللغة"))
        val tags = listOf("", "en", "ar")
        val langs = listOf(tr(this, "System", "النظام"), "English", "العربية")
        val rl = RadioGroup(this)
        val cur = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')
        langs.forEachIndexed { i, n -> rl.addView(RadioButton(this).apply { text = n; id = 200 + i; isChecked = tags[i] == cur }) }
        rl.setOnCheckedChangeListener { _, id ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[id - 200]))
        }
        root.addView(rl)

        head("Termux")
        val usr = File(filesDir, "usr")
        root.addView(TextView(this).apply {
            text = "PREFIX: $usr\nHOME: ${File(filesDir, "home")}\n" +
                tr(this@SettingsActivity, "Installed: ", "مثبّتة: ") + File(usr, "bin").exists()
        })
        val result = Ui.mono(this)
        root.addView(Ui.button(this, tr(this, "Run Termux self-test (real commands)", "اختبار Termux (أوامر حقيقية)")) {
            result.text = "…"
            Executors.newSingleThreadExecutor().execute {
                val r = SelfTest.run(TermuxEnv(this), withPackages = true)
                runOnUiThread { result.text = r }
            }
        })
        root.addView(result)
        head(tr(this, "License", "الترخيص"))
        root.addView(TextView(this).apply { text = "GPL-3.0-or-later. Uses Termux terminal-emulator / terminal-view (GPLv3)." })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
