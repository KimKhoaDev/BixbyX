package com.bixbyx.assistant.agent

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.bixbyx.assistant.service.AgentAccessibilityService
import com.bixbyx.assistant.service.NotifyListener
import org.json.JSONObject

/** One action chosen by the model. */
class Act(val j: JSONObject) {
    val action = j.optString("action")
    val thought = j.optString("thought")
    val say = j.optString("say")
    val id = j.optInt("id", -1)
    private fun pick(vararg keys: String): String = keys.firstNotNullOfOrNull { k -> j.optString(k).takeIf { it.isNotBlank() } } ?: ""
    // models often name arguments differently, so accept the usual alternatives
    val text = pick("text", "command", "cmd", "query", "q", "url", "content", "message", "app", "package")
    val name = pick("name", "skill", "text")
    val path = pick("path", "file", "filename", "filepath", "dir")
    val old = pick("old", "old_str", "old_text", "find")
    val new = if (j.has("new")) j.optString("new") else if (j.has("new_str")) j.optString("new_str") else j.optString("new_text")
    val append = j.optBoolean("append", false)
    val x1 = j.optInt("x1", -1)
    val y1 = j.optInt("y1", -1)
    val x2 = j.optInt("x2", -1)
    val y2 = j.optInt("y2", -1)
    val ms = j.optInt("ms", 500)
    val x = j.optInt("x", -1)
    val y = j.optInt("y", -1)
    val value = j.optInt("value", -1)
    val hour = j.optInt("hour", -1)
    val minute = j.optInt("minute", 0)
    val on = j.optBoolean("on", true)
}

/** A control shown under a reply so the user can adjust what Bixby just changed. */
data class Ctl(val type: String, val value: Int) {
    val title: String get() = when (type) {
        "wifi" -> "Wi-Fi"; "data" -> "Mobile data"; "bluetooth" -> "Bluetooth"; "flashlight" -> "Flashlight"
        "dark" -> "Dark mode"; "volume" -> "Media volume"; "brightness" -> "Brightness"; else -> type
    }
    val isSlider: Boolean get() = type == "volume" || type == "brightness"
}

/** Multiplies every screen coordinate, to turn screenshot pixels back into real pixels. */
fun Act.scaled(f: Float): Act {
    if (f == 1f) return this
    val o = JSONObject(j.toString())
    for (k in listOf("x", "y", "x1", "y1", "x2", "y2")) if (o.has(k)) o.put(k, (o.optDouble(k) * f).toInt())
    o.optJSONArray("strokes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            for (k in listOf("x", "y", "x1", "y1", "x2", "y2")) if (s.has(k)) s.put(k, (s.optDouble(k) * f).toInt())
        }
    }
    return Act(o)
}

fun Act.strokes(): List<AgentAccessibilityService.Stroke> {
    val arr = j.optJSONArray("strokes") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val s = arr.optJSONObject(i) ?: return@mapNotNull null
        val x1 = s.optDouble("x1", s.optDouble("x", -1.0)).toFloat()
        val y1 = s.optDouble("y1", s.optDouble("y", -1.0)).toFloat()
        AgentAccessibilityService.Stroke(x1, y1, s.optDouble("x2", x1.toDouble()).toFloat(), s.optDouble("y2", y1.toDouble()).toFloat(), s.optLong("ms", 500))
    }
}

