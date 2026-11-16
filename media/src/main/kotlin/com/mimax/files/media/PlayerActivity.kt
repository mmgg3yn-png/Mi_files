package com.mimax.files.media

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mimax.files.common.Ui
import com.mimax.files.common.tr
import java.io.File

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var names: List<String> = emptyList()
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIdx = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paths = intent.getStringArrayExtra("paths") ?: emptyArray()
        val index = intent.getIntExtra("index", 0)
        val items = paths.map { item(it) }
        names = paths.map { if (it.contains("://")) Uri.parse(it).lastPathSegment ?: it else File(it).name }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val pv = PlayerView(this).apply {
            setShowSubtitleButton(true)
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            setShowNextButton(true)
            setShowPreviousButton(true)
        }
        root.addView(pv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val p = ExoPlayer.Builder(this).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        player = p
        pv.player = p
        p.setMediaItems(items, index.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        p.prepare(); p.playWhenReady = true

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Ui.button(this, "1x") {}.also { b ->
            b.setOnClickListener {
                speedIdx = (speedIdx + 1) % speeds.size
                p.setPlaybackSpeed(speeds[speedIdx]); b.text = "${speeds[speedIdx]}x"
            }
        })
        bar.addView(Ui.button(this, tr(this, "Playlist", "القائمة")) {
            AlertDialog.Builder(this).setItems(names.toTypedArray()) { _, i -> p.seekTo(i, 0L); p.play() }.show()
        })
        val vol = SeekBar(this).apply {
            max = 100; progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { p.volume = v / 100f }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bar.addView(vol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }

    private fun item(path: String): MediaItem {
        if (path.contains("://")) return MediaItem.fromUri(Uri.parse(path))
        val f = File(path)
        val subs = f.parentFile?.listFiles()?.filter {
            it.isFile && it.nameWithoutExtension.startsWith(f.nameWithoutExtension) && it.extension.lowercase() in setOf("srt", "vtt", "ass", "ssa")
        }?.map {
            val mime = when (it.extension.lowercase()) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                else -> MimeTypes.TEXT_SSA
            }
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(it)).setMimeType(mime)
                .setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        } ?: emptyList()
        return MediaItem.Builder().setUri(Uri.fromFile(f)).setSubtitleConfigurations(subs).build()
    }

    override fun onStop() { player?.pause(); super.onStop() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
