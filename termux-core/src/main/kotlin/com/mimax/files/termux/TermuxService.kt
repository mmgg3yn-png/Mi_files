package com.mimax.files.termux

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.termux.terminal.TerminalSession
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the process (and therefore all shell sessions) alive while the UI is closed.
 * Sessions live in a process-wide list, decoupled from any Activity.
 */
class TermuxService : Service() {
    companion object {
        val sessions = CopyOnWriteArrayList<TerminalSession>()
        const val ACTION_EXIT = "com.mimax.files.termux.EXIT"
        private const val CHANNEL = "termux_sessions"
        @Volatile private var instance: TermuxService? = null

        /** Client used while no Activity is attached. Drops finished sessions. */
        val baseClient = object : NoopSessionClient() {
            override fun onSessionFinished(finishedSession: TerminalSession) {
                sessions.remove(finishedSession)
                if (sessions.isEmpty()) instance?.shutdown()
            }
        }

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TermuxService::class.java))
        }

        fun newSession(env: TermuxEnv, client: com.termux.terminal.TerminalSessionClient): TerminalSession {
            val shell = env.shellPath()
            val s = TerminalSession(
                shell, env.home.absolutePath,
                arrayOf("-" + java.io.File(shell).name),
                env.environment(), 2000, client
            )
            sessions.add(s)
            return s
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            sessions.forEach { it.finishIfRunning() }
            sessions.clear()
            shutdown()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent().setClassName(packageName, "com.mimax.files.terminal.TerminalActivity"),
            PendingIntent.FLAG_IMMUTABLE)
        val exit = PendingIntent.getService(
            this, 1, Intent(this, TermuxService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Mi_files Terminal")
            .setContentText("${sessions.size} session(s) running")
            .setSmallIcon(applicationInfo.icon)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Exit", exit).build())
            .setOngoing(true).build()
        startForeground(1, n)
        return START_STICKY
    }

    fun shutdown() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { instance = null; super.onDestroy() }
}
