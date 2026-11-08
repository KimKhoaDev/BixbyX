package com.bixbyx.kilo.assistant.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bixbyx.kilo.assistant.AssistActivity

/** Quick Settings tile: a reliable way to summon Bixby on HyperOS. */
class BixbyTile : TileService() {
    override fun onStartListening() { qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() } }
    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = Intent(this, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("listen", true)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(i)
    }
}

