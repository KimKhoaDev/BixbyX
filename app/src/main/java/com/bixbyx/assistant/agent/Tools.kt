package com.bixbyx.assistant.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** File, command, web, clipboard and memory tools. Everything here blocks, so call it off the main thread. */
object Tools {
    lateinit var root: File
        private set
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun init(ctx: Context) {
        if (::root.isInitialized) return
        root = File(ctx.filesDir, "workspace").apply { mkdirs() }
        Skills.init(root)
    }

    // ------------------------------------------------------------------ files
    private val shared = File("/storage/emulated/0")

    /** Paths are limited to the workspace and shared storage. */
    private fun resolve(path: String): Pair<File?, String?> {
        val p = path.trim()
        if (p.isEmpty()) return null to "Missing path"
        val f = (if (p.startsWith("/")) File(p) else File(root, p)).canonicalFile
        val inRoot = f.path == root.canonicalPath || f.path.startsWith(root.canonicalPath + "/")
        val inShared = f.path == shared.path || f.path.startsWith(shared.path + "/")
        if (!inRoot && !inShared) return null to "Path not allowed. Use the workspace or /storage/emulated/0."
        if (inShared && !inRoot && android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return null to "Turn on All files access in Settings to use shared storage."
        return f to null
    }

    fun readFile(path: String): String {
        val (f, err) = resolve(path); if (f == null) return err!!
        if (!f.isFile) return "Not a file: $path"
        val t = f.readText()
        return if (t.length > 12000) t.take(12000) + "\n[truncated, ${t.length} characters in total]" else t.ifEmpty { "(empty file)" }
    }

    fun writeFile(path: String, text: String, append: Boolean): String {
        val (f, err) = resolve(path); if (f == null) return err!!
        f.parentFile?.mkdirs()
        if (append) f.appendText(text) else f.writeText(text)
        return "Wrote ${text.length} characters to ${f.path}"
    }

    fun editFile(path: String, old: String, new: String): String {
        val (f, err) = resolve(path); if (f == null) return err!!
        if (!f.isFile) return "Not a file: $path"
        if (old.isEmpty()) return "Missing 'old' text"
        val t = f.readText()
        val n = t.windowed(old.length).count { it == old }
        if (n == 0) return "Text to replace was not found"
        if (n > 1) return "Text to replace matches $n places. Add more context to make it unique."
        f.writeText(t.replace(old, new))
        return "Edited ${f.path}"
    }

    fun listDir(path: String): String {
        val (f, err) = resolve(path.ifBlank { "." }); if (f == null) return err!!
        val items = f.listFiles() ?: return "Not a folder: $path"
        return items.sortedWith(compareBy({ !it.isDirectory }, { it.name })).take(100)
            .joinToString("\n") { if (it.isDirectory) "${it.name}/" else "${it.name} (${it.length()} bytes)" }.ifBlank { "(empty folder)" }
    }

    fun searchFiles(path: String, query: String): String {
        val (f, err) = resolve(path.ifBlank { "." }); if (f == null) return err!!
        if (query.isBlank()) return "Missing query"
        val hits = ArrayList<String>()
        f.walkTopDown().maxDepth(4).filter { it.isFile && it.length() < 400_000 }.take(400).forEach { file ->
            if (hits.size >= 40) return@forEach
            runCatching {
                file.useLines { lines -> lines.forEachIndexed { i, l -> if (hits.size < 40 && l.contains(query, true)) hits += "${file.path}:${i + 1}: ${l.trim().take(160)}" } }
            }
        }
        return hits.joinToString("\n").ifBlank { "No matches" }
    }

    fun deleteFile(path: String): String {
        val (f, err) = resolve(path); if (f == null) return err!!
        if (!(f.path == root.canonicalPath || f.path.startsWith(root.canonicalPath + "/"))) return "Deleting is only allowed inside the workspace."
        if (f.path == root.canonicalPath) return "Cannot delete the workspace itself"
        return if (f.deleteRecursively()) "Deleted ${f.path}" else "Could not delete"
    }

    // ------------------------------------------------------------------ commands
    fun runCommand(cmd: String): String {
        Safety.blocked(cmd)?.let { return "Blocked for safety: '$it'" }
        if (ShizukuShell.granted()) return ShizukuShell.exec(cmd)
        return runCatching {
            val p = ProcessBuilder("sh", "-c", cmd).directory(root).redirectErrorStream(true).start()
            val sb = StringBuffer()
            val reader = thread { runCatching { p.inputStream.bufferedReader().forEachLine { if (sb.length < 4000) sb.append(it).append('\n') } } }
            if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroyForcibly(); sb.append("(timed out after 20 s)") }
            reader.join(500)
            sb.toString().trim().ifBlank { "OK" }
        }.getOrElse { "Command failed: ${it.message}" }
    }

    // ------------------------------------------------------------------ web
    private fun get(url: String): Pair<String, String> {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) BixbyX").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return (r.header("Content-Type") ?: "") to (r.body?.string() ?: "")
        }
    }

    private fun htmlToText(h: String) = h
        .replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim()

    fun webFetch(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "Only http and https links are supported"
        return runCatching {
            val (type, body) = get(url)
            val text = if (type.contains("html", true) || body.trimStart().startsWith("<")) htmlToText(body) else body
            if (text.length > 8000) text.take(8000) + " [truncated]" else text.ifBlank { "(no text)" }
        }.getOrElse { "Fetch failed: ${it.message}" }
    }

    fun webSearch(q: String): String {
        if (q.isBlank()) return "Missing query"
        return runCatching {
            val (_, html) = get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
            val links = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
            val snippets = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html).map { htmlToText(it.groupValues[1]) }.toList()
            links.take(6).mapIndexed { i, m ->
                var u = m.groupValues[1]
                Regex("uddg=([^&]+)").find(u)?.let { u = URLDecoder.decode(it.groupValues[1], "UTF-8") }
                if (u.startsWith("//")) u = "https:$u"
                "${htmlToText(m.groupValues[2])}\n$u\n${snippets.getOrNull(i).orEmpty()}"
            }.joinToString("\n\n").ifBlank { "No results" }
        }.getOrElse { "Search failed: ${it.message}" }
    }

    // ------------------------------------------------------------------ clipboard, share, settings
    fun clipboardGet(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.take(4000)
            ?: "Clipboard is empty (Android only lets the foreground app read it)"
    }

    fun clipboardSet(ctx: Context, text: String): String {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Bixby", text))
        return "OK"
    }

    fun share(ctx: Context, text: String): String = runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "OK" }.getOrElse { "Failed: ${it.message}" }

    fun openSettings(ctx: Context, page: String): String {
        val action = when (page.trim().lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "date", "time" -> Settings.ACTION_DATE_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "OK" }.getOrElse { "Failed: ${it.message}" }
    }

    // ------------------------------------------------------------------ memory
    private val memoryFile get() = File(root, "memory.md")
    fun memory(): String = runCatching { memoryFile.readText().takeLast(2000) }.getOrDefault("")
    fun memoryAdd(text: String): String { memoryFile.appendText("- ${text.trim().replace('\n', ' ')}\n"); return "Saved to memory" }
}
