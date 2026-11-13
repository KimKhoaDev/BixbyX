package com.bixbyx.assistant.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Prefs
import com.bixbyx.assistant.service.AgentAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Reports every step so the UI can show them as they happen. */
interface AgentUi {
    fun thinking(text: String)
    fun thought(text: String)
    fun begin(text: String, input: String): Int
    fun end(n: Int, ok: Boolean, output: String)
}

data class AgentResult(val text: String, val controls: List<Ctl>)

/** Read the screen, ask the model for one action, run it, repeat. */
class AgentLoop(private val ctx: Context, private val prefs: Prefs) {
    private fun system(task: String) = """
You are Bixby, an assistant that controls an Android phone (Xiaomi, HyperOS). Reply with exactly one JSON object and nothing else.
Format: {"thought":"short","action":"<name>", ...params}
Phone: open_app{text} open_url{text} tap{id} tap_xy{x,y} long_press{x,y} type{text,submit} scroll{text:up|down|left|right} back home recents notifications quick_settings lock screenshot wait
  flashlight{on} volume{value:0-100} brightness{value:1-100} alarm{hour,minute,text} timer{value:seconds} call{text} open_settings{text:wifi|bluetooth|display|sound|battery|apps|location|storage} device_info speak{text}
Touch (games, and apps whose screen cannot be read): hold{x,y,ms} swipe{x1,y1,x2,y2,ms} gesture{strokes:[{x1,y1,x2,y2,ms},...]} (every stroke happens at the same moment)
System: run_command{text} (adb-level when Shizuku is on: svc wifi enable|disable, svc data enable|disable, cmd bluetooth_manager enable|disable, cmd uimode night yes|no, am force-stop <pkg>, input keyevent <n>)
Files (workspace, or /storage/emulated/0): read_file{path} write_file{path,text,append} edit_file{path,old,new} list_dir{path} search_files{path,text} delete_file{path} (delete: workspace only)
Web and data: web_search{text} web_fetch{text:url} clipboard_get clipboard_set{text} share{text} memory_add{text} use_skill{name} read_notifications clear_notifications calendar{value:days}
Finish: done{say}
Rules:
- Prefer direct actions over tapping. Tap by the [id] in the screen list.
- After each action you get the result and the new screen. Finish with done as soon as the task is complete.
- For questions and chat, answer straight away with done. Keep "say" short and plain, in the user's language.
- edit_file needs 'old' to match exactly once. Read a file before editing it.
- If a skill fits the task, call use_skill once, then carry out its instructions with the other tools. Never repeat the same action twice in a row.
- Never send money, post publicly or delete data unless the user clearly asked.
Memory:
${Tools.memory().ifBlank { "(empty)" }}
Skills (load with use_skill when relevant):
${Skills.summary().ifBlank { "(none)" }}
${Skills.matching(task).joinToString("\n") { (n, body) -> "\nSkill '$n' (already loaded, follow it):\n$body" }}""".trimIndent()

    private class Entry(val head: String, val result: String)

    private val gameWords = listOf("minecraft", "pojav", "zalith", "roblox", "rust", "play a game", "play the game", " game")
    private fun gameTask(task: String) = task.lowercase().let { t -> gameWords.any { t.contains(it) } }
    private fun gamePkg(pkg: String) = pkg.lowercase().let { p -> listOf("minecraft", "mojang", "pojav", "zalith", "roblox", "facepunch").any { p.contains(it) } }

