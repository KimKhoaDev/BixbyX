package com.bixbyx.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OneGreen = Color(0xFF34C759)
val OneOrange = Color(0xFFFF9500)
val OneRed = Color(0xFFFF3B30)

/** One UI page: tall viewing area with a big title that collapses, calm background, rounded groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiPage(title: String, onBack: (() -> Unit)? = null, bottomBar: @Composable () -> Unit = {},
              state: LazyListState = rememberLazyListState(), actions: @Composable RowScope.() -> Unit = {},
              content: LazyListScope.() -> Unit) {
    val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bg = MaterialTheme.colorScheme.background
    Scaffold(Modifier.nestedScroll(sb.nestedScrollConnection), containerColor = bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
            LargeTopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions, expandedHeight = 200.dp, scrollBehavior = sb,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = bg, scrolledContainerColor = bg))
        }, bottomBar = bottomBar) { pad ->
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp,
            pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun OneCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), content = content)
    }
}

/** A rounded group of list rows (like the Settings app). Rows pad themselves. */
@Composable
fun OneGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
fun OneDivider() = HorizontalDivider(Modifier.padding(start = 70.dp, end = 18.dp), thickness = .5.dp,
    color = MaterialTheme.colorScheme.onSurface.copy(.12f))

@Composable
fun IconBadge(icon: ImageVector, color: Color) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color), Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun OneRow(title: String, sub: String? = null, icon: ImageVector? = null, tint: Color = OneBlue,
           onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { IconBadge(icon, tint); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(.55f))
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.35f))

@Composable
fun StatusText(ok: Boolean, on: String = "On", off: String = "Set up") =
    Text(if (ok) on else off, fontSize = 14.sp, color = if (ok) OneGreen else OneOrange, fontWeight = FontWeight.Medium)

@Composable
fun SectionLabel(t: String) = Text(t, Modifier.padding(start = 8.dp, top = 8.dp), color = MaterialTheme.colorScheme.primary,
    fontSize = 13.sp, fontWeight = FontWeight.Medium)

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

@Composable
fun SwitchRow(title: String, sub: String, icon: ImageVector, tint: Color, checked: Boolean, onChange: (Boolean) -> Unit) =
    OneRow(title, sub, icon, tint, { onChange(!checked) }) { OneSwitch(checked, onChange) }

data class Tab(val label: String, val icon: ImageVector)

/** Bottom tab bar: rounded top, selected tab gets a soft blue pill. */
@Composable
fun OneTabBar(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp), shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                val bg by animateColorAsState(if (sel) OneBlue.copy(.14f) else Color.Transparent, tween(220), label = "tb")
                val fg by animateColorAsState(if (sel) OneBlue else MaterialTheme.colorScheme.onSurface.copy(.6f), tween(220), label = "tf")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(bg).clickable { onSelect(i) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val pop by animateFloatAsState(if (sel) 1.18f else 1f, spring(dampingRatio = .4f, stiffness = 400f), label = "pop")
                    Icon(t.icon, t.label, tint = fg, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
                    Text(t.label, fontSize = 12.sp, color = fg, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Fades + slides a block in with a small delay per index, so lists cascade in. */
@Composable
fun Stagger(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 70L); shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(350)) + slideInVertically(spring(dampingRatio = .8f, stiffness = 300f)) { it / 3 }) { content() }
}
