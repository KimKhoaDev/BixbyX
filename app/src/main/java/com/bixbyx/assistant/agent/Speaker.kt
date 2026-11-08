package com.bixbyx.assistant.agent

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.bixbyx.assistant.ai.FishAudio
import com.bixbyx.assistant.ai.Prefs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.Locale

class Speaker(private val ctx: Context, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var chan = Channel<String>(Channel.UNLIMITED)
    private var worker: Job? = null
    private var player: MediaPlayer? = null
    private var sysReady = false
    private var pending: CancellableContinuation<Unit>? = null
    private lateinit var sys: TextToSpeech

    init {
        sys = TextToSpeech(ctx) { st ->
            sysReady = st == TextToSpeech.SUCCESS
            if (sysReady) runCatching { sys.language = Locale.getDefault() }
        }
        sys.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { resumePending() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { resumePending() }
        })
        startWorker()
    }

    private fun resumePending() { scope.launch { pending?.let { if (it.isActive) it.resume(Unit) { } }; pending = null } }
    private fun startWorker() { worker = scope.launch { for (t in chan) speakNow(t) } }

    /** interrupt = drop whatever is queued/playing first (used for step narration). */
    fun speak(text: String, interrupt: Boolean = false) {
        if (interrupt) stop()
        chan.trySend(text)
    }

    fun stop() {
        worker?.cancel(); chan.close(); chan = Channel(Channel.UNLIMITED)
        runCatching { player?.release() }; player = null
        runCatching { sys.stop() }
        startWorker()
    }

    private suspend fun speakNow(raw: String) {
        val text = raw.replace(Regex("[*_`#>]"), "").trim()
        if (text.isBlank()) return
        if (prefs.useFishTts) {
            try { play(FishAudio.tts(prefs, text)); return }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { BixbyEngine.notify("Fish Audio: ${e.message?.take(60)} — using phone voice") }
        }
        speakSystem(text)
    }

    private suspend fun play(bytes: ByteArray) = withContext(Dispatchers.Main) {
        val f = File(ctx.cacheDir, "tts_${System.nanoTime()}.mp3").apply { writeBytes(bytes) }
        try {
            suspendCancellableCoroutine<Unit> { c ->
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(f.absolutePath)
                mp.setOnCompletionListener { if (c.isActive) c.resume(Unit) { } }
                mp.setOnErrorListener { _, w, _ -> if (c.isActive) c.resumeWith(Result.failure(IllegalStateException("player error $w"))); true }
                mp.setOnPreparedListener { it.start() }
                c.invokeOnCancellation { runCatching { mp.release() } }
                mp.prepareAsync()
            }
        } finally { runCatching { player?.release() }; player = null; f.delete() }
    }

    private suspend fun speakSystem(text: String) {
        if (!sysReady) { BixbyEngine.notify("No phone speech engine found — add a Fish Audio key in Settings"); return }
        suspendCancellableCoroutine<Unit> { c ->
            pending = c
            c.invokeOnCancellation { runCatching { sys.stop() } }
            if (sys.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bx${System.nanoTime()}") != TextToSpeech.SUCCESS) {
                pending = null; if (c.isActive) c.resume(Unit) { }
            }
        }
    }
}
