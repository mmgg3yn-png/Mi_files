package com.mimax.files.common

import android.content.Context
import android.content.Intent
import android.widget.TextView
import android.graphics.Typeface
import com.google.android.material.button.MaterialButton
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Returns the Arabic string when the app language is Arabic, English otherwise. */
fun tr(c: Context, en: String, ar: String): String =
    if (c.resources.configuration.locales[0].language == "ar") ar else en

object Fmt {
    fun size(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = -1
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", v, u[i])
    }
    fun date(ms: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
}

object Ui {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun button(c: Context, text: String, onClick: () -> Unit) = MaterialButton(c).apply {
        this.text = text; isAllCaps = false
        setOnClickListener { onClick() }
    }
    fun mono(c: Context) = TextView(c).apply {
        typeface = Typeface.MONOSPACE; textSize = 12f
        setTextIsSelectable(true)
        setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8))
    }
}

object Nav {
    const val PKG = "com.mimax.files"
    fun intent(ctx: Context, cls: String) = Intent().setClassName(ctx.packageName, cls)
    fun start(ctx: Context, cls: String, path: String? = null) {
        ctx.startActivity(intent(ctx, cls).apply { if (path != null) putExtra("path", path) })
    }
    const val TERMINAL = "com.mimax.files.terminal.TerminalActivity"
    const val FILES = "com.mimax.files.filemanager.FileManagerActivity"
    const val PLAYER = "com.mimax.files.media.PlayerActivity"
    const val APK = "com.mimax.files.apktools.ApkToolsActivity"
    const val ROM = "com.mimax.files.romtools.RomToolsActivity"
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("mi_files", Context.MODE_PRIVATE)
    fun theme(c: Context) = sp(c).getInt("theme", 0)            // 0 system, 1 light, 2 dark
    fun setTheme(c: Context, v: Int) = sp(c).edit().putInt("theme", v).apply()
    fun fontSp(c: Context) = sp(c).getInt("font_sp", 14)
    fun setFontSp(c: Context, v: Int) = sp(c).edit().putInt("font_sp", v).apply()
    fun scheme(c: Context) = sp(c).getInt("scheme", 0)
    fun setScheme(c: Context, v: Int) = sp(c).edit().putInt("scheme", v).apply()
    fun typeface(c: Context) = sp(c).getInt("typeface", 0)
    fun setTypeface(c: Context, v: Int) = sp(c).edit().putInt("typeface", v).apply()
    fun hidden(c: Context) = sp(c).getBoolean("hidden", false)
    fun setHidden(c: Context, v: Boolean) = sp(c).edit().putBoolean("hidden", v).apply()
}
