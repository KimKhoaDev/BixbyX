package com.bixbyx.assistant.agent

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Short vibration cues: a tick when Bixby starts listening, a click when it has answered. */
object Haptics {
    fun tick(ctx: Context) = play(ctx, VibrationEffect.EFFECT_TICK, 12)
    fun click(ctx: Context) = play(ctx, VibrationEffect.EFFECT_CLICK, 25)

    private fun play(ctx: Context, effect: Int, ms: Long) {
        runCatching {
            val v = ctx.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(effect))
            else v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
