package com.bixbyx.kilo.assistant.agent

import android.content.Context
import org.json.JSONArray

/** Simple persistent memory: key-value store plus a shortlist of facts Bixby learns about the user/device. */
class Memory(ctx: Context) {
    private val sp = ctx.getSharedPreferences("bixbyx_memory", Context.MODE_PRIVATE)

    fun get(key: String): String = sp.getString(key, "") ?: ""
    fun put(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun remove(key: String) = sp.edit().remove(key).apply()

    val facts: List<String>
        get() = runCatching {
            val a = JSONArray(sp.getString("facts", "[]"))
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())

    fun addFact(fact: String) {
        val trimmed = fact.trim()
        if (trimmed.isBlank() || trimmed in facts) return
        val cur = facts
        if (cur.size >= 50) return
        val a = JSONArray()
        (cur + trimmed).forEach { a.put(it) }
        sp.edit().putString("facts", a.toString()).apply()
    }

    fun clearFacts() = sp.edit().putString("facts", "[]").apply()
}
