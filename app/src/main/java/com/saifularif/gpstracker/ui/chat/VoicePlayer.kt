package com.saifularif.gpstracker.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    var playingId by mutableStateOf<String?>(null)
        private set

    fun toggle(id: String, base64: String) {
        if (playingId == id) { stop(); return }
        stop()
        val f = File(context.cacheDir, "play-$id.m4a")
        val p = MediaPlayer()
        try {
            f.writeBytes(Base64.decode(base64, Base64.DEFAULT))
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            player = p
            playingId = id
        } catch (e: Exception) {
            runCatching { p.release() }
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playingId = null
    }
}
