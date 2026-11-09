package com.bixbyx.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BixbyEngine
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(padding: PaddingValues) {
    val msgs by BixbyEngine.messages.collectAsState()
    val state by BixbyEngine.state.collectAsState()
    val steps by BixbyEngine.steps.collectAsState()
    val chats by BixbyEngine.chats.collectAsState()
    val cur by BixbyEngine.currentId.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, steps.size) {
        val n = list.layoutInfo.totalItemsCount
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    val title = chats.firstOrNull { it.id == cur }?.title?.takeIf { it != "New chat" } ?: "Bixby"
    Page(title, padding, state = list, reserveBottom = false,
        actions = { IconButton({ BixbyEngine.newChat() }) { Icon(MiuixIcons.Add, "New chat") } },
        bottomBar = { Box(Modifier.imePadding().padding(bottom = padding.calculateBottomPadding())) { InputBar() } }) {
        if (msgs.isEmpty() && steps.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BixbyOrb(state, 120.dp)
                Spacer(Modifier.height(14.dp))
                Text("Ask Bixby", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body1)
            }
        }
        if (msgs.isEmpty() && steps.isEmpty()) item { QuickChips() }
        if (steps.isNotEmpty()) item { ToolSteps(steps, running = state != com.bixbyx.assistant.agent.BState.Idle, defaultOpen = true) }
        messageItems(msgs.takeLast(60))
    }
}
