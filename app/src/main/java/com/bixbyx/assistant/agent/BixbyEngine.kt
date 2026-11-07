package com.bixbyx.assistant.agent

import android.app.Application
import android.content.Context
import com.bixbyx.assistant.ai.LlmClient
import com.bixbyx.assistant.ai.Prefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class Msg(val text: String, val user: Boolean, val ts: Long = System.currentTimeMillis())
enum class BState { Idle, Listening, Thinking, Working }

/** Single shared brain used by the app, the assist popup, the floating orb and the widget. */
object BixbyEngine {
    private lateinit var app: Application
    lateinit var prefs: Prefs
    private var voice: VoiceController? = null
    private var job: Job? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val messages = MutableStateFlow<List<Msg>>(emptyList())
    val state = MutableStateFlow(BState.Idle)
    val status = MutableStateFlow("")
    val partial = MutableStateFlow("")
    val panelOpen = MutableStateFlow(false)

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext as Application
        prefs = Prefs(app)
        messages.value = prefs.loadHistory()
        voice = VoiceController(app)
    }

    private fun add(m: Msg) { messages.value = messages.value + m; prefs.saveHistory(messages.value) }

    fun send(text: String) {
        if (text.isBlank()) return
        add(Msg(text, true))
        job?.cancel()
        job = scope.launch {
            state.value = BState.Thinking
            try {
                val reply = AgentLoop(app, prefs).run(text, messages.value) {
                    status.value = it; state.value = BState.Working
                }
                add(Msg(reply, false)); if (prefs.speak) voice?.speak(reply)
            } catch (e: CancellationException) { add(Msg("Stopped.", false)); throw e
            } catch (e: Exception) { add(Msg("⚠ ${e.message}", false)) }
            finally { state.value = BState.Idle; status.value = "" }
        }
    }

    fun listen() {
        state.value = BState.Listening; partial.value = ""
        voice?.listen({ partial.value = it },
            { partial.value = ""; state.value = BState.Idle; send(it) },
            { partial.value = ""; state.value = BState.Idle; status.value = it })
    }

    /** Voice -> text only (used by the Interpreter), does not run the agent. */
    fun listenInto(onText: (String) -> Unit) {
        state.value = BState.Listening
        voice?.listen({ partial.value = it }, { partial.value = ""; state.value = BState.Idle; onText(it) },
            { partial.value = ""; state.value = BState.Idle })
    }

    fun stop() { job?.cancel(); voice?.stop(); state.value = BState.Idle }
    fun speak(t: String) = voice?.speak(t)
    fun clear() { messages.value = emptyList(); prefs.saveHistory(emptyList()) }

    // ---- One UI 9 Interpreter ----
    suspend fun translate(text: String, target: String): String = LlmClient.chat(prefs, listOf(
        "system" to "You are a live interpreter. Translate the user's text into $target. Output ONLY the translation.",
        "user" to text), 0.1)

    // ---- One UI 9 Diagnostics ----
    suspend fun diagnose(h: Health): String = LlmClient.chat(prefs, listOf(
        "system" to "You are Bixby Diagnostics. Given device stats, say in 3 short bullet points what is fine, what looks wrong and one practical fix. Be concrete, no fluff.",
        "user" to h.asText()), 0.3)
}
