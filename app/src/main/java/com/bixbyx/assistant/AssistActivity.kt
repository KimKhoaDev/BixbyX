package com.bixbyx.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.service.AgentAccessibilityService
import com.bixbyx.assistant.ui.BixbySheet
import com.bixbyx.assistant.ui.BixbyTheme

/** The popup that appears over the current app on assistant gesture, widget tap, tile or volume shortcut. */
class AssistActivity : ComponentActivity() {
    /** Text picked in another app: the "Ask Bixby" entry in the text menu, or text shared to Bixby. */
    private fun sharedText(i: Intent): String? =
        (i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: if (i.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT) else null)
            ?.takeIf { it.isNotBlank() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText(intent)?.let { BixbyEngine.pendingText.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BixbyEngine.init(this)
        val shared = sharedText(intent)
        if (shared != null) BixbyEngine.pendingText.value = shared
        val listen = intent.getBooleanExtra("listen", false) && shared == null
        // grab what's on screen right now (before this popup takes focus) and say it
        val describing = listen && BixbyEngine.prefs.autoDescribe && AgentAccessibilityService.running()
        if (describing) BixbyEngine.describeScreen()
        setContent {
            BixbyTheme {
                val state by BixbyEngine.state.collectAsState()
                LaunchedEffect(Unit) { if (listen && !describing) BixbyEngine.listen() }
                LaunchedEffect(state) { if (state == BState.Working) finish() }
                Box(Modifier.fillMaxSize().imePadding()) { BixbySheet(true) { finish() } }
            }
        }
    }
}
