package com.mimax.files.termux

import android.content.Context
import android.os.Build
import java.io.File

/** Paths and environment for the embedded Termux userland. */
class TermuxEnv(val context: Context) {
    /**
     * Packages are built for the literal prefix /data/data/<pkg>/files/usr, but Context.filesDir returns
     * /data/user/0/<pkg>/files on Android 7+. Use the literal path (same directory, resolved through the
     * /data/data symlink) so PREFIX/HOME/TMPDIR match what was baked in at package build time.
     * Falls back to filesDir when the literal path is not the same directory (secondary users).
     */
    val files: File = File("/data/data/${context.packageName}/files").let {
        if (runCatching { it.canonicalPath == context.filesDir.canonicalPath }.getOrDefault(false)) it else context.filesDir
    }
    val prefix = File(files, "usr")
    val home = File(files, "home")
    val tmp = File(prefix, "tmp")

    val arch: String
        get() = when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a", "armeabi" -> "arm"
            "x86_64" -> "x86_64" // only used for emulator CI smoke tests
            else -> throw IllegalStateException("Unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        }

    fun shellPath(): String {
        val bash = File(prefix, "bin/bash")
        return if (bash.exists()) bash.absolutePath else File(prefix, "bin/sh").absolutePath
    }

    val isInstalled: Boolean
        get() = File(prefix, "bin/bash").exists() || File(prefix, "bin/sh").exists()

    fun environment(): Array<String> {
        val l = arrayListOf(
            "HOME=${home.absolutePath}",
            "PREFIX=${prefix.absolutePath}",
            "TMPDIR=${tmp.absolutePath}",
            "PATH=${prefix.absolutePath}/bin",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "EXTERNAL_STORAGE=/sdcard",
            "TERMUX__ROOTFS=${files.absolutePath}",
            "TERMUX__HOME=${home.absolutePath}",
            "TERMUX__PREFIX=${prefix.absolutePath}",
            "TERMUX_APP__PACKAGE_NAME=${context.packageName}",
            "TERMUX_APP__DATA_DIR=${context.applicationInfo.dataDir}",
            "TERMUX_APP__FILES_DIR=${files.absolutePath}"
        )
        // termux-exec (if shipped in the bootstrap) must be preloaded for exec/shebang handling.
        val exec = File(prefix, "lib/libtermux-exec.so")
        if (exec.exists()) l.add("LD_PRELOAD=${exec.absolutePath}")
        return l.toTypedArray()
    }
}
