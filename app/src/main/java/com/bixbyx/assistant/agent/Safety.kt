package com.bixbyx.assistant.agent

/** Commands the model may never run, locally or through Shizuku. */
object Safety {
    private val blocked = listOf("rm ", "rmdir", "pm uninstall", "pm clear", "pm disable", "wipe", "format", "mkfs", "dd ",
        "reboot", "recovery", "bootloader", "factory", "su ", "setprop", "settings put secure", "settings put global adb",
        "chmod", "chown", "mount", "> /", "| sh", "curl", "wget")

    fun blocked(cmd: String): String? = blocked.firstOrNull { cmd.contains(it, ignoreCase = true) }
}