    private fun encode(bmp: Bitmap, f: Float): Triple<String, Int, Int> {
        val w = (bmp.width * f).toInt().coerceAtLeast(1)
        val h = (bmp.height * f).toInt().coerceAtLeast(1)
        val small = if (f == 1f) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 60, out)
        return Triple(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), w, h)
    }

    suspend fun run(task: String, history: List<Msg>, ui: AgentUi, attachments: List<Attachment> = emptyList()): AgentResult {
        val convo = history.dropLast(1).takeLast(6).joinToString("\n") { (if (it.user) "User: " else "Bixby: ") + it.text.take(300) }
        val trail = ArrayList<Entry>()
        val controls = LinkedHashMap<String, Ctl>()
        val game = gameTask(task)
        val sys = system(task) + if (game) "\n" + GAME_RULES else ""
        val files = attachments.joinToString("\n") { f ->
            "- ${f.name} (${f.kind.name.lowercase()}), saved at ${f.path}" + (f.text?.let { "\n  Contents:\n  " + it.take(12000) } ?: "")
        }
        val attached = attachments.flatMap { it.images }.take(3)
        var parseFails = 0
        var lastSig = ""
        var repeats = 0
        val maxSteps = if (game) 200 else 25
        for (step in 0 until maxSteps) {
            val svc = AgentAccessibilityService.instance
            ui.thinking(if (step == 0) "Reading the screen" else "Thinking")
            val pkg = svc?.currentPackage().orEmpty()
            val dump = svc?.dump().orEmpty()
            val elements = if (dump.isBlank()) 0 else dump.lines().size
            val wantShot = svc != null && prefs.vision != 2 && (prefs.vision == 1 || game || gamePkg(pkg) || elements < 4)
            var shot: String? = null
            var scale = 1f
            var shotInfo = ""
            if (wantShot && svc != null) {
                val bmp = svc.screenshot() ?: run { delay(450); svc.screenshot() }
                if (bmp != null) {
                    val f = if (bmp.width > 720) 720f / bmp.width else 1f
                    val enc = withContext(Dispatchers.Default) { encode(bmp, f) }
                    shot = enc.first
                    scale = 1f / f
                    shotInfo = "A screenshot of the phone is attached, ${enc.second} x ${enc.third} pixels. Give tap_xy, hold, swipe and gesture coordinates in pixels of that image."
                }
            }
            val screen = if (svc != null) "Foreground app: $pkg\n" + dump.take(if (shot != null) 1500 else 4500) else "(screen control is off)"
            val steps = trail.takeLast(8).mapIndexed { i, e ->
                val last = i == trail.takeLast(8).lastIndex
                "${e.head} -> ${e.result.take(if (last) 3500 else 200)}"
            }.joinToString("\n").ifBlank { "(none)" }
            val prompt = buildString {
                if (convo.isNotBlank()) append("Recent conversation:\n$convo\n\n")
                if (files.isNotBlank()) append("Attached by the user:\n$files\n${if (attached.isNotEmpty()) "The attached images are sent with this message${if (shot != null) ", after the screenshot" else ""}.\n" else ""}\n")
                append("TASK: $task\n\nSteps so far:\n$steps\n\nCurrent screen:\n$screen\n$shotInfo\n\nNext JSON action?")
            }
            val raw = LlmClient.chat(prefs, listOf("system" to sys, "user" to prompt), imageB64 = shot, extraImages = attached) { ui.thinking(it) }
            val json = extractJson(raw)
            if (json == null) {
                if (++parseFails > 2) return AgentResult(raw.take(400).ifBlank { "I couldn't understand the reply." }, controls.values.toList())
                trail += Entry("(invalid reply)", "Send exactly one JSON object"); continue
            }
            val a = Act(json).scaled(scale)
            val sig = a.action + a.text + a.path + a.name + a.id + a.old
            repeats = if (sig == lastSig) repeats + 1 else 0
            lastSig = sig
            if (!game && a.action != "done" && repeats >= 1) {
                if (repeats >= 3) return AgentResult("I got stuck repeating the same step.", controls.values.toList())
                trail += Entry("${step + 1}. ${a.action}", "You already did this. Do the next step of the task or finish with done.")
                continue
            }
            if (a.action == "done") return AgentResult(a.say.ifBlank { "Done." }, controls.values.toList())
            if (a.thought.isNotBlank()) ui.thought(a.thought)
            val n = ui.begin(describe(a, svc), inputOf(a))
            val res = withContext(Dispatchers.IO) { DeviceActions.execute(ctx, a) }
            val ok = !(res.startsWith("Blocked") || res.startsWith("Failed") || res.startsWith("Shizuku") || res.contains("is off") || res.startsWith("Unknown")
                || res.startsWith("Path not allowed") || res.startsWith("Missing") || res.startsWith("No installed"))
            ui.end(n, ok, res.take(1500))
            if (ok) DeviceActions.controlFor(a)?.let { controls[it.type] = it }
            trail += Entry("${step + 1}. ${a.action} ${a.text.take(40)}${a.path.take(40)}${if (a.id > 0) " #${a.id}" else ""}", res)
            if (trail.size > 20) trail.removeAt(0)
            // wait for a touch to finish before looking at the screen again
            val pause = when {
                a.action == "open_app" || a.action == "open_url" -> 1500L
                game -> when (a.action) {
                    "hold", "swipe" -> a.ms + 150L
                    "gesture" -> (a.strokes().maxOfOrNull { it.ms } ?: 300L) + 150L
                    else -> 350L
                }
                else -> 600L
            }
            delay(pause.coerceIn(100L, 9000L))
        }
        return AgentResult("I stopped after $maxSteps steps. Ask me to continue if you want.", controls.values.toList())
    }

    /** What was sent, shown in the step details. */
    private fun inputOf(a: Act): String = when (a.action) {
        "shell", "run_command" -> a.text
        else -> JSONObject(a.j.toString()).apply { remove("thought") }.toString()
    }

    private fun short(s: String, n: Int = 28) = if (s.length > n) s.take(n) + "..." else s

    private fun describe(a: Act, svc: AgentAccessibilityService?): String = when (a.action) {
        "open_app" -> "Opening ${a.text}"
        "open_url" -> "Opening a link"
        "tap" -> svc?.labelFor(a.id)?.takeIf { it.isNotBlank() }?.let { "Tapping \"$it\"" } ?: "Tapping the screen"
        "tap_xy", "long_press" -> "Tapping the screen"
        "type" -> "Typing \"${short(a.text, 26)}\""
        "scroll" -> "Scrolling ${a.text.ifBlank { "down" }}"
        "back" -> "Going back"; "home" -> "Going home"; "recents" -> "Opening recent apps"
        "notifications" -> "Opening notifications"; "quick_settings" -> "Opening quick settings"
        "lock" -> "Locking the screen"; "screenshot" -> "Taking a screenshot"
        "wait" -> "Waiting"
        "hold" -> "Holding the screen"
        "swipe" -> "Swiping"
        "gesture" -> "Using several fingers"
        "flashlight" -> if (a.on) "Turning the flashlight on" else "Turning the flashlight off"
        "volume" -> "Setting volume to ${a.value}%"
        "brightness" -> "Setting brightness to ${a.value}%"
        "alarm" -> "Setting an alarm for %02d:%02d".format(a.hour, a.minute)
        "timer" -> "Starting a timer"
        "call" -> "Calling ${a.text}"
        "open_settings" -> "Opening ${a.text} settings"
        "shell", "run_command" -> "Running \"${short(a.text, 30)}\""
        "read_file" -> "Reading ${short(a.path.substringAfterLast('/'))}"
        "write_file" -> "Writing ${short(a.path.substringAfterLast('/'))}"
        "edit_file" -> "Editing ${short(a.path.substringAfterLast('/'))}"
        "list_dir" -> "Listing ${short(a.path.ifBlank { "workspace" }.substringAfterLast('/').ifBlank { "files" })}"
        "search_files" -> "Searching files for \"${short(a.text, 20)}\""
        "delete_file" -> "Deleting ${short(a.path.substringAfterLast('/'))}"
        "web_search" -> "Searching the web for \"${short(a.text, 24)}\""
        "web_fetch" -> "Reading a web page"
        "clipboard_get" -> "Reading the clipboard"; "clipboard_set" -> "Copying to the clipboard"
        "share" -> "Sharing"
        "memory_add" -> "Saving to memory"
        "use_skill" -> "Using skill ${a.name}"
        "device_info" -> "Checking the device"
        "read_notifications" -> "Reading notifications"
        "clear_notifications" -> "Clearing notifications"
        "calendar" -> "Checking the calendar"
        "speak" -> "Speaking"
        else -> a.action.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun extractJson(s: String): JSONObject? {
        val i = s.indexOf('{'); val j = s.lastIndexOf('}')
        if (i < 0 || j <= i) return null
        return runCatching { JSONObject(s.substring(i, j + 1)) }.getOrNull()
    }
}

private val GAME_RULES = """
You are playing a game on the phone through touch. Every step you get a screenshot, so find the real controls in it before acting and never guess coordinates.
- hold{x,y,ms} keeps a finger on a button (a movement arrow, mine or attack). swipe{x1,y1,x2,y2,ms} looks around or drags items. tap_xy taps. gesture{strokes:[...]} does several touches at once, for example hold forward while swiping to look.
- Use short actions (200 to 1500 ms), look at the next screenshot, and adjust. Keep playing toward the user's goal. Call done only when the goal is reached or you cannot continue, and say what happened.
- Minecraft Bedrock (Pocket Edition) on Android: default touch controls have a movement pad at the bottom left, jump at the bottom right, the hotbar at the bottom centre with the inventory button beside it, and you look by dragging on the right side. Hold a finger on a block to mine it, tap to place or use.
- Minecraft Java through a launcher such as PojavLauncher: the on-screen keyboard and mouse overlays are used. Tap the on-screen keys (W, space, E and so on) and drag the screen to look.
- Survival basics: punch trees for wood first, make a crafting table and tools, avoid lava and deep water, eat when hungry, and build a shelter before night. In Rust, gather wood and stone, avoid other players at first, and keep an eye on health and hunger.
""".trimIndent()
