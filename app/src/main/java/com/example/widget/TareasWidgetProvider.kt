package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.toDomain
import com.example.data.model.TaskRecurrence
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TareasWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsNow(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_NEW_TASK = "extra_open_new_task"

        fun requestRefresh(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TareasWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        updateWidgetsNow(context, appWidgetManager, ids)
                    }
                }
            } catch (_: Exception) {
            }
        }

        private suspend fun updateWidgetsNow(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val today = DateUtils.todayISO()
            val pending = AppDatabase.getInstance(context.applicationContext)
                .taskDao()
                .getAllTasksOnce()
                .map { it.toDomain() }
                .filter { !it.completada }

            val dailyPending = pending.filter { it.repeticion == TaskRecurrence.DIARIA && it.fecha <= today }
            val overdue = pending.filter { it.repeticion != TaskRecurrence.DIARIA && it.fecha < today }
            val todayList = pending.filter { it.repeticion != TaskRecurrence.DIARIA && it.fecha == today }

            val combined = (overdue + dailyPending + todayList).distinctBy { it.id }

            val summaryText = when {
                combined.isEmpty() -> "✓ Todo al día para hoy"
                else -> buildString {
                    val parts = mutableListOf<String>()
                    if (overdue.isNotEmpty()) parts.add("${overdue.size} vencidas")
                    if (dailyPending.isNotEmpty()) parts.add("${dailyPending.size} diarias")
                    if (todayList.isNotEmpty()) parts.add("${todayList.size} hoy")
                    append(parts.joinToString(" · "))
                }
            }

            val itemsText = if (combined.isEmpty()) {
                "Pulsa + Tarea para añadir una nueva tarea o aviso."
            } else {
                combined.take(4).joinToString("\n") { task ->
                    val prefix = when {
                        task.fecha < today && task.repeticion != TaskRecurrence.DIARIA -> "🔴"
                        task.repeticion == TaskRecurrence.DIARIA -> "🔁"
                        else -> "📌"
                    }
                    val time = if (task.hora.isNotBlank()) " (${task.hora})" else ""
                    "$prefix ${task.titulo}$time"
                }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                910001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val newTaskIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_NEW_TASK, true)
            }
            val newTaskPending = PendingIntent.getActivity(
                context,
                910002,
                newTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_tareas).apply {
                    setTextViewText(R.id.widget_summary, summaryText)
                    setTextViewText(R.id.widget_items, itemsText)
                    setOnClickPendingIntent(R.id.widget_root, openPending)
                    setOnClickPendingIntent(R.id.widget_add_btn, newTaskPending)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
