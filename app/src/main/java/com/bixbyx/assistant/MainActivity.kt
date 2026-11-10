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
import androidx.compose.foundation.layout.*
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
    val look by BixbyEngine.look.collectAsState()
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

    val c = MiuixTheme.colorScheme
    val glass = rememberGlassBackdrop { drawRect(c.background); drawContent() }
    val blur = if (look.blur && isRuntimeShaderSupported()) rememberBlurBackdrop { drawRect(c.surface); drawContent() } else null
    val floatShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
    val highlight = if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight

    Scaffold(bottomBar = {
        AnimatedVisibility(!imeUp, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            when (look.navStyle) {
                0 -> LiquidBottomTabs(selectedTabIndex = { tab }, onTabSelected = { tab = it }, backdrop = glass, tabsCount = tabs.size,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    tabs.forEachIndexed { i, t ->
                        LiquidBottomTab({ tab = i }) {
                            Icon(t.icon, t.label, Modifier.size(24.dp), tint = c.onSurface)
                            Text(t.label, fontSize = 11.sp, color = c.onSurface)
                        }
                    }
                }
                1 -> FloatingNavigationBar(
                    modifier = if (blur != null) Modifier.textureBlur(blur, floatShape, 25f,
                        colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = c.surfaceContainer.copy(alpha = .6f)))), highlight = highlight) else Modifier,
                    color = if (blur != null) androidx.compose.ui.graphics.Color.Transparent else c.surfaceContainer,
                    horizontalAlignment = when (look.navPosition) { 1 -> Alignment.Start; 2 -> Alignment.End; else -> Alignment.CenterHorizontally }) {
                    tabs.forEachIndexed { i, t -> FloatingNavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = t.icon, label = t.label) }
                }
                else -> NavigationBar(mode = NavigationBarDisplayMode.entries.getOrElse(look.navMode) { NavigationBarDisplayMode.IconAndText }) {
                    tabs.forEachIndexed { i, t -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = t.icon, label = t.label) }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().glassLayer(glass).then(if (blur != null) Modifier.blurLayer(blur) else Modifier)) {
            AnimatedContent(tab, transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(240)) + slideInHorizontally(tween(280)) { dir * it / 10 }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(280)) { -dir * it / 10 })
            }, label = "tabs") { t ->
                when (t) {
                    0 -> HomeScreen(padding)
                    1 -> ChatsScreen(padding) { tab = 0 }
                    2 -> ToolsScreen(padding)
                    3 -> SettingsScreen(padding)
                    else -> AboutScreen(padding)
                }
            }
        }
    }
}
