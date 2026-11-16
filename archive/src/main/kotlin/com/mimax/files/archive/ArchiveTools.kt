package com.mimax.files.archive

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.*

object ArchiveTools {
    enum class Fmt(val ext: String, val label: String) {
        ZIP("zip", "ZIP"), SEVENZ("7z", "7Z"), TAR("tar", "TAR"),
        TGZ("tar.gz", "TAR.GZ"), TXZ("tar.xz", "TAR.XZ"), TBZ2("tar.bz2", "TAR.BZ2"),
        GZ("gz", "GZ (single file)"), XZ("xz", "XZ (single file)"), BZ2("bz2", "BZIP2 (single file)")
    }

    data class Entry(val name: String, val size: Long, val isDir: Boolean)

    val creatable = Fmt.values().toList()

    fun detect(f: File): Fmt? {
        val n = f.name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Fmt.TGZ
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Fmt.TXZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> Fmt.TBZ2
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".mtz") || n.endsWith(".apk") -> Fmt.ZIP
            n.endsWith(".7z") -> Fmt.SEVENZ
            n.endsWith(".tar") -> Fmt.TAR
            n.endsWith(".gz") -> Fmt.GZ
            n.endsWith(".xz") -> Fmt.XZ
            n.endsWith(".bz2") -> Fmt.BZ2
            else -> null
        }
    }

    private fun open(f: File) = BufferedInputStream(FileInputStream(f), 1 shl 16)

    private fun archiveStream(f: File, fmt: Fmt): ArchiveInputStream<out ArchiveEntry> = when (fmt) {
        Fmt.ZIP -> ZipArchiveInputStream(open(f))
        Fmt.TAR -> TarArchiveInputStream(open(f))
        Fmt.TGZ -> TarArchiveInputStream(GzipCompressorInputStream(open(f)))
        Fmt.TXZ -> TarArchiveInputStream(XZCompressorInputStream(open(f)))
        Fmt.TBZ2 -> TarArchiveInputStream(BZip2CompressorInputStream(open(f)))
        else -> throw IllegalArgumentException("not a multi-file archive")
    }

    private fun single(f: File, fmt: Fmt): InputStream = when (fmt) {
        Fmt.GZ -> GzipCompressorInputStream(open(f))
        Fmt.XZ -> XZCompressorInputStream(open(f))
        else -> BZip2CompressorInputStream(open(f))
    }

    fun list(f: File): List<Entry> {
        val fmt = detect(f) ?: throw IOException("Unsupported archive: ${f.name}")
        val out = ArrayList<Entry>()
        when (fmt) {
            Fmt.SEVENZ -> SevenZFile(f).use { z ->
                var e: SevenZArchiveEntry? = z.nextEntry
                while (e != null) { out.add(Entry(e.name, e.size, e.isDirectory)); e = z.nextEntry }
            }
            Fmt.GZ, Fmt.XZ, Fmt.BZ2 -> out.add(Entry(f.nameWithoutExtension, -1, false))
            else -> archiveStream(f, fmt).use { s ->
                var e = s.nextEntry
                while (e != null) { out.add(Entry(e.name, e.size, e.isDirectory)); e = s.nextEntry }
            }
        }
        return out
    }

    private fun safe(dest: File, name: String): File {
        val f = File(dest, name).canonicalFile
        if (!f.path.startsWith(dest.canonicalPath + File.separator) && f != dest.canonicalFile)
            throw SecurityException("Blocked path traversal: $name")
        return f
    }

    private fun copy(i: InputStream, o: OutputStream) {
        val buf = ByteArray(1 shl 16)
        var n = i.read(buf)
        while (n >= 0) { o.write(buf, 0, n); n = i.read(buf) }
    }

    fun extract(f: File, dest: File) {
        val fmt = detect(f) ?: throw IOException("Unsupported archive: ${f.name}")
        dest.mkdirs()
        when (fmt) {
            Fmt.SEVENZ -> SevenZFile(f).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val out = safe(dest, e.name)
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { os ->
                            val buf = ByteArray(1 shl 16)
                            var n = z.read(buf)
                            while (n >= 0) { os.write(buf, 0, n); n = z.read(buf) }
                        }
                    }
                    e = z.nextEntry
                }
            }
            Fmt.GZ, Fmt.XZ, Fmt.BZ2 -> single(f, fmt).use { i ->
                FileOutputStream(File(dest, f.nameWithoutExtension)).use { copy(i, it) }
            }
            else -> archiveStream(f, fmt).use { s ->
                var e = s.nextEntry
                while (e != null) {
                    val out = safe(dest, e.name)
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { copy(s, it) }
                    }
                    e = s.nextEntry
                }
            }
        }
    }

    private fun gather(inputs: List<File>): List<Pair<File, String>> {
        val res = ArrayList<Pair<File, String>>()
        fun walk(f: File, name: String) {
            if (f.isDirectory) {
                res.add(f to "$name/")
                f.listFiles()?.sortedBy { it.name }?.forEach { walk(it, "$name/${it.name}") }
            } else res.add(f to name)
        }
        inputs.forEach { walk(it, it.name) }
        return res
    }

    fun compress(inputs: List<File>, out: File, fmt: Fmt) {
        val items = gather(inputs)
        when (fmt) {
            Fmt.ZIP -> ZipArchiveOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
                for ((f, n) in items) {
                    z.putArchiveEntry(ZipArchiveEntry(f, n))
                    if (f.isFile) FileInputStream(f).use { copy(it, z) }
                    z.closeArchiveEntry()
                }
            }
            Fmt.SEVENZ -> SevenZOutputFile(out).use { z ->
                for ((f, n) in items) {
                    val e = z.createArchiveEntry(f, n)
                    z.putArchiveEntry(e)
                    if (f.isFile) FileInputStream(f).use { i ->
                        val buf = ByteArray(1 shl 16); var r = i.read(buf)
                        while (r >= 0) { z.write(buf, 0, r); r = i.read(buf) }
                    }
                    z.closeArchiveEntry()
                }
            }
            Fmt.TAR, Fmt.TGZ, Fmt.TXZ, Fmt.TBZ2 -> {
                val raw = BufferedOutputStream(FileOutputStream(out))
                val wrapped: OutputStream = when (fmt) {
                    Fmt.TGZ -> GzipCompressorOutputStream(raw)
                    Fmt.TXZ -> XZCompressorOutputStream(raw)
                    Fmt.TBZ2 -> BZip2CompressorOutputStream(raw)
                    else -> raw
                }
                TarArchiveOutputStream(wrapped).use { t ->
                    t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    for ((f, n) in items) {
                        t.putArchiveEntry(TarArchiveEntry(f, n))
                        if (f.isFile) FileInputStream(f).use { copy(it, t) }
                        t.closeArchiveEntry()
                    }
                }
            }
            Fmt.GZ, Fmt.XZ, Fmt.BZ2 -> {
                require(inputs.size == 1 && inputs[0].isFile) { "Single-file formats need exactly one file" }
                val raw = BufferedOutputStream(FileOutputStream(out))
                val w: OutputStream = when (fmt) {
                    Fmt.GZ -> GzipCompressorOutputStream(raw)
                    Fmt.XZ -> XZCompressorOutputStream(raw)
                    else -> BZip2CompressorOutputStream(raw)
                }
                w.use { o -> FileInputStream(inputs[0]).use { copy(it, o) } }
            }
        }
    }
}
