package com.bixbyx.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {
    data class El(val id: Int, val label: String, val cls: String, val click: Boolean, val edit: Boolean,
                  val scroll: Boolean, val cx: Int, val cy: Int)

    companion object {
        var instance: AgentAccessibilityService? = null
        fun running() = instance != null
    }

    private var last: List<El> = emptyList()
    private var upDown = false
    private var downDown = false
    private var fired = false
    override fun onServiceConnected() { instance = this; com.bixbyx.assistant.agent.BixbyEngine.init(this) }

    /** Optional shortcut: press Volume Up + Volume Down together to summon Bixby. */
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (!com.bixbyx.assistant.agent.BixbyEngine.prefs.volumeCombo) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> upDown = down
            KeyEvent.KEYCODE_VOLUME_DOWN -> downDown = down
            else -> return false
        }
        if (upDown && downDown && !fired) { fired = true; openBixby(this, true) }
        if (!upDown && !downDown) fired = false
        return false
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: android.content.Intent?): Boolean { instance = null; return super.onUnbind(i) }

    /** The window the user is really looking at: skips Bixby's own popup/overlay windows. */
    private fun userRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) return active
        return windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() != packageName } } ?: active
    }

    fun currentPackage(): String = userRoot()?.packageName?.toString() ?: "?"
    fun labelFor(id: Int): String = last.firstOrNull { it.id == id }?.label?.take(30).orEmpty()

    /** Numbered list of what is on screen, the same idea as private-agent's screen dump. */
    fun dump(): String {
        val root = userRoot() ?: return "(no window)"
        val out = mutableListOf<El>()
        walk(root, out)
        last = out
        return out.joinToString("\n") {
            val flags = listOfNotNull(if (it.click) "tap" else null, if (it.edit) "input" else null, if (it.scroll) "scroll" else null).joinToString(",")
            "[${it.id}] ${it.cls} \"${it.label.take(60)}\" ($flags) @${it.cx},${it.cy}"
        }.ifBlank { "(empty screen)" }
    }

    private fun walk(n: AccessibilityNodeInfo?, out: MutableList<El>) {
        if (n == null || out.size > 120 || !n.isVisibleToUser) return
        val label = (n.text ?: n.contentDescription ?: n.hintText ?: "").toString()
        val interesting = n.isClickable || n.isEditable || n.isScrollable || label.isNotBlank()
        if (interesting) {
            val r = Rect(); n.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0)
                out += El(out.size + 1, label, n.className?.toString()?.substringAfterLast('.') ?: "View",
                    n.isClickable, n.isEditable, n.isScrollable, r.centerX(), r.centerY())
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), out)
    }

    data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val ms: Long)

    /** Several touches at the same moment, e.g. hold a movement button while swiping to look around. */
    fun multi(strokes: List<Stroke>): Boolean {
        if (strokes.isEmpty()) return false
        val b = GestureDescription.Builder()
        strokes.take(4).forEach { st ->
            val path = Path().apply { moveTo(st.x1, st.y1); if (st.x2 != st.x1 || st.y2 != st.y1) lineTo(st.x2, st.y2) }
            b.addStroke(GestureDescription.StrokeDescription(path, 0, st.ms.coerceIn(50, 8000)))
        }
        return dispatchGesture(b.build(), null, null)
    }

    /** A picture of the screen, for models that can see. Android 11 or newer. */
    suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < 30) { cont.resume(null); return@suspendCancellableCoroutine }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val bmp = runCatching { Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                runCatching { result.hardwareBuffer.close() }
                cont.resume(bmp)
            }
            override fun onFailure(errorCode: Int) { cont.resume(null) }
        })
    }

    fun tapId(id: Int): Boolean { val e = last.firstOrNull { it.id == id } ?: return false; return tap(e.cx, e.cy) }

    fun tap(x: Int, y: Int, hold: Long = 60): Boolean = gesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, hold)

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long = 350): Boolean =
        gesture(Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }, ms)

    private fun gesture(p: Path, ms: Long): Boolean =
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)

    fun scroll(dir: String): Boolean {
        val w = resources.displayMetrics.widthPixels; val h = resources.displayMetrics.heightPixels
        return when (dir) {
            "down" -> swipe(w / 2, (h * .7).toInt(), w / 2, (h * .3).toInt())
            "up" -> swipe(w / 2, (h * .3).toInt(), w / 2, (h * .7).toInt())
            "left" -> swipe((w * .2).toInt(), h / 2, (w * .8).toInt(), h / 2)
            else -> swipe((w * .8).toInt(), h / 2, (w * .2).toInt(), h / 2)
        }
    }

    fun type(text: String, submit: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && submit) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return ok
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findEditable(c)?.let { return it } }
        return null
    }

    fun global(action: String): Boolean {
        val code = when (action) {
            "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN; "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> return false
        }
        return performGlobalAction(code)
    }
}
