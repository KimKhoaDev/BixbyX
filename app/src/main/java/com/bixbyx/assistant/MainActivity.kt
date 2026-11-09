package com.bixbyx.assistant

import android.Manifest
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.service.OverlayService
import com.bixbyx.assistant.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks

private class TabItem(val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BixbyEngine.init(this)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        if (BixbyEngine.prefs.orbEnabled) OverlayService.start(this)
        setContent { BixbyTheme { Root() } }
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

    Scaffold(bottomBar = {
        AnimatedVisibility(!imeUp, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            if (look.floatingNav) {
                FloatingNavigationBar(horizontalAlignment = when (look.navPosition) { 1 -> Alignment.Start; 2 -> Alignment.End; else -> Alignment.CenterHorizontally }) {
                    tabs.forEachIndexed { i, t -> FloatingNavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = t.icon, label = t.label) }
                }
            } else {
                NavigationBar(mode = NavigationBarDisplayMode.entries.getOrElse(look.navMode) { NavigationBarDisplayMode.IconAndText }) {
                    tabs.forEachIndexed { i, t -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = t.icon, label = t.label) }
                }
            }
        }
    }) { padding ->
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
