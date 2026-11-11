package com.bixbyx.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.bixbyx.assistant.agent.BixbyEngine
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop as glassLayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberGlassBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop as blurLayer
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberBlurBackdrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Backdrop for the glass surfaces (tab bar, input bar, toggles). Null in overlay windows, which cannot refract other apps. */
val LocalGlass = staticCompositionLocalOf<Backdrop?> { null }

/** A Miuix page: collapsing top bar with progressive blur, overscroll, and room for the tab bar. */
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
    val sb = MiuixScrollBehavior()
    val look by BixbyEngine.look.collectAsState()
    val c = MiuixTheme.colorScheme
    val blur = if (look.blur && isRuntimeShaderSupported()) rememberBlurBackdrop { drawRect(c.surface); drawContent() } else null
    val glass = rememberGlassBackdrop { drawRect(c.background); drawContent() }
    val wall = if (look.design == 1) rememberGlassBackdrop() else null
    val nav: @Composable () -> Unit = { if (onBack != null) IconButton(onClick = onBack) { Icon(MiuixIcons.Back, "Back") } }
    CompositionLocalProvider(LocalGlass provides glass, LocalWall provides wall) {
        Scaffold(
            topBar = {
                BlurredBar(blur, look.progressive, sb) {
                    TopAppBar(title = title, subtitle = subtitle, color = if (blur != null) Color.Transparent else c.surface,
                        scrollBehavior = sb, actions = actions, navigationIcon = nav)
                }
            },
            bottomBar = bottomBar,
        ) { inner ->
            Box(Modifier.fillMaxSize().then(if (blur != null) Modifier.blurLayer(blur) else Modifier).glassLayer(glass)) {
                if (wall != null) Box(Modifier.fillMaxSize().glassLayer(wall)) { GlassWallpaper() }
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxHeight()
                        .then(if (look.scrollHaptic) Modifier.scrollEndHaptic() else Modifier)
                        .overScrollVertical()
                        .nestedScroll(sb.nestedScrollConnection),
                    contentPadding = PaddingValues(top = inner.calculateTopPadding(),
                        bottom = inner.calculateBottomPadding() + (if (reserveBottom) padding.calculateBottomPadding() else 0.dp) + 12.dp),
                    content = content,
                )
            }
        }
    }
}

/** Rounded group of rows, as in the Miuix demo. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Panel(modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), content = content)

/** Fades and slides a block in, delayed per index, so lists cascade in. */
@Composable
fun Stagger(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 60L); shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(300)) + slideInVertically(spring(dampingRatio = .85f, stiffness = 300f)) { it / 4 }) { content() }
}

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(MiuixTheme.colorScheme.surfaceContainer)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface)
}

@Composable
fun StatusText(ok: Boolean, on: String = "On", off: String = "Off") =
    Text(if (ok) on else off, color = if (ok) Green else MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)

@Composable
private fun barColors(progressive: Boolean): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = if (progressive) 0.3f else 0.8f))))

/** Blurs whatever scrolls under the top bar. Progressive: strongest at the top edge, fading out below it. */
@Composable
private fun BlurredBar(backdrop: LayerBackdrop?, progressive: Boolean, scrollBehavior: ScrollBehavior?, content: @Composable () -> Unit) {
    Box(if (backdrop != null && !progressive) Modifier.textureBlur(backdrop, RectangleShape, 25f, colors = barColors(false)) else Modifier) {
        if (backdrop != null && progressive) {
            Box(Modifier.matchParentSize()
                .graphicsLayer { alpha = scrollBehavior?.state?.let { (-it.contentOffset / 48.dp.toPx()).coerceIn(0f, 1f) } ?: 1f }
                .progressiveTextureBlur(backdrop, RectangleShape, 10f, ProgressiveBlur.Top.copy(curve = 2.2f), colors = barColors(true)))
        }
        content()
    }
}
