package com.chvertko.focusstep.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chvertko.focusstep.AppPrefs
import com.chvertko.focusstep.MainActivity
import com.chvertko.focusstep.R

class FocusStepWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusStepWidgetProvider::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.focusstep_widget)
            val selected = AppPrefs.selectedApps(context).size
            val active = AppPrefs.protectionRequested(context) && selected > 0 && AppPrefs.isMonitorHeartbeatFresh(context)
            val status = when {
                selected == 0 -> "Выбери приложения"
                active -> "Защита активна · $selected приложений"
                AppPrefs.protectionRequested(context) -> "Нужно восстановить защиту"
                else -> "Защита выключена"
            }
            val stats = AppPrefs.dailyStats(context)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_stats, "Сегодня: ${stats.attempts} попыток · ${stats.completed} заданий · ${stats.waits} пауз")
            views.setViewVisibility(R.id.widget_stats, if (AppPrefs.widgetStatsVisible(context)) android.view.View.VISIBLE else android.view.View.GONE)
            runCatching {
                val icon = context.packageManager.getApplicationIcon(context.packageName)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).also { canvas -> icon.setBounds(0, 0, canvas.width, canvas.height); icon.draw(canvas) }
                views.setImageViewBitmap(R.id.widget_icon, bitmap)
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
