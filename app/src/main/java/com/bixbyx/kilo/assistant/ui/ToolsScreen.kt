package com.bixbyx.kilo.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.bixbyx.kilo.assistant.agent.BixbyEngine

enum class Tool { Diagnostics, Interpreter }

@Composable
fun ToolsScreen() {
    var sub by remember { mutableStateOf<Tool?>(null) }
    BackHandler(sub != null) { sub = null }
    AnimatedContent(sub, transitionSpec = {
        if (targetState != null) (slideInHorizontally(spring(stiffness = 400f)) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
        else (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
    }, label = "tools") { t ->
        when (t) {
            Tool.Diagnostics -> DiagnosticsScreen { sub = null }
            Tool.Interpreter -> InterpreterScreen { sub = null }
            null -> ToolsHome { sub = it }
        }
    }
}

@Composable
private fun ToolsHome(open: (Tool) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    OneUiPage("Tools") {
        item { Stagger(0) { MiuixGroup {
            OneRow("Diagnostics", "Battery, storage, memory â€” with an AI check", Icons.Default.MonitorHeart, OneGreen, { open(Tool.Diagnostics) }) { Chevron() }
            OneDivider()
            OneRow("Interpreter", "Translate speech and text", Icons.Default.Translate, OnePurple, { open(Tool.Interpreter) }) { Chevron() }
        } } }
        item { Stagger(1) { MiuixGroup {
            OneRow("Read my screen aloud", "Bixby tells you what's on screen", Icons.Default.RecordVoiceOver, OneBlue, { BixbyEngine.describeScreen() })
            OneDivider()
            OneRow("Test voice", "Hear how Bixby sounds", Icons.Default.VolumeUp, OneOrange, { BixbyEngine.testVoice() })
        } } }
        item { Stagger(2) { MiuixGroup {
            OneRow("Clear all chats", "Deletes every conversation", Icons.Default.Delete, OneRed, { confirm = true })
        } } }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear all chats?") },
        text = { Text("Every conversation will be deleted. This can't be undone.") },
        confirmButton = { TextButton({ BixbyEngine.clearAllChats(); confirm = false }) { Text("Delete all") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

