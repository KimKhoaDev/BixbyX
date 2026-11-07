package com.bixbyx.assistant.agent

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands with adb-level privileges through Shizuku. */
object ShizukuShell {
    fun running() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun granted() = running() && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun request() { runCatching { if (running() && !Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(4242) } }

    // Safety net: the model must never run destructive commands.
    private val blocked = listOf("rm ", "rmdir", "pm uninstall", "pm clear", "pm disable", "wipe", "format", "mkfs", "dd ",
        "reboot", "recovery", "bootloader", "factory", "su ", "setprop", "settings put secure", "settings put global adb",
        "chmod", "chown", "mount", "> /", "| sh", "curl", "wget")

    fun exec(cmd: String): String {
        if (!granted()) return "Shizuku not ready (start Shizuku and grant BixbyX permission)"
        val c = cmd.trim()
        blocked.firstOrNull { c.contains(it, ignoreCase = true) }?.let { return "Blocked for safety: '$it'" }
        return runCatching {
            // Shizuku.newProcess is private in API 13+, still the common way to run one-off commands.
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", c), null, null) as Process
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + err).trim().take(1500).ifBlank { "OK" }
        }.getOrElse { "Shizuku error: ${it.message}" }
    }
}
