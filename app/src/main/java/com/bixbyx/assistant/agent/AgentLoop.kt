package com.bixbyx.assistant.agent

import android.content.Context
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Prefs
import com.bixbyx.assistant.service.AgentAccessibilityService
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Observe screen -> ask LLM -> run one action -> repeat (same loop as private-agent). */
class AgentLoop(private val ctx: Context, private val prefs: Prefs) {
    private val system = """
You are Bixby, a voice assistant that controls an Android phone (Xiaomi / HyperOS). Reply with EXACTLY ONE JSON object, no prose, no markdown.
Schema: {"thought":"short","action":"<name>", ...params, "say":"only for done"}
Actions:
 open_app{text:app name} open_url{text} tap{id} tap_xy{x,y} long_press{x,y} type{text,submit:bool} scroll{text:up|down|left|right}
 back home recents notifications quick_settings lock screenshot wait
 flashlight{on} volume{value:0-100} brightness{value:1-100} alarm{hour,minute,text} timer{value:seconds} call{text:number}
 shell{text:command}  (adb-level via Shizuku: svc wifi enable|disable, svc data enable|disable, cmd bluetooth_manager enable|disable, cmd uimode night yes|no, am force-stop <pkg>, input keyevent <n>, cmd statusbar expand-notifications. Never destructive commands.)
 done{say:final answer to the user}
Rules: prefer direct actions (open_app, alarm, flashlight, shell) over tapping. Use tap with the element [id] from the screen list.
After each action you get the new screen. Finish with done as soon as the task is complete. For plain questions or chat answer immediately with done. Keep "say" short and friendly, in the user's language.
Never send money, delete data, or post publicly unless the user explicitly asked.""".trimIndent()

    suspend fun run(task: String, history: List<Msg>, status: (String) -> Unit): String {
        val convo = history.dropLast(1).takeLast(6).joinToString("\n") { (if (it.user) "User: " else "Bixby: ") + it.text.take(300) }
        val trail = ArrayDeque<String>()
        var parseFails = 0
        repeat(18) { step ->
            val svc = AgentAccessibilityService.instance
            val screen = if (svc != null) "Foreground app: ${svc.currentPackage()}\n${svc.dump().take(6000)}" else "(screen control not enabled)"
            val prompt = buildString {
                if (convo.isNotBlank()) append("Recent conversation:\n$convo\n\n")
                append("TASK: $task\n\nSteps so far:\n${trail.joinToString("\n").ifBlank { "(none)" }}\n\nCurrent screen:\n$screen\n\nNext JSON action?")
            }
            val raw = LlmClient.chat(prefs, listOf("system" to system, "user" to prompt))
            val json = extractJson(raw)
            if (json == null) {
                if (++parseFails > 2) return raw.take(400).ifBlank { "I couldn't understand the model's reply." }
                trail += "(invalid reply, send one JSON object)"; return@repeat
            }
            val a = Act(json)
            status(a.thought.ifBlank { a.action })
            if (a.action == "done") return a.say.ifBlank { "Done." }
            val res = DeviceActions.execute(ctx, a)
            trail += "${step + 1}. ${a.action} ${a.text.take(40)}${if (a.id > 0) " #${a.id}" else ""} -> $res"
            if (trail.size > 8) trail.removeFirst()
            delay(if (a.action == "open_app" || a.action == "open_url") 1500 else 700)
        }
        return "I stopped after 18 steps. Tell me if you want me to continue."
    }

    private fun extractJson(s: String): JSONObject? {
        val i = s.indexOf('{'); val j = s.lastIndexOf('}')
        if (i < 0 || j <= i) return null
        return runCatching { JSONObject(s.substring(i, j + 1)) }.getOrNull()
    }
}
