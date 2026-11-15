package com.bixbyx.assistant.ai

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

/** One client for every OpenAI-compatible provider: Kilo AI, OpenRouter, OpenAI, custom. Streams, and retries on timeouts / 429 / 5xx. */
object LlmClient {
    private val base = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()

    private fun strip(s: String): String {
        var t = s.replace(Regex("(?s)<think>.*?</think>"), "")
        if (t.contains("<think>")) t = t.substringBefore("<think>")   // still thinking, nothing to show yet
        return t.trim()
    }

    /** Opens the connection ahead of time (DNS and TLS), so the first real request is faster. */
    suspend fun warmUp(prefs: Prefs) = withContext(Dispatchers.IO) {
        runCatching {
            val b = prefs.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
            if (b.isNotEmpty()) base.newCall(Request.Builder().url(b).head().build()).execute().close()
        }
    }

    /**
     * onToken: called with the text so far while the reply streams in.
     * onRetry: called with a short message when a request is retried.
     */
    suspend fun chat(prefs: Prefs, messages: List<Pair<String, String>>, temperature: Double = 0.2,
                     imageB64: String? = null, extraImages: List<String> = emptyList(),
                     onToken: ((String) -> Unit)? = null, onRetry: (String) -> Unit = {}): String {
        var last: Exception? = null
        for (attempt in 1..3) {
            try { return once(prefs, messages, temperature, listOfNotNull(imageB64) + extraImages, onToken) }
            catch (e: CancellationException) { throw e }
            catch (e: HttpError) {
                if (e.code != 429 && e.code < 500) throw e
                last = e
            } catch (e: IOException) { last = e }
            if (attempt < 3) { onRetry("Slow answer, retrying ($attempt/2)"); delay(1200L * attempt) }
        }
        throw last ?: IOException("Request failed")
    }

    private suspend fun once(prefs: Prefs, messages: List<Pair<String, String>>, temperature: Double,
                             images: List<String>, onToken: ((String) -> Unit)?): String =
        withContext(Dispatchers.IO) {
            val b = prefs.baseUrl.trim().trimEnd('/')
            require(b.isNotEmpty()) { "Set a base URL in Settings" }
            require(prefs.key().isNotEmpty()) { "Add your ${prefs.provider.label} API key in Settings" }
            val url = if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
            val arr = JSONArray()
            messages.forEachIndexed { i, m ->
                if (images.isNotEmpty() && i == messages.lastIndex && m.first == "user") {
                    val parts = JSONArray().put(JSONObject().put("type", "text").put("text", m.second))
                    images.forEach { parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) }
                    arr.put(JSONObject().put("role", m.first).put("content", parts))
                } else arr.put(JSONObject().put("role", m.first).put("content", m.second))
            }
            val body = JSONObject().put("model", prefs.model()).put("messages", arr)
                .put("temperature", temperature).put("max_tokens", 700)
            if (onToken != null) body.put("stream", true)
            // a short reasoning budget keeps thinking models quick (OpenRouter ignores it for models that do not reason)
            if (prefs.provider == Provider.OPENROUTER) body.put("reasoning", JSONObject().put("effort", "low"))
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer ${prefs.key()}")
                .header("HTTP-Referer", "https://github.com/KimKhoaDev/BixbyX").header("X-Title", "BixbyX")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val call = base.newBuilder().readTimeout(120, TimeUnit.SECONDS).build().newCall(req)
            val h = coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { r ->
                    if (!r.isSuccessful) throw HttpError(r.code, "${prefs.provider.label} ${r.code}: ${r.body?.string().orEmpty().take(160)}")
                    val streaming = onToken != null && r.header("Content-Type").orEmpty().contains("event-stream")
                    if (streaming) {
                        val src = r.body!!.source()
                        val acc = StringBuilder()
                        var lastEmit = 0L
                        while (true) {
                            val line = src.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            val delta = runCatching {
                                val d = JSONObject(data).getJSONArray("choices").getJSONObject(0).optJSONObject("delta")
                                if (d == null || d.isNull("content")) "" else d.getString("content")
                            }.getOrDefault("")
                            if (delta.isEmpty()) continue
                            acc.append(delta)
                            val now = System.nanoTime()
                            if (now - lastEmit > 60_000_000L) { lastEmit = now; onToken!!(strip(acc.toString())) }
                        }
                        val full = strip(acc.toString())
                        onToken!!(full)
                        full
                    } else {
                        val txt = r.body?.string().orEmpty()
                        val c = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                        strip(c)
                    }
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
