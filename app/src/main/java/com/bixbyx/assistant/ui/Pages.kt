package com.bixbyx.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop as glassLayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberGlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The page of whichever design is active. */
@Composable
fun Page(
    title: String,
    padding: PaddingValues,
    onBack: (() -> Unit)? = null,
    subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    reserveBottom: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    when (LocalDesign.current) {
        Design.Miuix -> MiuixPage(title, padding, onBack, subtitle, actions, bottomBar, state, reserveBottom, content)
        Design.Glass -> GlassPage(title, padding, onBack, subtitle, actions, bottomBar, state, reserveBottom, content)
        Design.One -> OnePage(title, padding, onBack, subtitle, actions, bottomBar, state, reserveBottom, content)
    }
}

/** Liquid glass: colour wallpaper behind everything and a floating glass title pill. */
@Composable
private fun GlassPage(
    title: String, padding: PaddingValues, onBack: (() -> Unit)?, subtitle: String, actions: @Composable RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit, state: LazyListState, reserveBottom: Boolean, content: LazyListScope.() -> Unit,
) {
    val c = MiuixTheme.colorScheme
    val glass = rememberGlassBackdrop { drawRect(c.background); drawContent() }
    val wall = rememberGlassBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    CompositionLocalProvider(LocalGlass provides glass, LocalWall provides wall) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassLayer(glass)) {
                Box(Modifier.fillMaxSize().glassLayer(wall)) { GlassWallpaper() }
                LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = top + 84.dp, bottom = padding.calculateBottomPadding() + (if (!reserveBottom) 90.dp else 0.dp) + 12.dp), content = content)
            }
            Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(52.dp)
                .drawBackdrop(glass, { CircleShape }, effects = { vibrancy(); blur(8.dp.toPx()); lens(14.dp.toPx(), 28.dp.toPx()) },
                    onDrawSurface = { drawRect(c.surfaceContainer.copy(alpha = .5f)) }).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onBack) { Icon(MiuixIcons.Back, "Back") } else Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = c.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                }
                Row(content = actions)
            }
            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        }
    }
}

/** One UI: a tall "viewing area" with a big title that collapses into a small centred one. */
@Composable
private fun OnePage(
    title: String, padding: PaddingValues, onBack: (() -> Unit)?, subtitle: String, actions: @Composable RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit, state: LazyListState, reserveBottom: Boolean, content: LazyListScope.() -> Unit,
) {
    val c = MiuixTheme.colorScheme
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsePx = with(LocalDensity.current) { 90.dp.toPx() }
    val collapse = if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / collapsePx).coerceIn(0f, 1f)
    CompositionLocalProvider(LocalGlass provides null, LocalWall provides null) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = top + 56.dp, bottom = padding.calculateBottomPadding() + (if (!reserveBottom) 90.dp else 0.dp) + 12.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 28.dp, vertical = 16.dp).graphicsLayer { alpha = 1f - collapse },
                        contentAlignment = Alignment.BottomStart) {
                        Column {
                            Text(title, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 14.sp, color = c.onSurfaceVariantSummary)
                        }
                    }
                }
                content()
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(c.background.copy(alpha = collapse)).statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onBack) { Icon(MiuixIcons.Back, "Back") } else Spacer(Modifier.width(48.dp))
                Box(Modifier.weight(1f), Alignment.Center) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.graphicsLayer { alpha = collapse })
                }
                Row(content = actions)
            }
            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        }
    }
}