object DeviceActions {
    private fun launch(ctx: Context, i: Intent): String =
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "OK" }.getOrElse { "Failed: ${it.message}" }

    /** Runs one action. Blocking: call from a background thread. */
    fun execute(ctx: Context, a: Act): String {
        val svc = AgentAccessibilityService.instance
        fun needA11y(block: (AgentAccessibilityService) -> Boolean): String =
            if (svc == null) "Screen control is off. Ask the user to turn it on in Settings."
            else if (block(svc)) "OK" else "Failed"
        return when (a.action) {
            "open_app" -> openApp(ctx, a.text)
            "open_url" -> launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(a.text)))
            "tap" -> needA11y { it.tapId(a.id) }
            "tap_xy" -> needA11y { it.tap(a.x, a.y) }
            "long_press" -> needA11y { it.tap(a.x, a.y, 700) }
            "hold" -> needA11y { it.tap(a.x, a.y, a.ms.toLong().coerceIn(50, 8000)) }
            "swipe" -> needA11y { it.swipe(a.x1, a.y1, a.x2, a.y2, a.ms.toLong().coerceIn(50, 8000)) }
            "gesture" -> needA11y { it.multi(a.strokes()) }
            "type" -> needA11y { it.type(a.text, a.j.optBoolean("submit", false)) }
            "scroll" -> needA11y { it.scroll(a.text.ifBlank { "down" }) }
            "back", "home", "recents", "notifications", "quick_settings", "lock", "screenshot" -> needA11y { it.global(a.action) }
            "wait" -> "OK"
            "flashlight" -> setControl(ctx, "flashlight", if (a.on) 1 else 0)
            "volume" -> setControl(ctx, "volume", a.value)
            "brightness" -> setControl(ctx, "brightness", a.value)
            "alarm" -> launch(ctx, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, a.minute).putExtra(AlarmClock.EXTRA_MESSAGE, a.text).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "timer" -> launch(ctx, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, a.value).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "call" -> launch(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:${a.text}")))
            "shell", "run_command" -> Tools.runCommand(a.text)
            "read_file" -> Tools.readFile(a.path)
            "write_file" -> Tools.writeFile(a.path, a.text, a.append)
            "edit_file" -> Tools.editFile(a.path, a.old, a.new)
            "list_dir" -> Tools.listDir(a.path)
            "search_files" -> Tools.searchFiles(a.path, a.text)
            "delete_file" -> Tools.deleteFile(a.path)
            "web_fetch" -> Tools.webFetch(a.text)
            "web_search" -> Tools.webSearch(a.text)
            "clipboard_get" -> Tools.clipboardGet(ctx)
            "clipboard_set" -> Tools.clipboardSet(ctx, a.text)
            "share" -> Tools.share(ctx, a.text)
            "open_settings" -> Tools.openSettings(ctx, a.text)
            "device_info" -> Diagnostics.read(ctx).asText()
            "memory_add" -> Tools.memoryAdd(a.text)
            "use_skill" -> Skills.read(a.name)?.let { "Skill instructions. Follow them now with the other tools:\n$it" } ?: "No skill named '${a.name}'. Available:\n${Skills.summary()}"
            "read_notifications" -> NotifyListener.instance?.snapshot()?.ifBlank { "No notifications" } ?: "Notification access is off. Turn it on in Settings."
            "clear_notifications" -> NotifyListener.instance?.let { it.cancelAllNotifications(); "OK" } ?: "Notification access is off. Turn it on in Settings."
            "calendar" -> Tools.calendar(ctx, if (a.value > 0) a.value else 1)
            "speak" -> { BixbyEngine.speak(a.text); "OK" }
            else -> "Unknown action '${a.action}'"
        }
    }

    /** Used by the agent and by the sliders and switches under a reply. */
    fun setControl(ctx: Context, type: String, value: Int): String = when (type) {
        "flashlight" -> runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList.first(), value == 1); "OK"
        }.getOrElse { "Failed: ${it.message}" }
        "volume" -> {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * value.coerceIn(0, 100) / 100f).toInt(), AudioManager.FLAG_SHOW_UI); "OK"
        }
        "brightness" -> {
            val v = (255 * value.coerceIn(1, 100) / 100f).toInt()
            if (Settings.System.canWrite(ctx)) { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v); "OK" }
            else ShizukuShell.exec("settings put system screen_brightness $v")
        }
        "wifi" -> ShizukuShell.exec("svc wifi ${if (value == 1) "enable" else "disable"}")
        "data" -> ShizukuShell.exec("svc data ${if (value == 1) "enable" else "disable"}")
        "bluetooth" -> ShizukuShell.exec("cmd bluetooth_manager ${if (value == 1) "enable" else "disable"}")
        "dark" -> ShizukuShell.exec("cmd uimode night ${if (value == 1) "yes" else "no"}")
        else -> "Unknown control"
    }

    /** Which control a finished action maps to, so the reply can show it. */
    fun controlFor(a: Act): Ctl? = when (a.action) {
        "flashlight" -> Ctl("flashlight", if (a.on) 1 else 0)
        "volume" -> Ctl("volume", a.value.coerceIn(0, 100))
        "brightness" -> Ctl("brightness", a.value.coerceIn(1, 100))
        "shell", "run_command" -> {
            val c = a.text.lowercase(); val on = c.contains("enable") || c.endsWith("yes")
            when {
                c.contains("svc wifi") -> Ctl("wifi", if (on) 1 else 0)
                c.contains("svc data") -> Ctl("data", if (on) 1 else 0)
                c.contains("bluetooth_manager") -> Ctl("bluetooth", if (on) 1 else 0)
                c.contains("uimode night") -> Ctl("dark", if (on) 1 else 0)
                else -> null
            }
        }
        else -> null
    }

    fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().equals(name, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(name, true) }
            ?: apps.firstOrNull { it.activityInfo.packageName.contains(name.replace(" ", ""), true) }
            ?: return "No installed app matches '$name'"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Cannot launch"
        return launch(ctx, i)
    }
}
