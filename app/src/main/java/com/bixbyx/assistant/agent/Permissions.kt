package com.bixbyx.assistant.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Grants Bixby's own permissions through Shizuku, so nothing has to be turned on by hand. */
object Permissions {
    private const val PKG = "com.bixbyx.assistant"
    private const val A11Y = "$PKG/$PKG.service.AgentAccessibilityService"
    private const val VIS = "$PKG/$PKG.service.BixbyVoiceInteractionService"
    private val runtime = listOf(Manifest.permission.RECORD_AUDIO to "Microphone", Manifest.permission.CALL_PHONE to "Phone calls")
    private var lastTry = 0L

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun secure(ctx: Context, key: String) = runCatching { Settings.Secure.getString(ctx.contentResolver, key) }.getOrNull().orEmpty()

    /** What is still missing, in plain words. */
    fun missing(ctx: Context): List<String> = buildList {
        runtime.forEach { (p, n) -> if (!has(ctx, p)) add(n) }
        if (Build.VERSION.SDK_INT >= 33 && !has(ctx, Manifest.permission.POST_NOTIFICATIONS)) add("Notifications")
        if (!Settings.canDrawOverlays(ctx)) add("Display over other apps")
        if (!Settings.System.canWrite(ctx)) add("Modify system settings")
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) add("All files access")
        if (!secure(ctx, "enabled_accessibility_services").contains(A11Y)) add("Screen control")
        if (!(secure(ctx, "assistant").startsWith(PKG) || secure(ctx, "voice_interaction_service").startsWith(PKG))) add("Default assistant")
        if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(PKG)) add("Battery")
    }

    /** Blocking: call off the main thread. Returns what it granted. */
    fun grant(ctx: Context): List<String> {
        if (!ShizukuShell.granted()) return emptyList()
        val done = ArrayList<String>()
        fun sh(label: String?, cmd: String) {
            val r = ShizukuShell.execTrusted(cmd)
            val failed = r.contains("Exception") || r.contains("Error") || r.contains("Unknown")
            if (!failed && label != null) done += label
        }
        runtime.forEach { (p, n) -> if (!has(ctx, p)) sh(n, "pm grant $PKG $p") }
        if (Build.VERSION.SDK_INT >= 33 && !has(ctx, Manifest.permission.POST_NOTIFICATIONS)) sh("Notifications", "pm grant $PKG ${Manifest.permission.POST_NOTIFICATIONS}")
        if (!Settings.canDrawOverlays(ctx)) sh("Display over other apps", "appops set $PKG SYSTEM_ALERT_WINDOW allow")
        if (!Settings.System.canWrite(ctx)) sh("Modify system settings", "appops set $PKG WRITE_SETTINGS allow")
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) sh("All files access", "appops set $PKG MANAGE_EXTERNAL_STORAGE allow")
        if (!secure(ctx, "enabled_accessibility_services").contains(A11Y)) {
            val cur = ShizukuShell.execTrusted("settings get secure enabled_accessibility_services").trim()
            val value = if (cur.isBlank() || cur == "null" || cur == "OK") A11Y else if (cur.contains(A11Y)) cur else "$cur:$A11Y"
            sh("Screen control", "settings put secure enabled_accessibility_services $value")
            sh(null, "settings put secure accessibility_enabled 1")
        }
        if (!(secure(ctx, "assistant").startsWith(PKG) || secure(ctx, "voice_interaction_service").startsWith(PKG))) {
            sh("Default assistant", "settings put secure assistant $VIS")
            sh(null, "settings put secure voice_interaction_service $VIS")
        }
        if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(PKG)) sh("Battery", "dumpsys deviceidle whitelist +$PKG")
        // background and pop-up rights; best effort, the numbered ops are Xiaomi's (pop-ups, lock screen, autostart)
        listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "10021", "10020", "10008").forEach { sh(null, "appops set $PKG $it allow") }
        return done
    }

    /** Runs when Shizuku is available and something is missing. At most once a minute. */
    fun autoGrant(ctx: Context) {
        if (!ShizukuShell.granted()) return
        val now = System.currentTimeMillis()
        if (now - lastTry < 60_000 || missing(ctx).isEmpty()) return
        lastTry = now
        BixbyEngine.scope.launch {
            val done = withContext(Dispatchers.IO) { grant(ctx) }
            if (done.isNotEmpty()) BixbyEngine.notify("Granted: " + done.joinToString())
        }
    }
}
