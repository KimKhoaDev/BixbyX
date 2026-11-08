package com.bixbyx.kilo.assistant

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
import com.bixbyx.kilo.assistant.agent.BState
import com.bixbyx.kilo.assistant.agent.BixbyEngine
import com.bixbyx.kilo.assistant.service.AgentAccessibilityService
import com.bixbyx.kilo.assistant.ui.BixbySheet
import com.bixbyx.kilo.assistant.ui.OneUITheme

/** The popup that appears over the current app on assistant gesture, widget tap, tile or volume shortcut. */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BixbyEngine.init(this)
        BixbyEngine.grantPermissions()
        val listen = intent.getBooleanExtra("listen", false)
        // grab what's on screen right now (before this popup takes focus) and say it
        val describing = listen && BixbyEngine.prefs.autoDescribe && AgentAccessibilityService.running()
        if (describing) BixbyEngine.describeScreen()
        setContent {
            OneUITheme {
                val state by BixbyEngine.state.collectAsState()
                LaunchedEffect(Unit) { if (listen && !describing) BixbyEngine.listen() }
                LaunchedEffect(state) { if (state == BState.Working) finish() }
                Box(Modifier.fillMaxSize().imePadding()) { BixbySheet(true) { finish() } }
            }
        }
    }
}

