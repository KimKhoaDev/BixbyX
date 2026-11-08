package com.bixbyx.kilo.assistant.service

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.bixbyx.kilo.assistant.R
import com.bixbyx.kilo.assistant.agent.BState
import com.bixbyx.kilo.assistant.agent.BixbyEngine
import com.bixbyx.kilo.assistant.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Floating Bixby layer (needs "Display over other apps"):
 *  - the animated icon bubble (drag, snaps to the screen edge, tap = open, long-press = read the screen)
 *  - a glowing step bar at the top while Bixby listens/works in other apps
 *  - the assist panel that slides up
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lcr = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lcr
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var orb: ComposeView? = null
    private var panel: ComposeView? = null
    private var bar: ComposeView? = null
    private var gesture: GestureWake? = null
    private val jobs = mutableListOf<kotlinx.coroutines.Job>()
    private lateinit var orbLp: WindowManager.LayoutParams
    private val barVisible = MutableStateFlow(false)

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
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("bx", "Bixby", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "bx").setSmallIcon(R.drawable.ic_stat_bixby).setContentTitle("Bixby is ready").build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try { ServiceCompat.startForeground(this, 1, n, type) }
        catch (e: Exception) { ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        addOrb()
        if (BixbyEngine.prefs.gestureWake) gesture = GestureWake(this) { BixbyEngine.panelOpen.value = true; BixbyEngine.listen() }.also { it.start() }
        lcr.handleLifecycleEvent(Lifecycle.Event.ON_START); lcr.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        jobs += BixbyEngine.scope.launch { BixbyEngine.panelOpen.collect { open -> if (open) showPanel() else hidePanel() } }
        jobs += BixbyEngine.scope.launch { BixbyEngine.state.collect { if (it == BState.Working) BixbyEngine.panelOpen.value = false } }
        jobs += BixbyEngine.scope.launch {
            combine(BixbyEngine.state, BixbyEngine.panelOpen) { s, p -> s != BState.Idle && !p }.distinctUntilChanged()
                .collect { show -> if (show) showBar() else hideBar() }
        }
    }

    private fun ComposeView.wire() {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ bubble
    private fun addOrb() {
        orbLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = -dp(8); y = dp(300) }
        orb = ComposeView(this).apply {
            wire()
            setContent {
                OneUITheme {
                    val s by BixbyEngine.state.collectAsState()
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (BixbyEngine.state.value == BState.Idle) {
                                            // read the screen *before* our panel covers it
                                            if (BixbyEngine.prefs.autoDescribe && AgentAccessibilityService.running()) BixbyEngine.describeScreen()
                                            BixbyEngine.panelOpen.value = true
                                        } else BixbyEngine.stop()
                                    },
                                    onLongPress = { BixbyEngine.describeScreen() })
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(onDragEnd = { snapToEdge() }) { c, d ->
                                    c.consume(); orbLp.x += d.x.toInt(); orbLp.y += d.y.toInt()
                                    runCatching { wm.updateViewLayout(orb, orbLp) }
                                }
                            }) { FloatingBubble(s) }
                }
            }
        }
        wm.addView(orb, orbLp)
    }

    /** After a drag, glide to the nearest screen edge with a little overshoot. */
    private fun snapToEdge() {
        val w = resources.displayMetrics.widthPixels
        val vw = orb?.width ?: dp(100)
        val target = if (orbLp.x + vw / 2 < w / 2) -dp(8) else w - vw + dp(8)
        ValueAnimator.ofInt(orbLp.x, target).apply {
            duration = 380; interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { orbLp.x = it.animatedValue as Int; runCatching { wm.updateViewLayout(orb, orbLp) } }
        }.start()
    }

    // ------------------------------------------------------------------ top step bar
    private fun showBar() {
        if (bar == null) {
            val top = run { val id = resources.getIdentifier("status_bar_height", "dimen", "android"); if (id > 0) resources.getDimensionPixelSize(id) else dp(24) }
            val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
                .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = top - dp(10) }
            bar = ComposeView(this).apply {
                wire()
                setContent {
                    OneUITheme {
                        val v by barVisible.collectAsState()
                        TopStepBar(v) { BixbyEngine.panelOpen.value = true }
                    }
                }
            }
            wm.addView(bar, lp)
        }
        barVisible.value = true
    }

    private fun hideBar() {
        barVisible.value = false
        handler.postDelayed({ if (!barVisible.value) bar?.let { runCatching { wm.removeView(it) }; bar = null } }, 450)
    }

    // ------------------------------------------------------------------ panel
    private val panelVisible = MutableStateFlow(false)

    private fun showPanel() {
        if (panel == null) {
            val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
                .apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
            panel = ComposeView(this).apply {
                wire()
                setContent {
                    OneUITheme {
                        val v by panelVisible.collectAsState()
                        BixbySheet(v) { BixbyEngine.panelOpen.value = false }
                    }
                }
            }
            wm.addView(panel, lp)
        }
        panelVisible.value = true
    }

    private fun hidePanel() {
        panelVisible.value = false
        handler.postDelayed({ if (!panelVisible.value) panel?.let { runCatching { wm.removeView(it) }; panel = null } }, 380)
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        jobs.forEach { it.cancel() }; gesture?.stop(); handler.removeCallbacksAndMessages(null)
        listOf(panel, bar, orb).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        lcr.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); super.onDestroy()
    }
}

