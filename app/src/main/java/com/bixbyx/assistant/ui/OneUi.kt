package com.bixbyx.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/** One UI page: big collapsing title, rounded 26dp cards, calm background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiPage(title: String, onBack: (() -> Unit)? = null, bottomBar: @Composable () -> Unit = {},
              content: LazyListScope.() -> Unit) {
    val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bg = MaterialTheme.colorScheme.background
    Scaffold(Modifier.nestedScroll(sb.nestedScrollConnection), containerColor = bg, topBar = {
        LargeTopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            scrollBehavior = sb,
            colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = bg, scrolledContainerColor = bg))
    }, bottomBar = bottomBar) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp,
            pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun OneCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), content = content)
    }
}

@Composable
fun SectionLabel(t: String) = Text(t, Modifier.padding(start = 8.dp, top = 8.dp), color = MaterialTheme.colorScheme.primary,
    fontSize = 13.sp, fontWeight = FontWeight.Medium)

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
        Switch(checked, onChange)
    }
}

@Composable
fun StatusRow(title: String, sub: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (ok) Color(0xFF34C759) else Color(0xFFFF9500), CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f)) }
        if (!ok) TextButton(onClick) { Text(action) }
    }
}
