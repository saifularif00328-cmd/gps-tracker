package com.saifularif.gpstracker.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import java.io.File

/** Merekam pesan suara AAC kecil (mono 16 kHz, 24 kbps, maks 30 dtk ≈ 90 KB). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var pending: Pair<String, Long>? = null

    val isRecording get() = recorder != null

    fun elapsedMs(): Long = if (recorder != null) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start(): Boolean {
        cancel()
        val f = File(context.cacheDir, "rec-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(f.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) pending = finish()
            }
            r.prepare()
            r.start()
            recorder = r
            file = f
            startedAt = SystemClock.elapsedRealtime()
            pending = null
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    /** Hasil: (audio base64, durasi ms), atau null bila terlalu singkat/gagal. */
    fun stop(): Pair<String, Long>? {
        pending?.let { pending = null; return it }
        return finish()
    }

    fun cancel() {
        pending = null
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        file?.delete()
        file = null
    }

    private fun finish(): Pair<String, Long>? {
        val r = recorder ?: return null
        val f = file
        val dur = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        file = null
        val stopped = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!stopped || f == null || dur < MIN_MS) { f?.delete(); return null }
        val bytes = f.readBytes()
        f.delete()
        if (bytes.size > MAX_BYTES) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP) to minOf(dur, MAX_MS.toLong())
    }

    companion object {
        const val MAX_MS = 30_000
        const val MIN_MS = 700L
        const val MAX_BYTES = 450_000
    }
}
