package com.bixbyx.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService

/** Lets the assistant read what is in the notification shade. Needs notification access. */
class NotifyListener : NotificationListenerService() {
    companion object { var instance: NotifyListener? = null }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    private fun label(pkg: String) = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    /** The latest notifications that are not ongoing (music players, downloads, etc.). */
    fun snapshot(max: Int = 25): String = runCatching {
        activeNotifications.filter { !it.isOngoing }.sortedByDescending { it.postTime }.take(max).joinToString("\n") { sbn ->
            val ex = sbn.notification.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            "[${label(sbn.packageName)}] $title: $text".take(200)
        }
    }.getOrDefault("")
}
