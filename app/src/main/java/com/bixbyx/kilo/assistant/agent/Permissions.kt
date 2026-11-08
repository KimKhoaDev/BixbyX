package com.bixbyx.kilo.assistant.agent

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Declared + common dangerous permissions BixbyX needs to fully control the device. */
private val ALL_PERMS = listOf(
    android.Manifest.permission.RECORD_AUDIO,
    android.Manifest.permission.CAMERA,
    android.Manifest.permission.READ_CONTACTS,
    android.Manifest.permission.WRITE_CONTACTS,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
    android.Manifest.permission.READ_PHONE_STATE,
    android.Manifest.permission.CALL_PHONE,
    android.Manifest.permission.ANSWER_PHONE_CALLS,
    android.Manifest.permission.READ_SMS,
    android.Manifest.permission.RECEIVE_SMS,
    android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.READ_CALL_LOG,
    android.Manifest.permission.WRITE_CALL_LOG,
    android.Manifest.permission.READ_EXTERNAL_STORAGE,
    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
    android.Manifest.permission.ACCESS_NOTIFICATION_POLICY,
    android.Manifest.permission.POST_NOTIFICATIONS,
    android.Manifest.permission.WRITE_SETTINGS,
    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
    android.Manifest.permission.BLUETOOTH_CONNECT,
    android.Manifest.permission.BLUETOOTH_SCAN,
    android.Manifest.permission.NFC,
    android.Manifest.permission.UWB_RANGING,
    android.Manifest.permission.ACTIVITY_RECOGNITION,
)

/** Grants every missing permission via Shizuku (pm grant). Returns perm -> result. */
object Permissions {
    fun grantAll(ctx: Context): Map<String, String> {
        val pkg = ctx.packageName
        val results = mutableMapOf<String, String>()
        if (!ShizukuShell.granted()) {
            results["shizuku"] = "Shizuku not granted — start Shizuku and tap Allow"
            return results
        }
        ALL_PERMS.forEach { p ->
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                results[p] = ShizukuShell.exec("pm grant $pkg $p")
            } else {
                results[p] = "already granted"
            }
        }
        return results
    }
}
