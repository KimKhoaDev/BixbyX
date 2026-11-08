package com.bixbyx.kilo.assistant.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Checks GitHub Releases (published automatically by CI) for a newer version. */
object Updater {
    const val REPO = "KimKhoaDev/BixbyX"
    data class Release(val tag: String, val page: String, val apk: String?)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json").build()
            OkHttpClient().newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val j = JSONObject(r.body!!.string())
                val a = j.optJSONArray("assets")
                Release(j.getString("tag_name"), j.getString("html_url"),
                    if (a != null && a.length() > 0) a.getJSONObject(0).optString("browser_download_url") else null)
            }
        }.getOrNull()
    }

    fun isNewer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(tag); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

