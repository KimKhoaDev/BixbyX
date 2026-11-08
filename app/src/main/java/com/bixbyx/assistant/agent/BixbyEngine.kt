package com.bixbyx.assistant.agent

import android.app.Application
import android.content.Context
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Prefs
import com.bixbyx.assistant.service.AgentAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.SocketTimeoutException

data class Msg(val text: String, val user: Boolean, val ts: Long = System.currentTimeMillis())
enum class BState { Idle, Listening, Thinking, Working }
enum class StepState { Running, Done, Failed }
data class Step(val n: Int, val text: String, val state: StepState)

/** Single shared brain used by the app, the assist popup, the floating overlay, the widget and the tile. */
object BixbyEngine {
    private lateinit var app: Application
    private lateinit var store: ChatStore
    lateinit var prefs: Prefs
    lateinit var voice: VoiceController
    private var job: Job? = null
    private var noticeJob: Job? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // chats manager
    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val currentId = MutableStateFlow("")
    val messages = MutableStateFlow<List<Msg>>(emptyList())

    // live UI state
    val state = MutableStateFlow(BState.Idle)
    val status = MutableStateFlow("")        // current step / what Bixby is doing
    val steps = MutableStateFlow<List<Step>>(emptyList())
    val partial = MutableStateFlow("")
    val notice = MutableStateFlow("")        // short hint / error under the bar (auto clears)
    val level = MutableStateFlow(0f)         // live mic level 0..1
    val panelOpen = MutableStateFlow(false)

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext as Application
        prefs = Prefs(app); store = ChatStore(app); voice = VoiceController(app, prefs)
        val list = store.load().toMutableList()
        if (list.isEmpty()) {
            val old = prefs.loadHistory()
            list += Chat(newId(), old.firstOrNull { it.user }?.text?.take(32) ?: "New chat", System.currentTimeMillis(), old)
        }
        chats.value = list.sortedByDescending { it.updated }
        currentId.value = chats.value.first().id
        messages.value = chats.value.first().messages
    }

    // ------------------------------------------------------------ chats manager
    private fun newId() = java.util.UUID.randomUUID().toString().take(8)
    private fun persist() = store.save(chats.value)

    private fun updateCurrent(f: (Chat) -> Chat) {
        chats.value = chats.value.map { if (it.id == currentId.value) f(it) else it }.sortedByDescending { it.updated }
        messages.value = chats.value.firstOrNull { it.id == currentId.value }?.messages ?: emptyList()
        persist()
    }

    private fun add(m: Msg) = updateCurrent { c ->
        c.copy(messages = c.messages + m, updated = System.currentTimeMillis(),
            title = if (c.title == "New chat" && m.user) m.text.take(32) else c.title)
    }

    fun openChat(id: String) {
        val c = chats.value.firstOrNull { it.id == id } ?: return
        stop(); currentId.value = id; messages.value = c.messages; steps.value = emptyList()
    }

    fun newChat() {
        val cur = chats.value.firstOrNull { it.id == currentId.value }
        if (cur != null && cur.messages.isEmpty()) return
        val c = Chat(newId(), "New chat", System.currentTimeMillis(), emptyList())
        chats.value = listOf(c) + chats.value; persist(); openChat(c.id)
    }

    fun deleteChat(id: String) {
        val rest = chats.value.filter { it.id != id }
        if (rest.isEmpty()) { clearAllChats(); return }
        chats.value = rest; persist()
        if (currentId.value == id) openChat(rest.first().id)
    }

    fun renameChat(id: String, title: String) {
        chats.value = chats.value.map { if (it.id == id) it.copy(title = title.trim().ifBlank { it.title }) else it }; persist()
    }

    fun clearAllChats() {
        val c = Chat(newId(), "New chat", System.currentTimeMillis(), emptyList())
        chats.value = listOf(c); persist(); openChat(c.id)
    }

    fun clear() = updateCurrent { it.copy(messages = emptyList(), title = "New chat") }

    // ------------------------------------------------------------ small helpers
    fun notify(text: String) {
        notice.value = text
        noticeJob?.cancel()
        noticeJob = scope.launch { delay(4500); notice.value = "" }
    }

    private fun friendly(e: Exception) = when (e) {
        is SocketTimeoutException -> "The AI took too long to answer. Try a faster model, or raise the request timeout in Settings."
        else -> e.message ?: "Something went wrong"
    }

    private fun ui() = object : AgentUi {
        override fun thinking(text: String) { status.value = text; state.value = BState.Thinking }
        override fun begin(text: String): Int {
            val n = steps.value.size + 1
            steps.value = steps.value + Step(n, text, StepState.Running)
            status.value = "Step $n · $text"; state.value = BState.Working
            if (prefs.narrate) voice.speak(text, interrupt = true)
            return n
        }
        override fun end(n: Int, ok: Boolean) {
            steps.value = steps.value.map { if (it.n == n) it.copy(state = if (ok) StepState.Done else StepState.Failed) else it }
        }
    }

    // ------------------------------------------------------------ doing things
    fun send(text: String) {
        if (text.isBlank()) return
        add(Msg(text, true))
        steps.value = emptyList()
        job?.cancel()
        job = scope.launch {
            state.value = BState.Thinking
            try {
                val reply = AgentLoop(app, prefs).run(text, messages.value, ui())
                add(Msg(reply, false)); if (prefs.speak) voice.speak(reply, interrupt = true)
            } catch (e: CancellationException) { add(Msg("Stopped.", false)); throw e
            } catch (e: Exception) { add(Msg("⚠ ${friendly(e)}", false)) }
            finally { state.value = BState.Idle; status.value = "" }
        }
    }

    /** Reads the screen out loud: "You're in Chrome on a page about …". */
    fun describeScreen() {
        val svc = AgentAccessibilityService.instance
        if (svc == null) { notify("Turn on Screen control in Settings so I can read the screen"); return }
        val dump = svc.dump(); val pkg = svc.currentPackage()   // taken now, before any popup changes the window
        add(Msg("What's on my screen?", true)); steps.value = emptyList()
        job?.cancel()
        job = scope.launch {
            state.value = BState.Thinking; status.value = "Reading the screen…"
            try {
                val t = LlmClient.chat(prefs, listOf(
                    "system" to "You describe a phone screen to the user, spoken aloud. In 2 or 3 short friendly sentences say which app is open and what the main content and options are. No lists, no technical jargon, same language as the user's recent messages if any, otherwise English.",
                    "user" to "App package: $pkg\nScreen elements:\n${dump.take(5000)}")) { status.value = it }
                add(Msg(t, false)); voice.speak(t, interrupt = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { add(Msg("⚠ ${friendly(e)}", false)) }
            finally { state.value = BState.Idle; status.value = "" }
        }
    }

    private fun resetListen() { partial.value = ""; level.value = 0f; state.value = BState.Idle }

    fun listen() {
        state.value = BState.Listening; partial.value = ""
        voice.listen(scope, { level.value = it }, { partial.value = it },
            { resetListen(); send(it) }, { resetListen(); notify(it) })
    }

    /** Voice -> text only (Interpreter). */
    fun listenInto(onText: (String) -> Unit) {
        state.value = BState.Listening; partial.value = ""
        voice.listen(scope, { level.value = it }, { partial.value = it },
            { resetListen(); onText(it) }, { resetListen(); notify(it) })
    }

    fun testVoice() = voice.speak("Hi, I'm Bixby. This is how I sound.", interrupt = true)

    fun stop() { job?.cancel(); voice.stop(); resetListen(); status.value = "" }
    fun speak(t: String) = voice.speak(t, interrupt = true)

    // ---- One UI 9 Interpreter ----
    suspend fun translate(text: String, target: String): String = LlmClient.chat(prefs, listOf(
        "system" to "You are a live interpreter. Translate the user's text into $target. Output ONLY the translation.",
        "user" to text), 0.1)

    // ---- One UI 9 Diagnostics ----
    suspend fun diagnose(h: Health): String = LlmClient.chat(prefs, listOf(
        "system" to "You are Bixby Diagnostics. Given device stats, say in 3 short bullet points what is fine, what looks wrong and one practical fix. Be concrete, no fluff.",
        "user" to h.asText()), 0.3)
}
