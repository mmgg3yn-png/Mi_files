package com.mimax.files.romtools

import com.mimax.files.common.Fmt
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Kind { BOOT, VENDOR_BOOT, SPARSE, SUPER, EXT4, EROFS, SQUASHFS, ZIP, SPLASH, UNKNOWN }

class Rd(val f: RandomAccessFile) {
    fun bytes(off: Long, n: Int): ByteArray { val b = ByteArray(n); f.seek(off); f.readFully(b); return b }
    private fun bb(off: Long, n: Int) = ByteBuffer.wrap(bytes(off, n)).order(ByteOrder.LITTLE_ENDIAN)
    fun u16(off: Long): Int = bb(off, 2).short.toInt() and 0xFFFF
    fun u32(off: Long): Long = bb(off, 4).int.toLong() and 0xFFFFFFFFL
    fun u64(off: Long): Long = bb(off, 8).long
    fun str(off: Long, n: Int): String =
        String(bytes(off, n), Charsets.ISO_8859_1).takeWhile { it != '\u0000' }
    val len get() = f.length()
}

object RomInspector {
    private fun align(v: Long, a: Long) = (v + a - 1) / a * a

    private fun copyRange(r: RandomAccessFile, off: Long, len: Long, out: File) {
        out.outputStream().use { os ->
            val buf = ByteArray(1 shl 16); var left = len; r.seek(off)
            while (left > 0) {
                val n = r.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw IOException("Unexpected end of file")
                os.write(buf, 0, n); left -= n
            }
        }
    }

    fun detect(f: File): Kind = RandomAccessFile(f, "r").use { r ->
        val rd = Rd(r); val n = rd.len
        fun ok(o: Long, s: Int) = n >= o + s
        when {
            ok(0, 8) && rd.str(0, 8) == "ANDROID!" -> Kind.BOOT
            ok(0, 8) && rd.str(0, 8) == "VNDRBOOT" -> Kind.VENDOR_BOOT
            ok(0, 4) && rd.u32(0) == 0xED26FF3AL -> Kind.SPARSE
            ok(0, 4) && rd.str(0, 2) == "PK" -> Kind.ZIP
            ok(0, 8) && rd.str(0, 8) == "SPLASH!!" -> Kind.SPLASH
            ok(4096, 4) && rd.u32(4096) == 0x616c4467L -> Kind.SUPER
            ok(1080, 2) && rd.u16(1080) == 0xEF53 -> Kind.EXT4
            ok(1024, 4) && rd.u32(1024) == 0xE0F5E1E2L -> Kind.EROFS
            ok(0, 4) && rd.str(0, 4) == "hsqs" -> Kind.SQUASHFS
            else -> Kind.UNKNOWN
        }
    }

