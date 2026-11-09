package com.bixbyx.assistant.agent

import android.app.Application
import android.content.Context
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Look
import com.bixbyx.assistant.ai.Prefs
import com.bixbyx.assistant.service.AgentAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.SocketTimeoutException

data class Msg(val text: String, val user: Boolean, val ts: Long = System.currentTimeMillis(), val controls: List<Ctl> = emptyList(), val steps: List<Step> = emptyList())
enum class BState { Idle, Listening, Thinking, Working }
enum class StepState { Running, Done, Failed }
enum class StepKind { Thought, Ran }
data class Step(val n: Int, val kind: StepKind, val text: String, val state: StepState, val input: String = "", val output: String = "")

/** Shared by the app, the assist popup, the floating overlay, the widget and the tile. */
object BixbyEngine {
    private lateinit var app: Application
    private lateinit var store: ChatStore
    lateinit var prefs: Prefs
    lateinit var voice: VoiceController
    private var job: Job? = null
    private var noticeJob: Job? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val currentId = MutableStateFlow("")
    val messages = MutableStateFlow<List<Msg>>(emptyList())

    val state = MutableStateFlow(BState.Idle)
    val status = MutableStateFlow("")
    val steps = MutableStateFlow<List<Step>>(emptyList())
    val partial = MutableStateFlow("")
    val notice = MutableStateFlow("")
    val level = MutableStateFlow(0f)
    val panelOpen = MutableStateFlow(false)
    val look = MutableStateFlow(Look(0, 0, 0, 0, true, 0, 0, true))

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext as Application
        prefs = Prefs(app); store = ChatStore(app); voice = VoiceController(app, prefs)
        Tools.init(app)
        look.value = prefs.look()
        val list = store.load().toMutableList()
        if (list.isEmpty()) {
            val old = prefs.loadHistory()
            list += Chat(newId(), old.firstOrNull { it.user }?.text?.take(32) ?: "New chat", System.currentTimeMillis(), old)
        }
        chats.value = list.sortedByDescending { it.updated }
        currentId.value = chats.value.first().id
        messages.value = chats.value.first().messages
    }

    fun updateLook(f: (Look) -> Look) { val l = f(look.value); look.value = l; prefs.saveLook(l) }

    // ---- chats ----
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

    // ---- helpers ----
    fun notify(text: String) {
        notice.value = text
        noticeJob?.cancel()
        noticeJob = scope.launch { delay(4500); notice.value = "" }
    }

    private fun friendly(e: Exception) = when (e) {
        is SocketTimeoutException -> "The model took too long to answer. Try a faster model."
        else -> e.message ?: "Something went wrong"
    }

    private fun ui() = object : AgentUi {
        override fun thinking(text: String) { status.value = text; state.value = BState.Thinking }
        override fun thought(text: String) {
            steps.value = steps.value + Step(steps.value.size + 1, StepKind.Thought, text, StepState.Done)
        }
        override fun begin(text: String, input: String): Int {
            val n = steps.value.size + 1
            steps.value = steps.value + Step(n, StepKind.Ran, text, StepState.Running, input)
            status.value = text; state.value = BState.Working
            return n
        }
        override fun end(n: Int, ok: Boolean, output: String) {
            steps.value = steps.value.map { if (it.n == n) it.copy(state = if (ok) StepState.Done else StepState.Failed, output = output) else it }
        }
    }

    // ---- actions ----
    fun send(text: String) {
        if (text.isBlank()) return
        add(Msg(text, true))
        steps.value = emptyList()
        job?.cancel()
        job = scope.launch {
            state.value = BState.Thinking
            try {
                val r = AgentLoop(app, prefs).run(text, messages.value, ui())
                add(Msg(r.text, false, controls = r.controls, steps = steps.value)); steps.value = emptyList()
                if (prefs.speak) voice.speak(r.text, interrupt = true)
            } catch (e: CancellationException) { add(Msg("Stopped.", false, steps = steps.value)); steps.value = emptyList(); throw e
            } catch (e: Exception) { add(Msg("Error: ${friendly(e)}", false, steps = steps.value)); steps.value = emptyList() }
            finally { state.value = BState.Idle; status.value = "" }
        }
    }

    /** Ask again with the last thing the user said. */
    fun regenerate() { messages.value.lastOrNull { it.user }?.let { send(it.text) } }

    fun applyControl(type: String, value: Int) {
        scope.launch { withContext(Dispatchers.IO) { DeviceActions.setControl(app, type, value) } }
    }

    fun describeScreen() {
        val svc = AgentAccessibilityService.instance
        if (svc == null) { notify("Turn on screen control in Settings"); return }
        val dump = svc.dump(); val pkg = svc.currentPackage()   // read now, before a popup covers the screen
        add(Msg("What's on my screen?", true)); steps.value = emptyList()
        job?.cancel()
        job = scope.launch {
            state.value = BState.Thinking; status.value = "Reading the screen"
            try {
                val t = LlmClient.chat(prefs, listOf(
                    "system" to "Describe a phone screen to the user, spoken aloud. In two or three short sentences say which app is open and what the main content and options are. No lists, no jargon. Use the language of the user's recent messages, otherwise English.",
                    "user" to "App package: $pkg\nScreen elements:\n${dump.take(5000)}")) { status.value = it }
                add(Msg(t, false)); voice.speak(t, interrupt = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { add(Msg("Error: ${friendly(e)}", false)) }
            finally { state.value = BState.Idle; status.value = "" }
        }
    }

    private fun resetListen() { partial.value = ""; level.value = 0f; state.value = BState.Idle }

    fun listen() {
        state.value = BState.Listening; partial.value = ""
        voice.listen(scope, { level.value = it }, { partial.value = it },
            { resetListen(); send(it) }, { resetListen(); notify(it) })
    }

    fun listenInto(onText: (String) -> Unit) {
        state.value = BState.Listening; partial.value = ""
        voice.listen(scope, { level.value = it }, { partial.value = it },
            { resetListen(); onText(it) }, { resetListen(); notify(it) })
    }

    fun testVoice() = voice.speak("Hi, I'm Bixby. This is how I sound.", interrupt = true)

    fun stop() { job?.cancel(); voice.stop(); resetListen(); status.value = "" }
    fun speak(t: String) = voice.speak(t, interrupt = true)

    suspend fun translate(text: String, target: String): String = LlmClient.chat(prefs, listOf(
        "system" to "You are an interpreter. Translate the user's text into $target. Output only the translation.",
        "user" to text), 0.1)

    suspend fun diagnose(h: Health): String = LlmClient.chat(prefs, listOf(
        "system" to "You check phone health. Given device stats, write three short bullet points: what is fine, what looks wrong, one practical fix.",
        "user" to h.asText()), 0.3)
}
