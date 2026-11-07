package com.bixbyx.assistant.service

import android.content.Context
import android.hardware.*
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Experimental "Gesture wake-up" (One UI 9): raise the phone toward your mouth, no hotword.
 * Heuristic: proximity goes NEAR while the phone is tilted ~25-80 degrees and was recently lifted.
 */
class GestureWake(private val ctx: Context, private val onWake: () -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tilt = 90f
    private var lastLift = 0L
    private var lastFire = 0L

    fun start() {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val (x, y, z) = e.values
            val g = sqrt(x * x + y * y + z * z)
            tilt = Math.toDegrees(atan2(sqrt(x * x + z * z), y).toDouble()).toFloat() // 0 = upright
            if (abs(g - 9.8f) > 3f) lastLift = now
        } else if (e.sensor.type == Sensor.TYPE_PROXIMITY) {
            val near = e.values[0] < e.sensor.maximumRange.coerceAtMost(5f) && e.values[0] < 3f
            if (near && now - lastLift < 2500 && tilt in 25f..80f && now - lastFire > 6000 &&
                pm.isInteractive && am.mode == AudioManager.MODE_NORMAL) {
                lastFire = now; onWake()
            }
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
