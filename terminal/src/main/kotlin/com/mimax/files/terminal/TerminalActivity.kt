package com.mimax.files.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.mimax.files.common.Prefs
import com.mimax.files.common.tr
import com.mimax.files.termux.BootstrapInstaller
import com.mimax.files.termux.NoopSessionClient
import com.mimax.files.termux.TermuxEnv
import com.mimax.files.termux.TermuxService
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalActivity : AppCompatActivity(), TerminalViewClient {

    private lateinit var env: TermuxEnv
    private lateinit var term: TerminalView
    private lateinit var tabBar: LinearLayout
    private lateinit var msg: TextView
    private lateinit var ctrlBtn: MaterialButton
    private lateinit var altBtn: MaterialButton
    private var ctrl = false
    private var alt = false
    private var current = 0
    private var ready = false
    private val sessions get() = TermuxService.sessions

    private data class Scheme(val name: String, val fg: Int, val bg: Int)
    private val schemes = listOf(
        Scheme("Default dark", 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
        Scheme("Solarized dark", 0xFF839496.toInt(), 0xFF002B36.toInt()),
        Scheme("Light", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        Scheme("Green on black", 0xFF33FF33.toInt(), 0xFF000000.toInt()),
        Scheme("Dracula", 0xFFF8F8F2.toInt(), 0xFF282A36.toInt())
    )
    private val typefaces = listOf(
        "Monospace" to Typeface.MONOSPACE,
        "Monospace bold" to Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
        "Serif monospace" to Typeface.create("serif-monospace", Typeface.NORMAL)
    )

    private val sessionClient = object : NoopSessionClient() {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (changedSession === term.currentSession) term.onScreenUpdated()
        }
        override fun onTitleChanged(changedSession: TerminalSession) = rebuildTabs()
        override fun onSessionFinished(finishedSession: TerminalSession) = onFinished(finishedSession)
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("terminal", text ?: ""))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this@TerminalActivity)
            if (!t.isNullOrEmpty()) (session ?: term.currentSession)?.emulator?.paste(t.toString())
        }
        override fun onColorsChanged(session: TerminalSession) = term.onScreenUpdated()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        env = TermuxEnv(this)
        buildUi()
        if (!env.isInstalled) installBootstrap() else start()
    }

    // ---------------------------------------------------------------- UI
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply { addView(tabBar) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        msg = TextView(this).apply { setTextColor(Color.WHITE); setPadding(32, 32, 32, 32); visibility = android.view.View.GONE }
        root.addView(msg, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        term = TerminalView(this, null)
        term.setTerminalViewClient(this)
        term.isFocusable = true
        term.isFocusableInTouchMode = true
        root.addView(term, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(extraKeys(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }

    private fun small(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; minWidth = 0; minimumWidth = 0
        setPadding(24, 0, 24, 0)
        setOnClickListener { onClick() }
    }

    private fun extraKeys(): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(label: String, f: () -> Unit) = row.addView(small(label, f))
        add("ESC") { send("\u001b") }
        add("TAB") { send("\t") }
        ctrlBtn = small("CTRL") { ctrl = !ctrl; refreshMods() }; row.addView(ctrlBtn)
        altBtn = small("ALT") { alt = !alt; refreshMods() }; row.addView(altBtn)
        add("←") { arrow('D') }; add("↑") { arrow('A') }; add("↓") { arrow('B') }; add("→") { arrow('C') }
        add("/") { send("/") }; add("-") { send("-") }; add("|") { send("|") }; add("~") { send("~") }
        refreshMods()
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun refreshMods() {
        ctrlBtn.alpha = if (ctrl) 1f else 0.5f
        altBtn.alpha = if (alt) 1f else 0.5f
    }

    private fun send(s: String) { term.currentSession?.write(s) }

    private fun arrow(c: Char) {
        val app = term.mEmulator?.isCursorKeysApplicationMode() ?: false
        send((if (app) "\u001bO" else "\u001b[") + c)
    }

    private fun rebuildTabs() {
        tabBar.removeAllViews()
        tabBar.addView(small("⌂") { finish() })           // back to home; sessions keep running
        sessions.forEachIndexed { i, s ->
            val t = small(s.title?.takeIf { it.isNotBlank() }?.take(12) ?: "${i + 1}") { current = i; attach() }
            t.alpha = if (i == current) 1f else 0.6f
            tabBar.addView(t)
        }
        tabBar.addView(small("＋") { newSession() })
        tabBar.addView(small("✕") { sessions.getOrNull(current)?.finishIfRunning() })
        tabBar.addView(small("⚙") { settingsDialog() })
    }

    // ---------------------------------------------------------------- sessions
    private fun installBootstrap() {
        msg.visibility = android.view.View.VISIBLE
        msg.text = tr(this, "Installing Termux environment…", "جارٍ تثبيت بيئة Termux…")
        Thread {
            try {
                BootstrapInstaller.install(env) { p -> runOnUiThread { msg.text = p } }
                runOnUiThread { msg.visibility = android.view.View.GONE; start() }
            } catch (e: Throwable) {
                Log.e("Terminal", "bootstrap", e)
                runOnUiThread { msg.text = "Bootstrap error:\n${e.message}" }
            }
        }.start()
    }

    private fun start() {
        ready = true
        TermuxService.start(this)
        if (sessions.isEmpty()) newSession() else { current = current.coerceIn(0, sessions.size - 1); attach() }
    }

    private fun newSession() {
        try {
            TermuxService.newSession(env, sessionClient)
            current = sessions.size - 1
            attach()
        } catch (e: Throwable) {
            msg.visibility = android.view.View.VISIBLE
            msg.text = "Cannot start shell: ${e.message}"
        }
    }

    private fun attach() {
        val s = sessions.getOrNull(current) ?: return
        s.updateTerminalSessionClient(sessionClient)
        applyStyle()
        term.attachSession(s)
        rebuildTabs()
        term.requestFocus()
    }

    private fun onFinished(s: TerminalSession) {
        sessions.remove(s)
        if (sessions.isEmpty()) { finish(); return }
        current = current.coerceIn(0, sessions.size - 1)
        attach()
    }

    override fun onResume() { super.onResume(); if (ready && sessions.isNotEmpty()) attach() }

    override fun onDestroy() {
        // detach from this Activity; sessions keep running in the service
        sessions.forEach { it.updateTerminalSessionClient(TermuxService.baseClient) }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- style / settings
    private fun applyStyle() {
        val px = (Prefs.fontSp(this) * resources.displayMetrics.scaledDensity).toInt()
        term.setTextSize(px)
        term.setTypeface(typefaces[Prefs.typeface(this).coerceIn(0, typefaces.size - 1)].second)
        applyColors()
    }

    private fun applyColors() {
        val e = term.mEmulator ?: return
        val sch = schemes[Prefs.scheme(this).coerceIn(0, schemes.size - 1)]
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = sch.fg
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = sch.bg
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = sch.fg
        e.mColors.reset()
        term.onScreenUpdated()
    }

    private fun settingsDialog() {
        val items = arrayOf(
            tr(this, "Font size", "حجم الخط"),
            tr(this, "Font", "نوع الخط"),
            tr(this, "Colors", "الألوان"),
            tr(this, "Paste", "لصق")
        )
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> {
                    val sizes = intArrayOf(8, 10, 12, 14, 16, 18, 20, 24, 28)
                    AlertDialog.Builder(this).setSingleChoiceItems(sizes.map { "$it sp" }.toTypedArray(),
                        sizes.indexOf(Prefs.fontSp(this))) { d, i -> Prefs.setFontSp(this, sizes[i]); applyStyle(); d.dismiss() }.show()
                }
                1 -> AlertDialog.Builder(this).setSingleChoiceItems(typefaces.map { it.first }.toTypedArray(),
                    Prefs.typeface(this)) { d, i -> Prefs.setTypeface(this, i); applyStyle(); d.dismiss() }.show()
                2 -> AlertDialog.Builder(this).setSingleChoiceItems(schemes.map { it.name }.toTypedArray(),
                    Prefs.scheme(this)) { d, i -> Prefs.setScheme(this, i); applyColors(); d.dismiss() }.show()
                3 -> sessionClient.onPasteTextFromClipboard(term.currentSession)
            }
        }.show()
    }

    private fun showKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(term, 0)
    }

    // ---------------------------------------------------------------- TerminalViewClient
    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            val v = (Prefs.fontSp(this) + if (scale > 1f) 1 else -1).coerceIn(6, 40)
            Prefs.setFontSp(this, v); applyStyle()
            return 1.0f
        }
        return scale
    }
    override fun onSingleTapUp(e: MotionEvent?) { showKeyboard() }
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = false
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
    override fun onLongPress(event: MotionEvent?) = false
    override fun readControlKey() = ctrl
    override fun readAltKey() = alt
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        if (ctrl || alt) { ctrl = false; alt = false; runOnUiThread { refreshMods() } }
        return false
    }
    override fun onEmulatorSet() { applyColors() }
    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "error", e) }
}
