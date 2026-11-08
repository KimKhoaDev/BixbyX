package com.bixbyx.kilo.assistant.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import java.io.File

/** Result of running a skill. */
data class SkillResult(val ok: Boolean, val text: String)

/** One parameter a skill accepts, used both for validation and for rendering the skill into the LLM prompt. */
sealed class SkillParam(val name: String, val type: String, val required: Boolean, val desc: String) {
    data class Str(val name: String, override val required: Boolean = true, override val desc: String = "") : SkillParam(name, "string", required, desc)
    data class Int(val name: String, override val required: Boolean = false, override val desc: String = "") : SkillParam(name, "integer", required, desc)
    data class Bool(val name: String, override val required: Boolean = false, override val desc: String = "") : SkillParam(name, "boolean", required, desc)
}

/** A skill the model can invoke. */
abstract class Skill(
    val name: String,
    val desc: String,
    val params: List<SkillParam>
) {
    abstract fun execute(ctx: Context, args: Map<String, Any?>): SkillResult

    fun promptLine(): String {
        val p = params.joinToString(", ") { it.name + ":" + it.type + (if (it.required) "" else "?") + " — " + it.desc }
        return "  $name{$p} — $desc"
    }
}

/** Built-in skills Bixby can call (files, shell, system info). No accessibility service needed. */
object Skills {
    private val all: List<Skill> = listOf(
        Shell(), ReadFile(), WriteFile(), EditFile(), ListFiles(), Storage(), Battery(), Apps(), Clipboard(), Notify()
    )

    val names: List<String> get() = all.map { it.name }
    fun byName(n: String): Skill? = all.firstOrNull { it.name == n }

    /** Render every skill as prompt lines the model can follow. */
    fun promptBlock(): String = all.joinToString("\n") { it.promptLine() }

    fun execute(ctx: Context, name: String, args: Map<String, Any?>): SkillResult =
        byName(name)?.let { it.execute(ctx, args) } ?: SkillResult(false, "Unknown skill '$name'")
}

/* ---------- individual skills ---------- */

private class Shell : Skill("shell", "Run a shell command via Shizuku. Never destructive (no rm/uninstall/reboot/su).", listOf(SkillParam.Str("text", true, "the command"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val text = args["text"]?.toString() ?: ""
        return try {
            val res = ShizukuShell.exec(text)
            SkillResult(!res.startsWith("Blocked") && !res.startsWith("Shizuku"), res)
        } catch (e: Exception) { SkillResult(false, e.message ?: "shell failed") }
    }
}

private class ReadFile : Skill("read", "Read a file from the app's private storage.", listOf(SkillParam.Str("path", true, "file path"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val path = args["path"]?.toString() ?: ""
        val f = File(ctx.filesDir, path)
        return if (!f.exists()) SkillResult(false, "Not found: $path")
        else SkillResult(true, f.readText().take(6000))
    }
}

private class WriteFile : Skill("write", "Write text to a file in the app's private storage.", listOf(SkillParam.Str("path", true, "file path"), SkillParam.Str("text", true, "content"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val path = args["path"]?.toString() ?: ""
        if (path.isBlank()) return SkillResult(false, "Write needs a path")
        val text = args["text"]?.toString() ?: ""
        File(ctx.filesDir, path).writeText(text)
        return SkillResult(true, "Wrote $path")
    }
}

private class EditFile : Skill("edit", "Replace oldText with newText in a file.", listOf(SkillParam.Str("path", true, "file path"), SkillParam.Str("oldText", true, "text to replace"), SkillParam.Str("newText", true, "replacement"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val path = args["path"]?.toString() ?: ""
        val f = File(ctx.filesDir, path)
        if (!f.exists()) return SkillResult(false, "Not found: $path")
        val old = args["oldText"]?.toString() ?: ""
        val neu = args["newText"]?.toString() ?: ""
        val cur = f.readText()
        f.writeText(cur.replace(old, neu))
        return SkillResult(true, "Edited $path")
    }
}

private class ListFiles : Skill("files", "List files in the app's private storage folder.", listOf()) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val files = ctx.filesDir.listFiles()?.map { it.name } ?: emptyList()
        return SkillResult(true, files.joinToString(", ").ifBlank { "(empty)" })
    }
}

private class Storage : Skill("storage", "Show free/total storage on the device.", listOf()) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockSizeLong * stat.blockCountLong
        val free = stat.blockSizeLong * stat.availableBlocksLong
        return SkillResult(true, "Storage: ${(free * 100 / total).toInt()}% free (${human(free)} / ${human(total)})")
    }

    private fun human(b: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = b.toDouble()
        var i = 0
        while (size >= 1024 && i < units.lastIndex) { size /= 1024; i++ }
        return "%.2f %s".format(size, units[i])
    }
}

private class Battery : Skill("battery", "Show battery level and charging state.", listOf()) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return SkillResult(false, "battery unavailable")
        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", 100)
        val pct = if (scale > 0) level * 100 / scale else -1
        val plugged = intent.getIntExtra("status", -1)
        val charging = plugged == android.os.BatteryManager.BATTERY_STATUS_CHARGING || plugged == android.os.BatteryManager.BATTERY_STATUS_FULL
        return SkillResult(true, "Battery: $pct%${if (charging) " · charging" else ""}")
    }
}

private class Apps : Skill("apps", "List apps whose label contains a keyword (or all if empty).", listOf(SkillParam.Str("q", false, "search keyword"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val q = (args["q"]?.toString() ?: "").lowercase()
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hits = pm.queryIntentActivities(main, 0)
            .mapNotNull { it.loadLabel(pm).toString() }
            .filter { q.isEmpty() || it.lowercase().contains(q) }
            .take(40)
        return SkillResult(true, hits.joinToString(", ").ifBlank { "(no apps)" })
    }
}

private class Clipboard : Skill("clipboard", "Copy text to the clipboard.", listOf(SkillParam.Str("text", true, "text to copy"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val text = args["text"]?.toString() ?: ""
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("bixbyx", text))
            return SkillResult(true, "Copied to clipboard")
        } catch (e: Exception) {
            return SkillResult(false, e.message ?: "clipboard failed")
        }
    }
}

private class Notify : Skill("notify", "Show a short toast notification to the user.", listOf(SkillParam.Str("text", true, "message"))) {
    override fun execute(ctx: Context, args: Map<String, Any?>): SkillResult {
        val text = args["text"]?.toString() ?: ""
        try {
            android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
            return SkillResult(true, "Shown: $text")
        } catch (e: Exception) {
            return SkillResult(false, e.message ?: "notify failed")
        }
    }
}
