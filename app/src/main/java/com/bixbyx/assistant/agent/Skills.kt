package com.bixbyx.assistant.agent

import java.io.File

/** Skills are short markdown instructions the assistant can load on demand. First line = description. */
object Skills {
    lateinit var dir: File

    fun init(workspace: File) {
        dir = File(workspace, "skills").apply { mkdirs() }
        if (dir.listFiles().isNullOrEmpty()) {
            save("research", "Look things up on the web and summarise.\nSearch the topic with web_search, open the two best results with web_fetch, then answer briefly and list the source URLs.")
            save("files", "Work with files in the workspace.\nUse list_dir, read_file, write_file and edit_file. Prefer edit_file for small changes. Never delete anything outside the workspace.")
            save("notes", "Keep notes for the user.\nUse memory_add for short facts to remember. Use write_file notes/<topic>.md for longer notes.")
        }
    }

    private fun clean(name: String) = name.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(40)

    fun names(): List<String> = dir.listFiles { f -> f.extension == "md" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
    fun read(name: String): String? = File(dir, clean(name) + ".md").takeIf { it.exists() }?.readText()
    fun description(name: String): String = read(name)?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(100).orEmpty()
    fun save(name: String, body: String) { val n = clean(name); if (n.isNotEmpty()) File(dir, "$n.md").writeText(body.trim() + "\n") }
    fun delete(name: String) { File(dir, clean(name) + ".md").delete() }
    fun summary(): String = names().joinToString("\n") { "- $it: ${description(it)}" }
}
