package com.bixbyx.kilo.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.kilo.assistant.agent.BixbyEngine
import com.bixbyx.kilo.assistant.agent.Chat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun relTime(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "Just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

/** Chats manager: search, open, rename, swipe-to-delete, new chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onOpen: () -> Unit) {
    val chats by BixbyEngine.chats.collectAsState()
    val cur by BixbyEngine.currentId.collectAsState()
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    val shown = chats.filter { query.isBlank() || it.title.contains(query, true) || it.messages.any { m -> m.text.contains(query, true) } }

    OneUiPage("Chats", actions = {
        IconButton({ BixbyEngine.newChat(); onOpen() }) { Icon(Icons.Default.Add, "New chat") }
    }) {
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
                placeholder = { Text("Search chats") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        if (shown.isEmpty()) item {
            Text("No chats found", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurface.copy(.5f))
        }
        items(shown, key = { it.id }) { c ->
            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                if (it == SwipeToDismissBoxValue.EndToStart) { BixbyEngine.deleteChat(c.id); true } else false
            })
            SwipeToDismissBox(dismiss, backgroundContent = {
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(OneRed).padding(end = 26.dp), Alignment.CenterEnd) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                }
            }, modifier = Modifier.animateItem(), enableDismissFromStartToEnd = false) {
                ChatCard(c, c.id == cur, { BixbyEngine.openChat(c.id); onOpen() }, { renaming = c }, { BixbyEngine.deleteChat(c.id) })
            }
        }
    }

    renaming?.let { c ->
        var t by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename chat") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ BixbyEngine.renameChat(c.id, t); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ChatCard(c: Chat, selected: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.5.dp, OneBlue) else null, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.ChatBubble, if (selected) OneBlue else OnePurple)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.messages.lastOrNull()?.text ?: "Empty chat", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(.55f))
            }
            Text(relTime(c.updated), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.45f))
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Rename") }, { menu = false; onRename() })
                    DropdownMenuItem({ Text("Delete") }, { menu = false; onDelete() })
                }
            }
        }
    }
}

