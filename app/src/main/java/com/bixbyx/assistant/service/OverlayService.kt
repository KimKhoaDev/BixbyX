package com.bixbyx.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import androidx.savedstate.*
import com.bixbyx.assistant.agent.BState
import com.bixbyx.assistant.agent.BixbyEngine
import com.bixbyx.assistant.ui.*

/**
 * Floating Bixby orb drawn over every app (needs "Display over other apps").
 * Tap = open the Bixby panel, drag = move, shows live status while the agent works in other apps.
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lcr = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lcr
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var wm: WindowManager
    private var orb: ComposeView? = null
    private var panel: ComposeView? = null
    private var gesture: GestureWake? = null
    private val jobs = mutableListOf<kotlinx.coroutines.Job>()
    private lateinit var orbLp: WindowManager.LayoutParams

    companion object {
        fun start(ctx: Context) { if (Settings.canDrawOverlays(ctx)) ctx.startForegroundService(Intent(ctx, OverlayService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }

    override fun onCreate() {
        super.onCreate()
        ssc.performAttach(); ssc.performRestore(null)
        lcr.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        BixbyEngine.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bx", "Bixby", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "bx").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Bixby is ready").build())
        addOrb()
        if (BixbyEngine.prefs.gestureWake) gesture = GestureWake(this) {
            BixbyEngine.panelOpen.value = true; BixbyEngine.listen()
        }.also { it.start() }
        lcr.handleLifecycleEvent(Lifecycle.Event.ON_START); lcr.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        jobs += BixbyEngine.scope.launch { BixbyEngine.panelOpen.collect { open -> if (open) showPanel() else hidePanel() } }
        jobs += BixbyEngine.scope.launch { BixbyEngine.state.collect { if (it == BState.Working) BixbyEngine.panelOpen.value = false } }
    }

    private fun ComposeView.wire() {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
    }

    private fun addOrb() {
        orbLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 600 }
        orb = ComposeView(this).apply {
            wire()
            setContent {
                OneUITheme {
                    val s by BixbyEngine.state.collectAsState()
                    val status by BixbyEngine.status.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .pointerInput(Unit) { detectTapGestures(onTap = {
                                if (s == BState.Working) BixbyEngine.stop() else BixbyEngine.panelOpen.value = true }) }
                            .pointerInput(Unit) { detectDragGestures { c, d ->
                                c.consume(); orbLp.x += d.x.toInt(); orbLp.y += d.y.toInt(); wm.updateViewLayout(orb, orbLp) } }) {
                        BixbyOrb(s, 56.dp)
                        if (s == BState.Working) {
                            Text12("$status  ✕")
                        }
                    }
                }
            }
        }
        wm.addView(orb, orbLp)
    }

    @Composable private fun Text12(t: String) {
        androidx.compose.material3.Text(t, color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
            maxLines = 1, modifier = Modifier.background(Color(0xCC1C1C1E), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
    }

    private fun showPanel() {
        if (panel != null) return
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        panel = ComposeView(this).apply {
            wire()
            setContent {
                OneUITheme {
                    BixbySheet(onDismiss = { BixbyEngine.panelOpen.value = false })
                }
            }
        }
        wm.addView(panel, lp)
    }

    private fun hidePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() {
        jobs.forEach { it.cancel() }; gesture?.stop(); hidePanel(); orb?.let { runCatching { wm.removeView(it) } }
        lcr.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); super.onDestroy()
    }
}
