package com.bixbyx.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One client for every OpenAI-compatible provider: Kilo AI, OpenRouter, OpenAI, custom. */
object LlmClient {
    private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()

    suspend fun chat(prefs: Prefs, messages: List<Pair<String, String>>, temperature: Double = 0.2): String =
        withContext(Dispatchers.IO) {
            val base = prefs.baseUrl.trim().trimEnd('/')
            require(base.isNotEmpty()) { "Set a base URL in Settings" }
            require(prefs.key().isNotEmpty()) { "Add your ${prefs.provider.label} API key in Settings" }
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
            val body = JSONObject().put("model", prefs.model()).put("messages", arr)
                .put("temperature", temperature).put("max_tokens", 1500)
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer ${prefs.key()}")
                .header("HTTP-Referer", "https://github.com/bixbyx") // OpenRouter ranking headers (harmless elsewhere)
                .header("X-Title", "BixbyX")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("${prefs.provider.label} ${r.code}: ${txt.take(200)}")
                val content = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content")
                content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            }
        }

    /** Lists model ids from the provider's /models endpoint. */
    suspend fun models(prefs: Prefs): List<String> = withContext(Dispatchers.IO) {
        val base = prefs.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        val req = Request.Builder().url("$base/models").header("Authorization", "Bearer ${prefs.key()}").build()
        http.newCall(req).execute().use { r ->
            val data = JSONObject(r.body?.string().orEmpty()).getJSONArray("data")
            (0 until data.length()).map { data.getJSONObject(it).getString("id") }.sorted()
        }
    }
}
