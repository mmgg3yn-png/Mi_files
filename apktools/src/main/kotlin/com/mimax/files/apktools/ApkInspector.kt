package com.mimax.files.apktools

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.xmlpull.v1.XmlPullParser
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

object ApkInspector {

    @Suppress("DEPRECATION")
    private fun versionCode(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()


    fun info(ctx: Context, apk: File): String {
        val pm = ctx.packageManager
        var flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        flags = flags or if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val pi: PackageInfo = pm.getPackageArchiveInfo(apk.path, flags) ?: throw IllegalArgumentException("Not a valid APK")
        val ai = pi.applicationInfo
        return buildString {
            appendLine("Package: ${pi.packageName}")
            appendLine("Version: ${pi.versionName} (${versionCode(pi)})")
            if (ai != null) {
                appendLine("minSdk: ${ai.minSdkVersion}   targetSdk: ${ai.targetSdkVersion}")
                ai.sourceDir = apk.path; ai.publicSourceDir = apk.path
                appendLine("Label: ${runCatching { ai.loadLabel(pm) }.getOrNull()}")
            }
            appendLine("Size: ${apk.length()} B")
            if (Build.VERSION.SDK_INT >= 28) {
                pi.signingInfo?.apkContentsSigners?.forEach {
                    val d = MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    appendLine("Signer SHA-256: " + d.joinToString("") { b -> "%02x".format(b) })
                }
            }
            appendLine("\nPermissions (${pi.requestedPermissions?.size ?: 0}):")
            pi.requestedPermissions?.forEach { appendLine("  $it") }
            fun comps(title: String, a: Array<out android.content.pm.ComponentInfo>?) {
                appendLine("\n$title (${a?.size ?: 0}):"); a?.forEach { appendLine("  ${it.name}") }
            }
            comps("Activities", pi.activities); comps("Services", pi.services)
            comps("Receivers", pi.receivers); comps("Providers", pi.providers)
        }
    }

    fun entries(apk: File): List<String> = ZipFile(apk).use { z -> z.entries().asSequence().map { it.name }.toList() }

    fun extractEntry(apk: File, name: String, outDir: File): File {
        ZipFile(apk).use { z ->
            val e = z.getEntry(name) ?: throw IllegalArgumentException("No entry $name")
            val out = File(outDir, name).canonicalFile
            if (!out.path.startsWith(outDir.canonicalPath)) throw SecurityException("bad path")
            out.parentFile?.mkdirs()
            z.getInputStream(e).use { i -> out.outputStream().use { i.copyTo(it) } }
            return out
        }
    }

    fun dexInfoOfFile(f: File): String = f.inputStream().use { i ->
        val b = ByteArray(0x70); DataInputStream(i).readFully(b); dexHeader(f.name, b)
    }

    fun dexInfo(apk: File): String = ZipFile(apk).use { z ->
        val sb = StringBuilder()
        z.entries().asSequence().filter { Regex("classes\\d*\\.dex").matches(it.name) }.forEach { e ->
            val b = ByteArray(0x70)
            DataInputStream(z.getInputStream(e)).use { it.readFully(b) }
            sb.appendLine(dexHeader(e.name, b)).appendLine()
        }
        if (sb.isEmpty()) "No classes*.dex in this archive" else sb.toString()
    }

    private fun dexHeader(name: String, b: ByteArray): String {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        fun u(o: Int) = bb.getInt(o).toLong() and 0xFFFFFFFFL
        val magic = String(b, 0, 8, Charsets.US_ASCII).replace("\n", "\\n").replace("\u0000", "\\0")
        return buildString {
            appendLine("== $name ==")
            appendLine("magic: $magic")
            appendLine("checksum: 0x%08x".format(u(8)))
            appendLine("file_size: ${u(32)}   header_size: ${u(36)}")
            appendLine("strings: ${u(56)}   types: ${u(64)}   protos: ${u(72)}")
            appendLine("fields: ${u(80)}   methods: ${u(88)}   classes: ${u(96)}")
            append("data_size: ${u(104)}")
        }
    }

    /** Decodes a compiled (binary) XML inside the APK (AndroidManifest.xml, res/...xml) to text. */
    fun decodeXml(ctx: Context, apk: File, entry: String): String {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(apk.path, 0) ?: throw IllegalArgumentException("Not a valid APK")
        val ai = pi.applicationInfo ?: throw IllegalStateException("No application info")
        ai.sourceDir = apk.path; ai.publicSourceDir = apk.path
        val res = pm.getResourcesForApplication(ai)
        val p = res.assets.openXmlResourceParser(entry)
        val sb = StringBuilder()
        var depth = 0
        try {
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        sb.append("  ".repeat(depth)).append('<').append(p.name)
                        for (i in 0 until p.attributeCount) {
                            val ns = p.getAttributePrefix(i)?.takeIf { it.isNotEmpty() }?.plus(":") ?: ""
                            sb.append("\n").append("  ".repeat(depth + 2)).append(ns).append(p.getAttributeName(i))
                                .append("=\"").append(p.getAttributeValue(i)).append('"')
                        }
                        sb.append(">\n"); depth++
                    }
                    XmlPullParser.END_TAG -> { depth--; sb.append("  ".repeat(depth)).append("</").append(p.name).append(">\n") }
                    XmlPullParser.TEXT -> if (p.text.isNotBlank()) sb.append("  ".repeat(depth)).append(p.text.trim()).append('\n')
                }
                ev = p.next()
            }
        } finally { p.close() }
        return sb.toString()
    }
}
