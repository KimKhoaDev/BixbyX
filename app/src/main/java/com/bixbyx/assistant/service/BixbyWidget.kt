package com.bixbyx.assistant.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bixbyx.assistant.AssistActivity
import com.bixbyx.assistant.MainActivity
import com.bixbyx.assistant.R

/** One UI 9 style Bixby widget: type or talk, or jump to conversation history. */
class BixbyWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        fun pi(i: Intent, c: Int) = PendingIntent.getActivity(ctx, c, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val v = RemoteViews(ctx.packageName, R.layout.widget_bixby)
        v.setOnClickPendingIntent(R.id.w_type, pi(Intent(ctx, AssistActivity::class.java), 1))
        v.setOnClickPendingIntent(R.id.w_talk, pi(Intent(ctx, AssistActivity::class.java).putExtra("listen", true), 2))
        v.setOnClickPendingIntent(R.id.w_history, pi(Intent(ctx, MainActivity::class.java), 3))
        ids.forEach { m.updateAppWidget(it, v) }
    }
}
