package com.bixbyx.assistant.agent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

/** One UI 9 style "Diagnostics": quick device health numbers. */
data class Health(val battery: Int, val tempC: Float, val charging: Boolean, val healthOk: Boolean,
                  val storageUsed: Float, val storageFreeGb: Float, val ramUsed: Float, val ramFreeGb: Float, val uptimeH: Float) {
    fun asText() = "Battery $battery% (${if (charging) "charging" else "discharging"}), battery temp ${"%.1f".format(tempC)}°C, " +
        "battery health ${if (healthOk) "good" else "degraded"}, storage ${(storageUsed * 100).toInt()}% used (${"%.1f".format(storageFreeGb)} GB free), " +
        "RAM ${(ramUsed * 100).toInt()}% used (${"%.1f".format(ramFreeGb)} GB free), uptime ${"%.1f".format(uptimeH)} h"
}

object Diagnostics {
    fun read(ctx: Context): Health {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
        val health = b?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
        val fs = StatFs(Environment.getDataDirectory().path)
        val total = fs.totalBytes.toFloat(); val free = fs.availableBytes.toFloat()
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val gb = 1024f * 1024f * 1024f
        return Health(lvl * 100 / scale, temp,
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            health == BatteryManager.BATTERY_HEALTH_GOOD, 1f - free / total, free / gb,
            1f - mi.availMem.toFloat() / mi.totalMem, mi.availMem / gb, SystemClock.elapsedRealtime() / 3_600_000f)
    }
}
