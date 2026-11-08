package com.bixbyx.kilo.assistant.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Chat(val id: String, val title: String, val updated: Long, val messages: List<Msg>)

class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chats.json")

    fun load(): List<Chat> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it); val ms = o.getJSONArray("m")
            Chat(o.getString("id"), o.getString("t"), o.getLong("u"), (0 until ms.length()).map { i ->
                val m = ms.getJSONObject(i); Msg(m.getString("t"), m.getBoolean("u"), m.getLong("ts")) })
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Chat>) {
        runCatching {
            val a = JSONArray()
            list.forEach { c ->
                val ms = JSONArray()
                c.messages.takeLast(200).forEach { ms.put(JSONObject().put("t", it.text).put("u", it.user).put("ts", it.ts)) }
                a.put(JSONObject().put("id", c.id).put("t", c.title).put("u", c.updated).put("m", ms))
            }
            file.writeText(a.toString())
        }
    }
}

