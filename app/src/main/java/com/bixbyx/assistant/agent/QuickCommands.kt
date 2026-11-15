package com.bixbyx.assistant.agent

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain commands that need no model: they run at once, with no network. Anything else goes to the AI. */
object QuickCommands {
    class Result(val text: String, val steps: List<Step>, val controls: List<Ctl>)

    private val onOff = "(on|off)"
    private val flash = Regex("^(?:please )?(?:turn|switch) $onOff (?:the )?(?:flash ?light|torch)$|^(?:flash ?light|torch) $onOff$")
    private val volume = Regex("^(?:set )?(?:the )?(?:media )?volume (?:to )?(\\d{1,3}) ?%?$")
    private val brightness = Regex("^(?:set )?(?:the )?brightness (?:to )?(\\d{1,3}) ?%?$")
    private val toggle = Regex("^(?:please )?(?:turn|switch) $onOff (?:the )?(wi-?fi|bluetooth|mobile data|data|dark mode)$|^(wi-?fi|bluetooth|dark mode) $onOff$")
    private val openApp = Regex("^(?:open|launch|start) (.{2,40})$")
    private val alarm = Regex("^(?:set )?(?:an? )?alarm (?:for|at) (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?$")
    private val timer = Regex("^(?:set )?(?:a )?timer (?:for )?(\\d+) ?(seconds?|secs?|minutes?|mins?|hours?)$")
    private val call = Regex("^call ([+\\d][\\d +()-]{4,})$")
    private val time = Regex("^what(?:'s| is) the time$|^what time is it$|^the time$")
    private val date = Regex("^what(?:'s| is) (?:the )?(?:date|day)(?: today)?$|^what day is it$")
    private val battery = Regex("^(?:what(?:'s| is) )?(?:the )?battery(?: level)?$|^how much battery.*")
    private val viFlash = Regex("^(bật|tắt) đèn pin$")
    private val viApp = Regex("^mở (.{2,40})$")

    private fun run(ctx: Context, label: String, json: String, say: String): Result? {
        val a = Act(JSONObject(json))
        val res = DeviceActions.execute(ctx, a)
        // not possible this way (no Shizuku, app not found): let the AI find another way
        if (res.startsWith("Shizuku") || res.startsWith("No installed") || res.startsWith("Blocked")) return null
        val ok = res == "OK" || (a.action == "run_command" && !Regex("(?i)exception|denied|not found|error").containsMatchIn(res))
        val step = Step(1, StepKind.Ran, label, if (ok) StepState.Done else StepState.Failed, a.j.toString(), res)
        return Result(if (ok) say else "That did not work: $res", listOf(step), if (ok) listOfNotNull(DeviceActions.controlFor(a)) else emptyList())
    }

    private fun shell(ctx: Context, label: String, cmd: String, say: String): Result? =
        if (!ShizukuShell.granted()) null
        else run(ctx, label, JSONObject().put("action", "run_command").put("text", cmd).toString(), say)

    /** Returns null when the text is not a simple command. Blocking: call off the main thread. */
    fun tryRun(ctx: Context, raw: String): Result? {
        val t = raw.trim().lowercase().trimEnd('.', '!', '?')
        if (t.length > 90 || t.isBlank()) return null

        flash.matchEntire(t)?.let { m ->
            val on = (m.groupValues[1].ifEmpty { m.groupValues[2] }) == "on"
            return run(ctx, if (on) "Turning the flashlight on" else "Turning the flashlight off", """{"action":"flashlight","on":$on}""", if (on) "Flashlight on." else "Flashlight off.")
        }
        viFlash.matchEntire(t)?.let { m ->
            val on = m.groupValues[1] == "bật"
            return run(ctx, if (on) "Turning the flashlight on" else "Turning the flashlight off", """{"action":"flashlight","on":$on}""", if (on) "Đã bật đèn pin." else "Đã tắt đèn pin.")
        }
        volume.matchEntire(t)?.let { m ->
            val v = m.groupValues[1].toInt().coerceIn(0, 100)
            return run(ctx, "Setting volume to $v%", """{"action":"volume","value":$v}""", "Volume set to $v%.")
        }
        brightness.matchEntire(t)?.let { m ->
            val v = m.groupValues[1].toInt().coerceIn(1, 100)
            return run(ctx, "Setting brightness to $v%", """{"action":"brightness","value":$v}""", "Brightness set to $v%.")
        }
        toggle.matchEntire(t)?.let { m ->
            val on = (m.groupValues[1].ifEmpty { m.groupValues[4] }) == "on"
            val what = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val word = if (on) "enable" else "disable"
            val (label, cmd) = when {
                what.startsWith("wi") -> "Wi-Fi" to "svc wifi $word"
                what == "bluetooth" -> "Bluetooth" to "cmd bluetooth_manager $word"
                what == "dark mode" -> "Dark mode" to "cmd uimode night ${if (on) "yes" else "no"}"
                else -> "Mobile data" to "svc data $word"
            }
            return shell(ctx, "Turning $label ${if (on) "on" else "off"}", cmd, "$label ${if (on) "on" else "off"}.")
        }
        alarm.matchEntire(t)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            when (m.groupValues[3]) { "pm" -> if (h < 12) h += 12; "am" -> if (h == 12) h = 0 }
            if (h > 23 || min > 59) return null
            return run(ctx, "Setting an alarm for %02d:%02d".format(h, min), """{"action":"alarm","hour":$h,"minute":$min,"text":"Alarm"}""", "Alarm set for %02d:%02d.".format(h, min))
        }
        timer.matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2]
            val secs = when { unit.startsWith("h") -> n * 3600; unit.startsWith("m") -> n * 60; else -> n }
            return run(ctx, "Starting a timer", """{"action":"timer","value":$secs}""", "Timer started for $n ${unit.trimEnd('s')}${if (n == 1) "" else "s"}.")
        }
        call.matchEntire(t)?.let { m -> return run(ctx, "Calling ${m.groupValues[1]}", JSONObject().put("action", "call").put("text", m.groupValues[1].trim()).toString(), "Calling ${m.groupValues[1].trim()}.") }
        time.matchEntire(t)?.let { return Result(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), emptyList(), emptyList()) }
        date.matchEntire(t)?.let { return Result(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()), emptyList(), emptyList()) }
        battery.matchEntire(t)?.let { val h = Diagnostics.read(ctx); return Result("Battery is at ${h.battery}%${if (h.charging) " and charging" else ""}.", emptyList(), emptyList()) }
        (openApp.matchEntire(t) ?: viApp.matchEntire(t))?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.contains(" and ") || name.contains(" then ") || name.split(" ").size > 4) return null   // a longer task
            return run(ctx, "Opening $name", JSONObject().put("action", "open_app").put("text", name).toString(), "Opening $name.")
        }
        return null
    }
}
