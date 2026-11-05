package com.cincikgames.not

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Ana ekran widget'ı: son notu gösterir, "Yaz" ve "Konuş" ile uygulamayı açar. */
class NoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, buildViews(context))
    }

    companion object {
        const val ACTION_WRITE = "com.cincikgames.not.WRITE"
        const val ACTION_SPEAK = "com.cincikgames.not.SPEAK"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
            if (ids.isNotEmpty()) {
                val views = buildViews(context)
                for (id in ids) manager.updateAppWidget(id, views)
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val note = NoteStore.load(context).trim()
            val preview = when {
                note.isEmpty() -> context.getString(R.string.widget_empty)
                note.length > 300 -> "…" + note.takeLast(300)
                else -> note
            }
            return RemoteViews(context.packageName, R.layout.widget_note).apply {
                setTextViewText(R.id.widget_text, preview)
                setOnClickPendingIntent(R.id.widget_text, open(context, ACTION_WRITE, 1))
                setOnClickPendingIntent(R.id.widget_write, open(context, ACTION_WRITE, 2))
                setOnClickPendingIntent(R.id.widget_speak, open(context, ACTION_SPEAK, 3))
            }
        }

        private fun open(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
