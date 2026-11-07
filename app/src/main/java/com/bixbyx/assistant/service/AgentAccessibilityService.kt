package com.bixbyx.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
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
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: android.content.Intent?): Boolean { instance = null; return super.onUnbind(i) }

    fun currentPackage(): String = rootInActiveWindow?.packageName?.toString() ?: "?"

    /** Numbered list of what is on screen, the same idea as private-agent's screen dump. */
    fun dump(): String {
        val root = rootInActiveWindow ?: return "(no window)"
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

    fun global(action: String): Boolean = performGlobalAction(when (action) {
        "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "recents" -> GLOBAL_ACTION_RECENTS
        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
        "lock" -> GLOBAL_ACTION_LOCK_SCREEN; "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
        else -> return false
    })
}
