package com.bixbyx.kilo.assistant.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class HttpError(val code: Int, msg: String) : IOException(msg)

/** One client for every OpenAI-compatible provider: Kilo AI, OpenRouter, OpenAI, custom. Retries on timeouts / 429 / 5xx. */
object LlmClient {
    private val base = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()

    suspend fun chat(prefs: Prefs, messages: List<Pair<String, String>>, temperature: Double = 0.2,
                     onRetry: (String) -> Unit = {}): String {
        var last: Exception? = null
        for (attempt in 1..3) {
            try { return once(prefs, messages, temperature) }
            catch (e: CancellationException) { throw e }
            catch (e: HttpError) {
                if (e.code != 429 && e.code < 500) throw e
                last = e
            } catch (e: IOException) { last = e }
            if (attempt < 3) { onRetry("Slow answer, retrying ($attempt/2)â€¦"); delay(1200L * attempt) }
        }
        throw last ?: IOException("Request failed")
    }

    private suspend fun once(prefs: Prefs, messages: List<Pair<String, String>>, temperature: Double): String =
        withContext(Dispatchers.IO) {
            val b = prefs.baseUrl.trim().trimEnd('/')
            require(b.isNotEmpty()) { "Set a base URL in Settings" }
            require(prefs.key().isNotEmpty()) { "Add your ${prefs.provider.label} API key in Settings" }
            val url = if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
            val body = JSONObject().put("model", prefs.model()).put("messages", arr)
                .put("temperature", temperature).put("max_tokens", 700)
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer ${prefs.key()}")
                .header("HTTP-Referer", "https://github.com/KimKhoaDev/BixbyX").header("X-Title", "BixbyX")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val call = base.newBuilder().readTimeout(120, TimeUnit.SECONDS).build().newCall(req)
            val h = coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw HttpError(r.code, "${prefs.provider.label} ${r.code}: ${txt.take(160)}")
                    val c = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                    c.replace(Regex("(?s)<think>.*?</think>"), "").trim()
                }
            } finally { h?.dispose() }
        }

    suspend fun models(prefs: Prefs): List<String> = withContext(Dispatchers.IO) {
        val b = prefs.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        val req = Request.Builder().url("$b/models").header("Authorization", "Bearer ${prefs.key()}").build()
        base.newCall(req).execute().use { r ->
            val data = JSONObject(r.body?.string().orEmpty()).getJSONArray("data")
            (0 until data.length()).map { data.getJSONObject(it).getString("id") }.sorted()
        }
    }
}

