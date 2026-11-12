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

class FishError(val code: Int, message: String) : Exception(message)

/** Fish Audio: text to speech (/v1/tts) and speech to text (/v1/asr). Both need a `model` header. */
object FishAudio {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    val ttsModels = listOf("s2.1-pro", "s2-pro", "s1")
    private val asrModels = listOf("transcribe-1-pro", "transcribe-1")
    private val fatal = listOf(401, 402, 403, 429)   // another model will not fix these

    private fun explain(code: Int, body: String) = when (code) {
        401 -> "the API key was rejected"
        402 -> "no credit left on this account"
        403 -> "access denied for this key"
        429 -> "rate limit reached, try again shortly"
        else -> "HTTP $code ${body.replace(Regex("\\s+"), " ").take(140)}"
    }

    suspend fun tts(prefs: Prefs, text: String): ByteArray = withContext(Dispatchers.IO) {
        var last: FishError? = null
        for (m in (listOf(prefs.fishModel) + ttsModels).filter { it.isNotBlank() }.distinct()) {
            try { return@withContext callTts(prefs, text, m) }
            catch (e: FishError) { last = e; if (e.code in fatal) throw e }
        }
        throw last ?: FishError(0, "no response")
    }

    private fun callTts(prefs: Prefs, text: String, model: String): ByteArray {
        val body = JSONObject().put("text", text.take(700)).put("format", "mp3")
        if (prefs.fishVoice.isNotBlank()) body.put("reference_id", prefs.fishVoice)
        val req = Request.Builder().url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer ${prefs.fishKey}").header("model", model)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) { val t = r.body?.string().orEmpty(); throw FishError(r.code, "speech ($model): " + explain(r.code, t)) }
            val bytes = r.body!!.bytes()
            if (r.body?.contentType()?.subtype == "json" || bytes.size < 200)
                throw FishError(r.code, "speech ($model): unexpected reply " + String(bytes).take(120))
            return bytes
        }
    }

    suspend fun asr(prefs: Prefs, wav: ByteArray): String = withContext(Dispatchers.IO) {
        var last: FishError? = null
        for (m in asrModels) {
            try { return@withContext callAsr(prefs, wav, m) }
            catch (e: FishError) { last = e; if (e.code in fatal) throw e }
        }
        throw last ?: FishError(0, "no response")
    }

    private fun callAsr(prefs: Prefs, wav: ByteArray, model: String): String {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("audio", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("ignore_timestamps", "true").build()
        val req = Request.Builder().url("https://api.fish.audio/v1/asr")
            .header("Authorization", "Bearer ${prefs.fishKey}").header("model", model).post(form).build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw FishError(r.code, "recognition ($model): " + explain(r.code, txt))
            return JSONObject(txt).optString("text").trim()
        }
    }
}
