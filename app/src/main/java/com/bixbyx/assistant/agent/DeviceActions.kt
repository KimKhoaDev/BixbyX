package com.bixbyx.assistant.agent

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.bixbyx.assistant.service.AgentAccessibilityService
import org.json.JSONObject

/** One action chosen by the model. */
class Act(val j: JSONObject) {
    val action = j.optString("action")
    val thought = j.optString("thought")
    val say = j.optString("say")
    val id = j.optInt("id", -1)
    val text = j.optString("text")
    val x = j.optInt("x", -1); val y = j.optInt("y", -1)
    val value = j.optInt("value", -1)
    val hour = j.optInt("hour", -1); val minute = j.optInt("minute", 0)
    val on = j.optBoolean("on", true)
}

object DeviceActions {
    private fun launch(ctx: Context, i: Intent): String =
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "OK" }.getOrElse { "Failed: ${it.message}" }

    fun execute(ctx: Context, a: Act): String {
        val svc = AgentAccessibilityService.instance
        fun needA11y(block: (AgentAccessibilityService) -> Boolean): String =
            if (svc == null) "Screen control is OFF. Ask the user to enable the Bixby Screen Control accessibility service."
            else if (block(svc)) "OK" else "Failed"
        return when (a.action) {
            "open_app" -> openApp(ctx, a.text)
            "open_url" -> launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(a.text)))
            "tap" -> needA11y { it.tapId(a.id) }
            "tap_xy" -> needA11y { it.tap(a.x, a.y) }
            "long_press" -> needA11y { it.tap(a.x, a.y, 700) }
            "type" -> needA11y { it.type(a.text, a.j.optBoolean("submit", false)) }
            "scroll" -> needA11y { it.scroll(a.text.ifBlank { "down" }) }
            "back", "home", "recents", "notifications", "quick_settings", "lock", "screenshot" -> needA11y { it.global(a.action) }
            "wait" -> "OK"
            "flashlight" -> runCatching {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cm.setTorchMode(cm.cameraIdList.first(), a.on); "OK"
            }.getOrElse { "Failed: ${it.message}" }
            "volume" -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * a.value.coerceIn(0, 100) / 100f).toInt(), AudioManager.FLAG_SHOW_UI); "OK"
            }
            "brightness" -> {
                val v = (255 * a.value.coerceIn(1, 100) / 100f).toInt()
                if (Settings.System.canWrite(ctx)) {
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v); "OK"
                } else ShizukuShell.exec("settings put system screen_brightness $v")
            }
            "alarm" -> launch(ctx, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, a.minute).putExtra(AlarmClock.EXTRA_MESSAGE, a.text).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "timer" -> launch(ctx, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, a.value).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "call" -> launch(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:${a.text}")))
            "shell" -> ShizukuShell.exec(a.text)
            else -> "Unknown action '${a.action}'"
        }
    }

    fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().equals(name, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(name, true) }
            ?: apps.firstOrNull { it.activityInfo.packageName.contains(name.replace(" ", ""), true) }
            ?: return "No installed app matches '$name'"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Cannot launch"
        return launch(ctx, i)
    }
}
