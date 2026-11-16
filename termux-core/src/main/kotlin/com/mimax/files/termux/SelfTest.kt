package com.mimax.files.termux

import java.io.File
import java.util.concurrent.TimeUnit

/** Runs REAL commands through the installed bootstrap (no simulation). Used by Settings and CI. */
object SelfTest {
    data class Result(val name: String, val ok: Boolean, val output: String)

    fun exec(env: TermuxEnv, command: String, timeoutSec: Long = 60): Result {
        return try {
            val pb = ProcessBuilder(env.shellPath(), "-c", command)
            pb.directory(env.home.apply { mkdirs() })
            pb.environment().clear()
            env.environment().forEach { val i = it.indexOf('='); pb.environment()[it.substring(0, i)] = it.substring(i + 1) }
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = StringBuilder()
            val t = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }.also { it.start() }
            val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) { p.destroyForcibly(); return Result(command, false, "TIMEOUT after ${timeoutSec}s\n$out") }
            t.join(2000)
            Result(command, p.exitValue() == 0, out.toString().trim())
        } catch (e: Throwable) { Result(command, false, "EXCEPTION: ${e.message}") }
    }

    /** Quick checks that need only the bootstrap itself. */
    val basic = listOf(
        "echo PREFIX=\$PREFIX HOME=\$HOME && uname -m",
        "bash --version | head -1",
        "ls \$PREFIX/bin | head -5",
        "apt --version",
        "dpkg --version | head -1",
        "pkg --help | head -1"
    )
    /** Checks that need packages installed via pkg. */
    val packages = listOf(
        "python --version", "git --version", "node --version", "npm --version", "ssh -V", "zsh --version"
    )

    fun run(env: TermuxEnv, withPackages: Boolean): String {
        if (!env.isInstalled) return "Bootstrap NOT installed (open Terminal once to install it)."
        val cmds = if (withPackages) basic + packages else basic
        return cmds.joinToString("\n\n") { c ->
            val r = exec(env, c)
            (if (r.ok) "[PASS] " else "[FAIL] ") + c + "\n" + r.output
        }
    }
}
