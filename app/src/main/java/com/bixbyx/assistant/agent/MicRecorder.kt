package com.bixbyx.assistant.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/** Records 16 kHz mono until you stop talking; reports a live mic level for the animations. */
object MicRecorder {
    suspend fun record(ctx: Context, onLevel: (Float) -> Unit, maxMs: Int = 15000): ByteArray? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@withContext null
        val sr = 16000
        val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(min, 8000)) } catch (e: Exception) { return@withContext null }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext null }
        val buf = ShortArray(1600)
        val out = ByteArrayOutputStream()
        var speech = false; var silence = 0; var elapsed = 0; var noise = 250.0
        try {
            rec.startRecording()
            while (isActive && elapsed < maxMs) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) { sum += buf[i] * buf[i].toDouble(); out.write(buf[i].toInt() and 0xFF); out.write((buf[i].toInt() shr 8) and 0xFF) }
                val rms = sqrt(sum / n)
                onLevel((rms / 5000.0).coerceIn(0.0, 1.0).toFloat())
                val ms = n * 1000 / sr
                elapsed += ms
                if (elapsed < 400) noise = noise * 0.8 + rms * 0.2
                if (rms > max(noise * 2.4, 650.0)) { speech = true; silence = 0 } else if (speech) silence += ms
                if (speech && silence > 1300) break
                if (!speech && elapsed > 7000) break
            }
        } finally { runCatching { rec.stop() }; rec.release(); onLevel(0f) }
        if (!speech) null else wav(out.toByteArray(), sr)
    }

    private fun wav(pcm: ByteArray, sr: Int): ByteArray {
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }
}
