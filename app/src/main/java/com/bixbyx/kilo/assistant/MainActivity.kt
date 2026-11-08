package com.bixbyx.kilo.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.bixbyx.kilo.assistant.agent.BixbyEngine
import com.bixbyx.kilo.assistant.service.OverlayService
import com.bixbyx.kilo.assistant.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BixbyEngine.init(this)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        if (BixbyEngine.prefs.orbEnabled) OverlayService.start(this)
        setContent {
            OneUITheme {
                val tabs = remember { listOf(Tab("Bixby", Icons.Default.AutoAwesome), Tab("Chats", Icons.Default.ChatBubble),
                    Tab("Tools", Icons.Default.Apps), Tab("Settings", Icons.Default.Settings), Tab("About", Icons.Default.Info)) }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                BackHandler(tab != 0) { tab = 0 }
                val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
                    Box(Modifier.weight(1f)) {
                        AnimatedContent(tab, transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { dir * it / 8 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(300)) { -dir * it / 8 })
                        }, label = "tabs") { t ->
                            when (t) {
                                0 -> HomeScreen()
                                1 -> ChatsScreen { tab = 0 }
                                2 -> ToolsScreen()
                                3 -> SettingsScreen()
                                else -> AboutScreen()
                            }
                        }
                    }
                    AnimatedVisibility(!imeUp, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        MiuixFloatingNavigationBar(tabs, tab) { tab = it }
                    }
                }
            }
        }
    }
}

