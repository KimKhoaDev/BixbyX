package com.bixbyx.assistant.agent

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
                val m = ms.getJSONObject(i)
                val cs = m.optJSONArray("c")
                val st = m.optJSONArray("s")
                Msg(m.getString("t"), m.getBoolean("u"), m.getLong("ts"),
                    if (cs == null) emptyList() else (0 until cs.length()).map { k -> val o2 = cs.getJSONObject(k); Ctl(o2.getString("k"), o2.getInt("v")) },
                    if (st == null) emptyList() else (0 until st.length()).map { k ->
                        val o3 = st.getJSONObject(k)
                        Step(k + 1, StepKind.entries[o3.getInt("k")], o3.getString("t"), StepState.entries[o3.getInt("s")], o3.optString("i"), o3.optString("o")) }) })
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Chat>) {
        runCatching {
            val a = JSONArray()
            list.forEach { c ->
                val ms = JSONArray()
                c.messages.takeLast(200).forEach { m ->
                    val cs = JSONArray(); m.controls.forEach { cs.put(JSONObject().put("k", it.type).put("v", it.value)) }
                    val st = JSONArray()
                    m.steps.takeLast(40).forEach { s -> st.put(JSONObject().put("k", s.kind.ordinal).put("t", s.text).put("s", s.state.ordinal)
                        .put("i", s.input.take(300)).put("o", s.output.take(800))) }
                    ms.put(JSONObject().put("t", m.text).put("u", m.user).put("ts", m.ts).put("c", cs).put("s", st))
                }
                a.put(JSONObject().put("id", c.id).put("t", c.title).put("u", c.updated).put("m", ms))
            }
            file.writeText(a.toString())
        }
    }
}
