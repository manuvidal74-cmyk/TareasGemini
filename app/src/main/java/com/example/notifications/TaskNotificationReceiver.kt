package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.toDomain
import com.example.data.model.TaskRecurrence
import com.example.data.repository.TareasRepository
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TareasRepository(context.applicationContext)
                val db = AppDatabase.getInstance(context.applicationContext)
                val settings = SettingsDataStore(context.applicationContext).getSettingsOnce()

                when (action) {
                    NotificationHelper.ACTION_TASK_ALARM -> {
                        val taskId = intent.getStringExtra(NotificationHelper.EXTRA_TASK_ID)
                        if (taskId != null && settings.notificacionesGlobalesActivas) {
                            val entity = db.taskDao().getTaskById(taskId)
                            if (entity != null && !entity.completada) {
                                NotificationHelper.showTaskNotification(
                                    context.applicationContext,
                                    entity.toDomain()
                                )
                            }
                        }
                    }

                    NotificationHelper.ACTION_DAILY_SUMMARY -> {
                        if (settings.notificacionesGlobalesActivas && settings.resumenDiarioActivo) {
                            val today = DateUtils.todayISO()
                            val pendingTasks = db.taskDao().getAllTasksOnce()
                                .map { it.toDomain() }
                                .filter { !it.completada }

                            val dailyTasks = pendingTasks.filter {
                                it.repeticion == TaskRecurrence.DIARIA && it.fecha <= today
                            }
                            val todayTasks = pendingTasks.filter {
                                it.repeticion != TaskRecurrence.DIARIA && it.fecha == today
                            }
                            val overdueTasks = pendingTasks.filter {
                                it.repeticion != TaskRecurrence.DIARIA && it.fecha < today
                            }
                            val topTitles = (dailyTasks + overdueTasks + todayTasks)
                                .distinctBy { it.id }
                                .map { it.titulo }

                            NotificationHelper.showDailySummaryNotification(
                                context = context.applicationContext,
                                dailyTasksCount = dailyTasks.size,
                                todayTasksCount = todayTasks.size,
                                overdueCount = overdueTasks.size,
                                topTitles = topTitles
                            )
                        }
                        // Schedule tomorrow's summary alarm
                        NotificationScheduler.scheduleDailySummary(context.applicationContext, settings)
                    }

                    NotificationHelper.ACTION_COMPLETE_TASK -> {
                        val taskId = intent.getStringExtra(NotificationHelper.EXTRA_TASK_ID)
                        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
                        if (taskId != null) {
                            repo.completeTask(
                                taskId = taskId,
                                notaCierre = "Completada desde notificación",
                                scheduleNextRecurrence = true
                            )
                        }
                        if (notifId != -1) {
                            NotificationHelper.cancelNotification(context.applicationContext, notifId)
                        }
                    }

                    NotificationHelper.ACTION_POSTPONE_TASK -> {
                        val taskId = intent.getStringExtra(NotificationHelper.EXTRA_TASK_ID)
                        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
                        if (taskId != null) {
                            repo.postponeTask(taskId, 1)
                        }
                        if (notifId != -1) {
                            NotificationHelper.cancelNotification(context.applicationContext, notifId)
                        }
                    }

                    NotificationHelper.ACTION_SNOOZE_TASK -> {
                        val taskId = intent.getStringExtra(NotificationHelper.EXTRA_TASK_ID)
                        val notifId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
                        if (taskId != null) {
                            NotificationScheduler.scheduleSnoozeNotification(
                                context.applicationContext,
                                taskId,
                                delayMinutes = 30
                            )
                        }
                        if (notifId != -1) {
                            NotificationHelper.cancelNotification(context.applicationContext, notifId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
