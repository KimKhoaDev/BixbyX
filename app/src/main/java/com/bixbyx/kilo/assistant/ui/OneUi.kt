package com.bixbyx.kilo.assistant.ui

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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OneGreen = Color(0xFF34C759)
val OneOrange = Color(0xFFFF9500)
val OneRed = Color(0xFFFF3B30)

val OneDarkSurface = Color(0xFF1C1C1E)
val OneDarkSurfaceVariant = Color(0xFF2C2C2E)
val OneLightSurface = Color(0xFFF6F6F6)
val OneLightSurfaceVariant = Color(0xFFEDEDED)

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

/** Miuix card: squircle container on surface variant. */
@Composable
fun MiuixCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
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

/** Miuix floating navigation bar: a floating rounded container with a soft shadow. */
@Composable
fun MiuixFloatingNavigationBar(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == selected
                    val fg by animateColorAsState(if (sel) OneBlue else MaterialTheme.colorScheme.onSurface.copy(.5f), tween(220), label = "tb$i")
                    Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 6.dp)) {
                        val pop by animateFloatAsState(if (sel) 1.15f else 1f, spring(dampingRatio = .5f, stiffness = 450f), label = "pop$i")
                        Icon(t.icon, t.label, tint = fg, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop }.size(26.dp))
                        Text(t.label, fontSize = 11.sp, color = fg, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
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

