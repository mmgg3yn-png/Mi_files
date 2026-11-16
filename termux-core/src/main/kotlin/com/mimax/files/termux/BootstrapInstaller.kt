package com.mimax.files.termux

import android.system.Os
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Installs a Termux-format bootstrap zip (bootstrap-<arch>.zip, containing SYMLINKS.txt)
 * from app assets into filesDir/usr. NOTE: the bootstrap must be built for THIS package's
 * prefix (/data/data/com.mimax.files/files/usr). See docs/BOOTSTRAP.md.
 */
object BootstrapInstaller {
    fun install(env: TermuxEnv, progress: (String) -> Unit) {
        val asset = "bootstrap-${env.arch}.zip"
        val am = env.context.assets
        if (asset !in (am.list("") ?: emptyArray())) {
            throw IllegalStateException(
                "Bootstrap asset '$asset' is missing from the APK. Build it with the " +
                    "'Build Bootstrap' workflow (docs/BOOTSTRAP.md) and rebuild the app.")
        }
        val staging = File(env.files, "usr-staging")
        staging.deleteRecursively(); staging.mkdirs()
        val stagingCanon = staging.canonicalPath
        val links = ArrayList<Pair<String, String>>()
        val buf = ByteArray(65536)
        var count = 0
        ZipInputStream(am.open(asset)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name == "SYMLINKS.txt") {
                    BufferedReader(InputStreamReader(zis, Charsets.UTF_8)).readLines().forEach { line ->
                        val parts = line.split("←")
                        if (parts.size == 2) links.add(parts[0] to parts[1])
                    }
                } else {
                    val f = File(staging, e.name)
                    if (!f.canonicalPath.startsWith(stagingCanon)) throw SecurityException("Bad entry ${e.name}")
                    if (e.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        val head = ByteArray(4); var headLen = 0
                        FileOutputStream(f).use { os ->
                            var n = zis.read(buf)
                            while (n > 0) {
                                if (headLen < 4) {
                                    val c = minOf(4 - headLen, n)
                                    System.arraycopy(buf, 0, head, headLen, c); headLen += c
                                }
                                os.write(buf, 0, n)
                                n = zis.read(buf)
                            }
                        }
                        val elf = headLen >= 4 && head[0] == 0x7f.toByte() && head[1] == 'E'.code.toByte() &&
                            head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
                        val shebang = headLen >= 2 && head[0] == '#'.code.toByte() && head[1] == '!'.code.toByte()
                        if (elf || shebang) Os.chmod(f.path, 448) // 0700
                        if (++count % 200 == 0) progress("Extracted $count files…")
                    }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        progress("Creating ${links.size} symlinks…")
        for ((target, link) in links) {
            val lf = File(staging, link)
            lf.parentFile?.mkdirs()
            if (lf.exists()) lf.delete()
            Os.symlink(target, lf.path)
        }
        env.prefix.deleteRecursively()
        if (!staging.renameTo(env.prefix)) throw IllegalStateException("Cannot move staging to ${env.prefix}")
        env.home.mkdirs(); env.tmp.mkdirs()
        trustOwnRepo(env)
        progress("Done")
    }

    /**
     * The custom apt repo is hosted unsigned (no GPG key yet), so mark it [trusted=yes].
     * SECURITY TRADE-OFF: package integrity then relies on HTTPS only. Replace with a signed repo + keyring later.
     */
    private fun trustOwnRepo(env: TermuxEnv) {
        for (f in listOf(File(env.prefix, "etc/apt/sources.list")) +
            (File(env.prefix, "etc/apt/sources.list.d").listFiles()?.toList() ?: emptyList())) {
            if (!f.isFile) continue
            val t = f.readText()
            val n = t.replace("deb https://mmgg3yn-png.github.io", "deb [trusted=yes] https://mmgg3yn-png.github.io")
            if (n != t) f.writeText(n)
        }
    }
}
