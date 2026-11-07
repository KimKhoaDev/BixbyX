package com.bixbyx.assistant.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceController(private val ctx: Context) {
    private var tts: TextToSpeech? = TextToSpeech(ctx) { it.let { _ -> } }
    private var rec: SpeechRecognizer? = null

    fun speak(t: String) { tts?.speak(t.take(400), TextToSpeech.QUEUE_FLUSH, null, "bx") }
    fun stop() { tts?.stop(); rec?.cancel() }

    fun listen(onPartial: (String) -> Unit, onResult: (String) -> Unit, onFail: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return onFail("Speech recognition not available on this device")
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) onFail("Didn't catch that") else onResult(t)
                }
                override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
                override fun onError(e: Int) { onFail(if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Allow microphone permission" else "Didn't catch that") }
                override fun onReadyForSpeech(p: Bundle?) {}; override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}; override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}; override fun onEvent(t: Int, b: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
        }
    }
}
