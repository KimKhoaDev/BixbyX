package com.bixbyx.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.icon.extended.Add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.agent.Permissions
import com.bixbyx.assistant.glass.LiquidBottomTab
import com.bixbyx.assistant.glass.LiquidBottomTabs
import com.bixbyx.assistant.service.OverlayService
import com.bixbyx.assistant.ui.*
import com.kyant.backdrop.backdrops.layerBackdrop as glassLayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberGlassBackdrop
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop as blurLayer
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class TabItem(val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    private val shizukuGranted = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) Permissions.autoGrant(this)
    }
    private val shizukuBinder = Shizuku.OnBinderReceivedListener { Permissions.autoGrant(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BixbyEngine.init(this)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Permissions.autoGrant(this) }
            .launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuGranted); Shizuku.addBinderReceivedListenerSticky(shizukuBinder) }
        if (BixbyEngine.prefs.orbEnabled) OverlayService.start(this)
        setContent { BixbyTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        Permissions.autoGrant(this)
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuGranted); Shizuku.removeBinderReceivedListener(shizukuBinder) }
        super.onDestroy()
    }
}

@Composable
private fun Root() {
    val activity = LocalContext.current as ComponentActivity
    val dark = isDark()
    LaunchedEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark })
    }
    val tabs = remember { listOf(TabItem("Bixby", MiuixIcons.Messages), TabItem("Chats", MiuixIcons.Recent), TabItem("Tools", MiuixIcons.Tasks),
        TabItem("Settings", MiuixIcons.Settings), TabItem("About", MiuixIcons.Info)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(tab != 0) { tab = 0 }
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    when (LocalDesign.current) {
        Design.Miuix -> MiuixRoot(tabs, tab, { tab = it }, imeUp, dark)
        Design.Glass -> GlassRoot(tabs, tab, { tab = it }, imeUp)
        Design.One -> OneRoot(tabs, tab, { tab = it }, imeUp)
    }
}

@Composable
private fun Pages(tab: Int, onTab: (Int) -> Unit, padding: PaddingValues) {
    AnimatedContent(tab, transitionSpec = {
        val dir = if (targetState > initialState) 1 else -1
        (fadeIn(tween(240)) + slideInHorizontally(tween(280)) { dir * it / 10 }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(280)) { -dir * it / 10 })
    }, label = "tabs") { t ->
        when (t) {
            0 -> HomeScreen(padding)
            1 -> ChatsScreen(padding) { onTab(0) }
            2 -> ToolsScreen(padding)
            3 -> SettingsScreen(padding)
            else -> AboutScreen(padding)
        }
    }
}

/** Miuix: floating tab bar over blurred content. */
@Composable
private fun MiuixRoot(tabs: List<TabItem>, tab: Int, onTab: (Int) -> Unit, imeUp: Boolean, dark: Boolean) {
    val look by BixbyEngine.look.collectAsState()
    val c = MiuixTheme.colorScheme
    val blur = if (look.blur && isRuntimeShaderSupported()) rememberBlurBackdrop { drawRect(c.surface); drawContent() } else null
    val floatShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
    val highlight = if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    Scaffold(bottomBar = {
        AnimatedVisibility(!imeUp, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            FloatingNavigationBar(
                modifier = if (blur != null) Modifier.textureBlur(blur, floatShape, 25f,
                    colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = c.surfaceContainer.copy(alpha = .6f)))), highlight = highlight) else Modifier,
                color = if (blur != null) androidx.compose.ui.graphics.Color.Transparent else c.surfaceContainer) {
                tabs.forEachIndexed { i, t -> FloatingNavigationBarItem(selected = tab == i, onClick = { onTab(i) }, icon = t.icon, label = t.label) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().then(if (blur != null) Modifier.blurLayer(blur) else Modifier)) { Pages(tab, onTab, padding) }
    }
}

/** Liquid glass: glass tab bar floating over the pages. */
@Composable
private fun GlassRoot(tabs: List<TabItem>, tab: Int, onTab: (Int) -> Unit, imeUp: Boolean) {
    val look by BixbyEngine.look.collectAsState()
    val c = MiuixTheme.colorScheme
    val glass = rememberGlassBackdrop { drawRect(c.background); drawContent() }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassLayer(glass)) { Pages(tab, onTab, PaddingValues(bottom = 96.dp + navInset)) }
        AnimatedVisibility(!imeUp, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            if (look.navStyle == 1) SplitBar(tabs, tab, onTab, glass, { BixbyEngine.newChat(); onTab(0) }, { onTab(0); BixbyEngine.listen() },
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp))
            else LiquidBottomTabs(selectedTabIndex = { tab }, onTabSelected = onTab, backdrop = glass, tabsCount = tabs.size,
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                tabs.forEachIndexed { i, t ->
                    LiquidBottomTab({ onTab(i) }) {
                        Icon(t.icon, t.label, Modifier.size(24.dp), tint = c.onSurface)
                        Text(t.label, fontSize = 11.sp, color = c.onSurface)
                    }
                }
            }
        }
    }
}

/** One UI: flat bottom bar with a soft pill behind the selected tab. */
@Composable
private fun OneRoot(tabs: List<TabItem>, tab: Int, onTab: (Int) -> Unit, imeUp: Boolean) {
    val c = MiuixTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.weight(1f)) { Pages(tab, onTab, PaddingValues()) }
        AnimatedVisibility(!imeUp) {
            Row(Modifier.fillMaxWidth().background(c.surfaceContainer).navigationBarsPadding().height(64.dp)) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == tab
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTab(i) }.padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.primary.copy(alpha = .14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp)) { Icon(t.icon, t.label, tint = if (sel) c.primary else c.onSurfaceVariantActions) }
                        Text(t.label, fontSize = 11.sp, color = if (sel) c.primary else c.onSurfaceVariantSummary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** Two glass capsules, as in iOS: tabs on the left, round action buttons on the right. */
@Composable
private fun SplitBar(tabs: List<TabItem>, tab: Int, onTab: (Int) -> Unit, glass: Backdrop, onNew: () -> Unit, onTalk: () -> Unit, modifier: Modifier = Modifier) {
    val c = MiuixTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidBottomTabs(selectedTabIndex = { tab }, onTabSelected = onTab, backdrop = glass, tabsCount = tabs.size, modifier = Modifier.weight(1f)) {
            tabs.forEachIndexed { i, t -> LiquidBottomTab({ onTab(i) }) { Icon(t.icon, t.label, Modifier.size(22.dp), tint = c.onSurface) } }
        }
        Row(Modifier.height(64.dp)
            .drawBackdrop(glass, { CircleShape }, effects = { vibrancy(); blur(8.dp.toPx()); lens(16.dp.toPx(), 32.dp.toPx()) },
                onDrawSurface = { drawRect(c.surfaceContainer.copy(alpha = .55f)) })
            .padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassRound(onNew) { Icon(MiuixIcons.Add, "New chat", Modifier.size(24.dp), tint = c.onSurface) }
            GlassRound(onTalk) { Image(painterResource(R.drawable.ic_bixby_logo), "Talk to Bixby", Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun GlassRound(onClick: () -> Unit, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .88f else 1f, spring(dampingRatio = .55f, stiffness = 500f), label = "press")
    Box(Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
        .background(if (pressed) MiuixTheme.colorScheme.onSurface.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Transparent)
        .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center) { content() }
}
