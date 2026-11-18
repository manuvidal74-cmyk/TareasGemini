package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskItem
import com.example.ui.theme.LocalAppColors
import com.example.util.ChecklistUtils
import com.example.util.DateUtils

@Composable
fun DailyTasksSection(
    dailyTasks: List<TaskItem>,
    completedTodayCount: Int,
    onQuickCompleteDaily: (TaskItem) -> Unit,
    onUndoCompleteDaily: (TaskItem) -> Unit,
    onEditDaily: (TaskItem) -> Unit,
    onAddDailyTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val today = DateUtils.todayISO()
    val pendingToday = dailyTasks.filter { it.fecha <= today }
    val doneToday = dailyTasks.count { it.ultimaFechaCompletadaDiaria == today }
    val totalDaily = dailyTasks.size.coerceAtLeast(pendingToday.size + completedTodayCount)
    val effectiveDone = maxOf(doneToday, completedTodayCount).coerceAtMost(totalDaily)
    val progress = if (totalDaily > 0) effectiveDone.toFloat() / totalDaily.toFloat() else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_tasks_section"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, appColors.purple.copy(alpha = 0.35f)),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔁 Tareas diarias",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.purple,
                    )
                    Text(
                        text = if (totalDaily == 0) {
                            "Crea rutinas diarias con recordatorio automático"
                        } else {
                            "$effectiveDone de $totalDaily completadas hoy"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalButton(
                    onClick = onAddDailyTask,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_daily_task_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Diaria", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (totalDaily > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = appColors.purple,
                    trackColor = appColors.purpleContainer,
                )
            }

            if (dailyTasks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dailyTasks.forEach { task ->
                        val isDoneForToday = task.ultimaFechaCompletadaDiaria == today
                        val isRestDay = !isDoneForToday && task.fecha > today
                        val weekdayLabel = DateUtils.formatWeekdaysLabel(task.diasSemana)
                        val checkProgress = ChecklistUtils.progress(task.notas)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDoneForToday) appColors.bannerOkBg else appColors.cardSubtleBg,
                            border = BorderStroke(
                                1.dp,
                                if (isDoneForToday) appColors.green.copy(alpha = 0.4f) else appColors.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onEditDaily(task) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (isDoneForToday) {
                                        Icons.Outlined.CheckCircle
                                    } else {
                                        Icons.Outlined.RadioButtonUnchecked
                                    },
                                    contentDescription = if (isDoneForToday) "Desmarcar tarea diaria" else "Marcar tarea diaria completada",
                                    tint = if (isDoneForToday) appColors.green else appColors.purple,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            if (isDoneForToday) onUndoCompleteDaily(task)
                                            else onQuickCompleteDaily(task)
                                        }
                                        .testTag("quick_complete_daily_${task.id}"),
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.titulo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDoneForToday) appColors.green else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (task.hora.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                if (task.notificacionActiva) {
                                                    Icon(
                                                        Icons.Outlined.NotificationsActive,
                                                        contentDescription = null,
                                                        tint = appColors.blue,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                                Text(
                                                    text = task.hora,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = appColors.blue,
                                                )
                                            }
                                        }
                                        if (weekdayLabel.isNotEmpty()) {
                                            Text(
                                                text = weekdayLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = appColors.purple,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        if (checkProgress != null) {
                                            Text(
                                                text = "☑️ ${checkProgress.first}/${checkProgress.second}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (task.rachaDiaria > 0) {
                                            Text(
                                                text = "🔥 ${task.rachaDiaria}d",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = appColors.amber,
                                            )
                                        }
                                        Text(
                                            text = when {
                                                isDoneForToday -> "✓ Hecha · Próx: ${DateUtils.formatDateES(task.fecha)}"
                                                isRestDay -> "Próx: ${DateUtils.formatDateES(task.fecha)}"
                                                else -> "Pendiente hoy"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isDoneForToday) appColors.green else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
