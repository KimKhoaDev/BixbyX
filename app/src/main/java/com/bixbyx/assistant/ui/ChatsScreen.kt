package com.bixbyx.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Chat
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun relTime(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "Now"
        d < 3_600_000 -> "${d / 60_000} min"
        d < 86_400_000 -> "${d / 3_600_000} h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

@Composable
fun ChatsScreen(padding: PaddingValues, onOpen: () -> Unit) {
    val chats by BixbyEngine.chats.collectAsState()
    val cur by BixbyEngine.currentId.collectAsState()
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Chat?>(null) }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    val shown = chats.filter { query.isBlank() || it.title.contains(query, true) || it.messages.any { m -> m.text.contains(query, true) } }

    Page("Chats", padding, actions = { IconButton({ BixbyEngine.newChat(); onOpen() }) { Icon(MiuixIcons.Add, "New chat") } }) {
        item {
            TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                label = "Search chats", useLabelAsPlaceholder = true, singleLine = true)
        }
        if (shown.isEmpty()) item {
            Text("No chats", Modifier.fillMaxWidth().padding(24.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        itemsIndexed(shown, key = { _, c -> c.id }) { _, c ->
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), showIndication = true,
                onClick = { BixbyEngine.openChat(c.id); onOpen() }, onLongPress = { menu = c }) {
                BasicComponent(title = c.title, summary = c.messages.lastOrNull()?.text?.take(90) ?: "Empty chat", endActions = {
                    Text(if (c.id == cur) "Open" else relTime(c.updated), color = if (c.id == cur) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1)
                    IconButton({ menu = c }) { Icon(MiuixIcons.More, "More", tint = MiuixTheme.colorScheme.onSurfaceVariantActions) }
                })
            }
        }
    }

    OverlayDialog(show = menu != null, title = menu?.title, onDismissRequest = { menu = null }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Rename", { renaming = menu; menu = null }, Modifier.weight(1f))
            TextButton("Delete", { menu?.let { BixbyEngine.deleteChat(it.id) }; menu = null }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
    val r = renaming
    var name by remember(r?.id) { mutableStateOf(r?.title ?: "") }
    OverlayDialog(show = r != null, title = "Rename chat", onDismissRequest = { renaming = null }) {
        TextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Cancel", { renaming = null }, Modifier.weight(1f))
            TextButton("Save", { r?.let { BixbyEngine.renameChat(it.id, name) }; renaming = null }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}
