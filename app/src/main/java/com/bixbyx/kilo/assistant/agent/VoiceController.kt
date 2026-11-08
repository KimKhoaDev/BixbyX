package com.bixbyx.kilo.assistant.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.bixbyx.kilo.assistant.ai.FishAudio
import com.bixbyx.kilo.assistant.ai.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Speech in/out. Fish Audio first when a key is set, otherwise (or on failure) the phone's own engines. */
class VoiceController(private val ctx: Context, private val prefs: Prefs) {
    private val speaker = Speaker(ctx, prefs)
    private var rec: SpeechRecognizer? = null
    private var micJob: Job? = null

    fun speak(t: String, interrupt: Boolean = false) = speaker.speak(t, interrupt)
    fun stopSpeaking() = speaker.stop()
    fun stop() { micJob?.cancel(); rec?.cancel(); speaker.stop() }

    fun listen(scope: CoroutineScope, onLevel: (Float) -> Unit, onPartial: (String) -> Unit,
               onResult: (String) -> Unit, onFail: (String) -> Unit) {
        speaker.stop()
        micJob?.cancel(); rec?.cancel()
        if (prefs.useFishStt) listenFish(scope, onLevel, onPartial, onResult, onFail)
        else listenSystem(onPartial, onResult, onFail)
    }

    private fun listenFish(scope: CoroutineScope, onLevel: (Float) -> Unit, onPartial: (String) -> Unit,
                           onResult: (String) -> Unit, onFail: (String) -> Unit) {
        micJob = scope.launch {
            val wav = MicRecorder.record(ctx, onLevel)
            if (wav == null) { onFail("I didn't hear anything â€” check the microphone permission"); return@launch }
            onPartial("Transcribingâ€¦")
            try {
                val t = FishAudio.asr(prefs, wav)
                if (t.isBlank()) onFail("Didn't catch that") else onResult(t)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                BixbyEngine.notify("Fish Audio: ${e.message?.take(50)} â€” trying phone recognizer")
                listenSystem(onPartial, onResult, onFail)
            }
        }
    }

    /** Never pick our own stub recognition service (it only exists so Bixby can be the default assistant). */
    private fun pickRecognizer(): ComponentName? {
        val list = ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            .filter { it.packageName != ctx.packageName }
        return list.firstOrNull { it.packageName.contains("google") } ?: list.firstOrNull()
    }

    private fun listenSystem(onPartial: (String) -> Unit, onResult: (String) -> Unit, onFail: (String) -> Unit) {
        val comp = pickRecognizer()
        if (comp == null) { onFail("No speech recognizer installed â€” add a Fish Audio key in Settings"); return }
        rec?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx, comp)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onFail("Didn't catch that") else onResult(t)
            }
            override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
            override fun onError(e: Int) {
                onFail(when (e) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone permission"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognizer needs internet"
                    else -> "Didn't catch that" })
            }
            override fun onRmsChanged(v: Float) { BixbyEngine.level.value = ((v + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onReadyForSpeech(p: Bundle?) {}; override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
    }
}

