package com.bixbyx.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.runtime.*
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.service.OverlayService
import com.bixbyx.assistant.ui.*

enum class Route { Home, Settings, Diagnostics, Interpreter }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BixbyEngine.init(this)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        if (BixbyEngine.prefs.orbEnabled) OverlayService.start(this)
        setContent {
            OneUITheme {
                var route by remember { mutableStateOf(Route.Home) }
                BackHandler(route != Route.Home) { route = Route.Home }
                AnimatedContent(route, transitionSpec = {
                    // One UI style: new page slides in from the right with a soft fade, old page drifts left
                    if (targetState != Route.Home)
                        (slideInHorizontally(spring(stiffness = 400f)) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                    else (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                }, label = "nav") { r ->
                    val back = { route = Route.Home }
                    when (r) {
                        Route.Home -> HomeScreen({ route = Route.Settings }, { route = Route.Diagnostics }, { route = Route.Interpreter })
                        Route.Settings -> SettingsScreen(back)
                        Route.Diagnostics -> DiagnosticsScreen(back)
                        Route.Interpreter -> InterpreterScreen(back)
                    }
                }
            }
        }
    }
}
