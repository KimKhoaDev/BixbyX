package com.bixbyx.assistant.agent

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands with adb-level privileges through Shizuku. */
object ShizukuShell {
    fun running() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun granted() = running() && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun request() { runCatching { if (running() && !Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(4242) } }

    fun exec(cmd: String): String {
        if (!granted()) return "Shizuku is not ready. Start it and grant access in Settings."
        val c = cmd.trim()
        Safety.blocked(c)?.let { return "Blocked for safety: '$it'" }
        return runCatching {
            // Shizuku.newProcess is private since API 13 but is still the usual way to run a one-off command
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", c), null, null) as Process
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + err).trim().take(3000).ifBlank { "OK" }
        }.getOrElse { "Shizuku error: ${it.message}" }
    }
}
