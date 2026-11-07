package com.bixbyx.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
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
        OneSwitch(checked, onChange)
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

/** One UI switch: slim pill track, white thumb that springs across. */
@Composable
fun OneSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) OneBlue else Color(0xFFB4B4BA), tween(220), label = "track")
    val x by animateDpAsState(if (checked) 23.dp else 3.dp, spring(dampingRatio = .7f, stiffness = 500f), label = "thumb")
    Box(Modifier.size(52.dp, 30.dp).clip(CircleShape).background(track)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }) {
        Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(24.dp).clip(CircleShape).background(Color.White))
    }
}
