package com.bixbyx.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Provider(val label: String, val baseUrl: String, val defaultModel: String) {
    KILO("Kilo AI", "https://api.kilo.ai/api/gateway", "anthropic/claude-sonnet-4.5"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-oss-120b:free"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    CUSTOM("Custom", "", "")
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("bixbyx", Context.MODE_PRIVATE)

    var provider: Provider
        get() = runCatching { Provider.valueOf(sp.getString("provider", "KILO")!!) }.getOrDefault(Provider.KILO)
        set(v) = sp.edit().putString("provider", v.name).apply()

    fun key(p: Provider = provider) = sp.getString("key_${p.name}", "") ?: ""
    fun setKey(p: Provider, v: String) = sp.edit().putString("key_${p.name}", v.trim()).apply()
    fun model(p: Provider = provider) = sp.getString("model_${p.name}", null)?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun setModel(p: Provider, v: String) = sp.edit().putString("model_${p.name}", v.trim()).apply()

    var customUrl: String
        get() = sp.getString("custom_url", "") ?: ""
        set(v) = sp.edit().putString("custom_url", v.trim()).apply()
    val baseUrl get() = if (provider == Provider.CUSTOM) customUrl else provider.baseUrl

    var speak: Boolean
        get() = sp.getBoolean("speak", true); set(v) = sp.edit().putBoolean("speak", v).apply()
    var orbEnabled: Boolean
        get() = sp.getBoolean("orb", false); set(v) = sp.edit().putBoolean("orb", v).apply()
    var gestureWake: Boolean
        get() = sp.getBoolean("gesture", false); set(v) = sp.edit().putBoolean("gesture", v).apply()
    var volumeCombo: Boolean
        get() = sp.getBoolean("vol_combo", false); set(v) = sp.edit().putBoolean("vol_combo", v).apply()
    // ---- voice ----
    var fishKey: String
        get() = sp.getString("fish_key", "") ?: ""; set(v) = sp.edit().putString("fish_key", v.trim()).apply()
    var fishVoice: String
        get() = sp.getString("fish_voice", "") ?: ""; set(v) = sp.edit().putString("fish_voice", v.trim()).apply()
    var fishModel: String
        get() = sp.getString("fish_model", "s2-pro") ?: "s2-pro"; set(v) = sp.edit().putString("fish_model", v.trim()).apply()
    /** auto = Fish Audio when a key is set, else the phone's own engine. */
    var ttsEngine: String
        get() = sp.getString("tts_engine", "auto") ?: "auto"; set(v) = sp.edit().putString("tts_engine", v).apply()
    var sttEngine: String
        get() = sp.getString("stt_engine", "auto") ?: "auto"; set(v) = sp.edit().putString("stt_engine", v).apply()
    var narrate: Boolean
        get() = sp.getBoolean("narrate", false); set(v) = sp.edit().putBoolean("narrate", v).apply()
    var autoDescribe: Boolean
        get() = sp.getBoolean("auto_describe", true); set(v) = sp.edit().putBoolean("auto_describe", v).apply()
    var timeoutSec: Int
        get() = sp.getInt("timeout", 120); set(v) = sp.edit().putInt("timeout", v).apply()
    val useFishTts get() = fishKey.isNotBlank() && ttsEngine != "system"
    val useFishStt get() = fishKey.isNotBlank() && sttEngine != "system"

    var targetLang: String
        get() = sp.getString("lang", "English") ?: "English"; set(v) = sp.edit().putString("lang", v).apply()

    // ---- conversation history (Bixby "Conversation history") ----
    fun loadHistory(): List<com.bixbyx.assistant.agent.Msg> = runCatching {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); com.bixbyx.assistant.agent.Msg(o.getString("t"), o.getBoolean("u"), o.getLong("ts")) }
    }.getOrDefault(emptyList())

    fun saveHistory(list: List<com.bixbyx.assistant.agent.Msg>) {
        val a = JSONArray()
        list.takeLast(80).forEach { a.put(JSONObject().put("t", it.text).put("u", it.user).put("ts", it.ts)) }
        sp.edit().putString("history", a.toString()).apply()
    }
}