    fun info(f: File): String {
        val k = detect(f)
        val sb = StringBuilder("File: ${f.name}\nSize: ${Fmt.size(f.length())}\nType: $k\n\n")
        RandomAccessFile(f, "r").use { r ->
            val rd = Rd(r)
            when (k) {
                Kind.BOOT -> sb.append(bootInfo(rd))
                Kind.VENDOR_BOOT -> sb.append(vendorBootInfo(rd))
                Kind.SPARSE -> sb.append("Sparse image\nblock size: ${rd.u32(12)}\nblocks: ${rd.u32(16)}\nchunks: ${rd.u32(20)}\n" +
                    "raw size: ${Fmt.size(rd.u32(12) * rd.u32(16))}\n")
                Kind.SUPER -> lpParse(rd).forEach { sb.append("${it.name}  ${Fmt.size(it.sectors * 512)}  extents=${it.extents.size}\n") }
                Kind.EXT4 -> sb.append("ext4 filesystem\nblocks: ${rd.u32(1024 + 4)}\nlabel: ${rd.str(1024 + 120, 16)}\n")
                Kind.EROFS -> sb.append("EROFS filesystem\n")
                Kind.SQUASHFS -> sb.append("SquashFS filesystem\n")
                Kind.ZIP -> sb.append("ZIP container (MTZ / flashable zip)\n")
                Kind.SPLASH -> sb.append("Qualcomm splash/logo image (header only parsed)\n")
                Kind.UNKNOWN -> {}
            }
            sb.append("\nFirst 128 bytes:\n").append(hex(rd.bytes(0, minOf(128L, rd.len).toInt())))
        }
        return sb.toString()
    }

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (i in b.indices step 16) {
            sb.append("%06x  ".format(i))
            for (j in i until minOf(i + 16, b.size)) sb.append("%02x ".format(b[j]))
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun osVer(v: Long): String {
        val ver = v shr 11; val patch = v and 0x7ff
        val a = (ver shr 14) and 0x7f; val b = (ver shr 7) and 0x7f; val c = ver and 0x7f
        return "$a.$b.$c patch ${(patch shr 4) + 2000}-${patch and 0xf}"
    }

    private fun bootVersion(rd: Rd): Int { val v = rd.u32(40).toInt(); return if (v in 0..4) v else 0 }

    private fun bootInfo(rd: Rd): String {
        val ver = bootVersion(rd)
        return if (ver >= 3) "header_version: $ver\nkernel: ${rd.u32(8)} B\nramdisk: ${rd.u32(12)} B\nos_version: ${osVer(rd.u32(16))}\n" +
            "cmdline: ${rd.str(44, 1536)}\n"
        else "header_version: $ver\npage_size: ${rd.u32(36)}\nkernel: ${rd.u32(8)} B\nramdisk: ${rd.u32(16)} B\nsecond: ${rd.u32(24)} B\n" +
            "os_version: ${osVer(rd.u32(44))}\nname: ${rd.str(48, 16)}\ncmdline: ${rd.str(64, 512)}${rd.str(608, 1024)}\n"
    }

    private fun vendorBootInfo(rd: Rd): String =
        "header_version: ${rd.u32(8)}\npage_size: ${rd.u32(12)}\nvendor_ramdisk: ${rd.u32(24)} B\ndtb: ${rd.u32(2100)} B\n" +
            "name: ${rd.str(2080, 16)}\ncmdline: ${rd.str(28, 2048)}\n"

    // --------------------------------------------------------------- extraction
    fun extractBoot(f: File, out: File): List<String> {
        out.mkdirs()
        val res = ArrayList<String>()
        RandomAccessFile(f, "r").use { r ->
            val rd = Rd(r); val ver = bootVersion(rd)
            fun dump(name: String, off: Long, size: Long) {
                if (size > 0) { copyRange(r, off, size, File(out, name)); res.add("$name (${Fmt.size(size)})") }
            }
            if (ver >= 3) {
                val page = 4096L; val ks = rd.u32(8); val rs = rd.u32(12)
                dump("kernel", page, ks); dump("ramdisk.cpio.gz", page + align(ks, page), rs)
                File(out, "cmdline.txt").writeText(rd.str(44, 1536))
            } else {
                val page = rd.u32(36); val ks = rd.u32(8); val rs = rd.u32(16); val ss = rd.u32(24)
                var off = page
                dump("kernel", off, ks); off += align(ks, page)
                dump("ramdisk.cpio.gz", off, rs); off += align(rs, page)
                dump("second", off, ss); off += align(ss, page)
                if (ver >= 1) { val ds = rd.u32(1632); dump("recovery_dtbo", rd.u64(1636), ds); off += align(ds, page) }
                if (ver >= 2) dump("dtb", off, rd.u32(1648))
                File(out, "cmdline.txt").writeText(rd.str(64, 512) + rd.str(608, 1024))
            }
        }
        return res
    }

    fun extractVendorBoot(f: File, out: File): List<String> {
        out.mkdirs()
        val res = ArrayList<String>()
        RandomAccessFile(f, "r").use { r ->
            val rd = Rd(r); val ver = rd.u32(8); val page = rd.u32(12)
            val rs = rd.u32(24); val ds = rd.u32(2100)
            val hdr = align(if (ver >= 4) 2128L else 2112L, page)
            if (rs > 0) { copyRange(r, hdr, rs, File(out, "vendor_ramdisk.cpio")); res.add("vendor_ramdisk.cpio (${Fmt.size(rs)})") }
            if (ds > 0) { copyRange(r, hdr + align(rs, page), ds, File(out, "dtb")); res.add("dtb (${Fmt.size(ds)})") }
            File(out, "cmdline.txt").writeText(rd.str(28, 2048))
        }
        return res
    }

    /** simg2img: Android sparse image -> raw image. */
    fun unsparse(src: File, dst: File): String {
        RandomAccessFile(src, "r").use { r ->
            val rd = Rd(r)
            if (rd.u32(0) != 0xED26FF3AL) throw IOException("Not a sparse image")
            val fileHdr = rd.u16(8).toLong(); val chunkHdr = rd.u16(10).toLong()
            val blk = rd.u32(12); val totalBlks = rd.u32(16); val chunks = rd.u32(20)
            var pos = fileHdr; var outPos = 0L
            RandomAccessFile(dst, "rw").use { o ->
                o.setLength(0)
                val buf = ByteArray(1 shl 16)
                for (i in 0 until chunks) {
                    val type = rd.u16(pos); val csz = rd.u32(pos + 4); val tsz = rd.u32(pos + 8)
                    val data = pos + chunkHdr; val bytes = csz * blk
                    when (type) {
                        0xCAC1 -> {
                            r.seek(data); o.seek(outPos); var left = bytes
                            while (left > 0) {
                                val n = r.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) throw IOException("Truncated sparse image")
                                o.write(buf, 0, n); left -= n
                            }
                            outPos += bytes
                        }
                        0xCAC2 -> {
                            val pat = rd.bytes(data, 4)
                            for (j in buf.indices) buf[j] = pat[j % 4]
                            o.seek(outPos); var left = bytes
                            while (left > 0) { val n = minOf(buf.size.toLong(), left).toInt(); o.write(buf, 0, n); left -= n }
                            outPos += bytes
                        }
                        0xCAC3 -> outPos += bytes
                        0xCAC4 -> {}
                        else -> throw IOException("Unknown chunk type 0x%x".format(type))
                    }
                    pos += tsz
                }
                o.setLength(totalBlks * blk)
            }
        }
        return "Raw image written: ${dst.path} (${Fmt.size(dst.length())})"
    }

