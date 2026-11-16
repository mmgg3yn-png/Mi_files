package com.mimax.files

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mimax.files.common.Prefs

class MiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme(Prefs.theme(this))
    }
    companion object {
        fun applyTheme(v: Int) = AppCompatDelegate.setDefaultNightMode(
            when (v) { 1 -> AppCompatDelegate.MODE_NIGHT_NO; 2 -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })
    }
}
