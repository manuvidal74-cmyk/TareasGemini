package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskType
import com.example.util.DateUtils

object NotificationHelper {
    const val CHANNEL_TASKS = "tareas_avisos_channel"
    const val CHANNEL_DAILY = "resumen_diario_channel"

    const val ACTION_TASK_ALARM = "com.example.action.TASK_ALARM"
    const val ACTION_DAILY_SUMMARY = "com.example.action.DAILY_SUMMARY"
    const val ACTION_COMPLETE_TASK = "com.example.action.COMPLETE_TASK"
    const val ACTION_POSTPONE_TASK = "com.example.action.POSTPONE_TASK"
    const val ACTION_SNOOZE_TASK = "com.example.action.SNOOZE_TASK"

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val DAILY_SUMMARY_NOTIFICATION_ID = 900001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                context.getString(R.string.notif_channel_tasks_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_tasks_desc)
                enableVibration(true)
            }

            val dailyChannel = NotificationChannel(
                CHANNEL_DAILY,
                context.getString(R.string.notif_channel_daily_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_daily_desc)
            }

            nm.createNotificationChannel(taskChannel)
            nm.createNotificationChannel(dailyChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notificationIdForTask(taskId: String): Int =
        (taskId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)

    fun showTaskNotification(context: Context, task: TaskItem) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notifId = notificationIdForTask(task.id)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_COMPLETE_TASK
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val postponeIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_POSTPONE_TASK
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val postponePending = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            postponeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = when {
            task.repeticion == TaskRecurrence.DIARIA -> "🔁 Tarea diaria"
            task.tipo == TaskType.AVISO -> "🔔 Aviso"
            else -> "📌 Tarea"
        }
        val timeText = if (task.hora.isNotBlank()) " · ${task.hora}" else ""
        val bodyText = buildString {
            append("${DateUtils.formatDateES(task.fecha)}$timeText")
            if (task.notas.isNotBlank()) {
                append("\n")
                append(task.notas.take(160))
            }
        }

        val snoozeIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE_TASK
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            notifId + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("$prefix: ${task.titulo}")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "✓ Hecho", completePending)
            .addAction(0, "⏰ +30 min", snoozePending)
            .addAction(0, "⏭️ +1 día", postponePending)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showDailySummaryNotification(
        context: Context,
        dailyTasksCount: Int,
        todayTasksCount: Int,
        overdueCount: Int,
        topTitles: List<String>,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            DAILY_SUMMARY_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalToday = dailyTasksCount + todayTasksCount + overdueCount
        val title = if (totalToday == 0) {
            "📋 Buenos días: todo al día"
        } else {
            "📋 Resumen diario: $totalToday pendientes"
        }

        val summaryLine = buildString {
            val parts = mutableListOf<String>()
            if (dailyTasksCount > 0) parts.add("$dailyTasksCount diarias")
            if (todayTasksCount > 0) parts.add("$todayTasksCount para hoy")
            if (overdueCount > 0) parts.add("$overdueCount vencidas")
            if (parts.isEmpty()) append("Sin tareas pendientes para hoy.")
            else append(parts.joinToString(" · "))
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(summaryLine)
        topTitles.take(5).forEach { inboxStyle.addLine("• $it") }

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPending)

        try {
            NotificationManagerCompat.from(context).notify(DAILY_SUMMARY_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showTestNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            900999,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🔔 Prueba de notificación local")
            .setContentText("Las notificaciones locales de Tareas funcionan correctamente.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)

        try {
            NotificationManagerCompat.from(context).notify(900999, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