    // --------------------------------------------------------------- super.img (LP metadata)
    class LpPart(val name: String, val sectors: Long, val extents: List<Triple<Long, Long, Int>>)

    fun lpParse(rd: Rd): List<LpPart> {
        if (rd.u32(4096) != 0x616c4467L) throw IOException("No LP geometry (is this a sparse super.img? convert it first)")
        val hdr = 4096L * 3
        if (rd.u32(hdr) != 0x414C5030L) throw IOException("No LP metadata header")
        val tables = hdr + rd.u32(hdr + 8)
        val pOff = rd.u32(hdr + 80); val pNum = rd.u32(hdr + 84); val pSz = rd.u32(hdr + 88)
        val eOff = rd.u32(hdr + 92); val eSz = rd.u32(hdr + 100)
        val res = ArrayList<LpPart>()
        for (i in 0 until pNum) {
            val p = tables + pOff + i * pSz
            val name = rd.str(p, 36)
            val first = rd.u32(p + 40); val num = rd.u32(p + 44)
            var total = 0L; val ex = ArrayList<Triple<Long, Long, Int>>()
            for (j in 0 until num) {
                val e = tables + eOff + (first + j) * eSz
                val sectors = rd.u64(e); val type = rd.u32(e + 8).toInt(); val target = rd.u64(e + 12)
                total += sectors; ex.add(Triple(sectors, target, type))
            }
            res.add(LpPart(name, total, ex))
        }
        return res
    }

    fun extractSuper(f: File, out: File): List<String> {
        out.mkdirs()
        val res = ArrayList<String>()
        RandomAccessFile(f, "r").use { r ->
            for (p in lpParse(Rd(r))) {
                if (p.sectors == 0L) continue
                File(out, p.name + ".img").outputStream().use { os ->
                    val buf = ByteArray(1 shl 16)
                    for ((sectors, target, type) in p.extents) {
                        var left = sectors * 512
                        if (type == 0) r.seek(target * 512)
                        while (left > 0) {
                            val n = minOf(buf.size.toLong(), left).toInt()
                            if (type == 0) { val rn = r.read(buf, 0, n); if (rn < 0) throw IOException("Truncated"); os.write(buf, 0, rn); left -= rn }
                            else { java.util.Arrays.fill(buf, 0); os.write(buf, 0, n); left -= n }
                        }
                    }
                }
                res.add("${p.name}.img (${Fmt.size(p.sectors * 512)})")
            }
        }
        return res
    }
}
