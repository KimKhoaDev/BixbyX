package com.bixbyx.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Fish Audio: natural text-to-speech (/v1/tts) and speech-to-text (/v1/asr). */
object FishAudio {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    suspend fun tts(prefs: Prefs, text: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject().put("text", text.take(700)).put("format", "mp3").put("latency", "normal")
        if (prefs.fishVoice.isNotBlank()) body.put("reference_id", prefs.fishVoice)
        val req = Request.Builder().url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer ${prefs.fishKey}").header("model", prefs.fishModel)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("TTS ${r.code}: ${r.body?.string()?.take(100)}")
            r.body!!.bytes()
        }
    }

    suspend fun asr(prefs: Prefs, wav: ByteArray): String = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("audio", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("ignore_timestamps", "true").build()
        val req = Request.Builder().url("https://api.fish.audio/v1/asr")
            .header("Authorization", "Bearer ${prefs.fishKey}").post(form).build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("ASR ${r.code}: ${txt.take(100)}")
            JSONObject(txt).optString("text").trim()
        }
    }
}
