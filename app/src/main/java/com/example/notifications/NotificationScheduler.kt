package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.AppSettings
import com.example.data.model.TaskItem
import com.example.util.DateUtils

object NotificationScheduler {
    private const val DAILY_SUMMARY_REQUEST_CODE = 900001

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun scheduleTaskNotification(context: Context, task: TaskItem, globalEnabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = NotificationHelper.notificationIdForTask(task.id)
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = NotificationHelper.ACTION_TASK_ALARM
            putExtra(NotificationHelper.EXTRA_TASK_ID, task.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!globalEnabled || task.completada || !task.notificacionActiva || task.fecha.isBlank()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val timeToUse = task.hora.ifBlank { "09:00" }
        val triggerMillis = DateUtils.computeTriggerMillis(task.fecha, timeToUse, task.minutosAntes)

        // Only schedule if trigger time is in the future (or within 1 min tolerance)
        if (triggerMillis > System.currentTimeMillis() - 60_000L) {
            val safeTrigger = maxOf(triggerMillis, System.currentTimeMillis() + 5_000L)
            scheduleAlarm(alarmManager, safeTrigger, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun cancelTaskNotification(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = NotificationHelper.notificationIdForTask(taskId)
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = NotificationHelper.ACTION_TASK_ALARM
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        NotificationHelper.cancelNotification(context, requestCode)
    }

    fun scheduleDailySummary(context: Context, settings: AppSettings) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = NotificationHelper.ACTION_DAILY_SUMMARY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_SUMMARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!settings.notificacionesGlobalesActivas || !settings.resumenDiarioActivo) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerMillis = DateUtils.computeNextDailySummaryMillis(settings.horaResumenDiario)
        scheduleAlarm(alarmManager, triggerMillis, pendingIntent)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleSnoozeNotification(context: Context, taskId: String, delayMinutes: Int = 30) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = NotificationHelper.notificationIdForTask(taskId) + 10
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = NotificationHelper.ACTION_TASK_ALARM
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        scheduleAlarm(alarmManager, triggerAt, pendingIntent)
    }

    fun rescheduleAll(context: Context, tasks: List<TaskItem>, settings: AppSettings) {
        NotificationHelper.ensureChannels(context)
        tasks.forEach { task ->
            scheduleTaskNotification(context, task, settings.notificacionesGlobalesActivas)
        }
        scheduleDailySummary(context, settings)
    }
}
