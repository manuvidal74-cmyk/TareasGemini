package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskCategory
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.ui.theme.LocalAppColors
import com.example.util.ChecklistUtils
import com.example.util.DateUtils
import com.example.util.NoteLine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: TaskItem,
    isProjected: Boolean = false,
    projectedDate: String? = null,
    onCompleteClick: () -> Unit,
    onPostponeOneDay: () -> Unit,
    onPostponeSevenDays: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDuplicateClick: (() -> Unit)? = null,
    onReopenClick: () -> Unit,
    onToggleChecklistLine: (Int) -> Unit,
    onGoogleCalendarClick: () -> Unit,
    onGoogleTasksClick: () -> Unit,
    onShareClick: () -> Unit,
    onJumpToCurrentTurn: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val displayDate = if (isProjected && projectedDate != null) projectedDate else task.fecha
    val diffDays = DateUtils.diffDaysFromToday(displayDate)
    var showOverflowMenu by remember { mutableStateOf(false) }

    val stripeColor = when {
        task.completada -> appColors.green
        task.relevancia == TaskRelevance.ALTA -> appColors.red
        task.relevancia == TaskRelevance.MEDIA -> appColors.amber
        else -> appColors.blue
    }

    val cardBg = if (isProjected) appColors.cardSubtleBg else MaterialTheme.colorScheme.surface
    val borderStroke = if (isProjected) {
        BorderStroke(1.dp, appColors.purple.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, appColors.borderSubtle)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = borderStroke,
        tonalElevation = if (isProjected) 0.dp else 1.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Header: Title + Relevance Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = task.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val (relBg, relFg, relLabel) = when (task.relevancia) {
                        TaskRelevance.ALTA -> Triple(appColors.redContainer, appColors.red, "Alta")
                        TaskRelevance.MEDIA -> Triple(appColors.amberContainer, appColors.amber, "Media")
                        TaskRelevance.BAJA -> Triple(appColors.blueContainer, appColors.blue, "Baja")
                    }
                    StatusPill(text = relLabel, containerColor = relBg, contentColor = relFg)
                }

                // Meta badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusPill(
                        text = if (task.tipo == TaskType.AVISO) "Aviso" else "Tarea",
                        containerColor = appColors.cardSubtleBg,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (task.categoria.isNotBlank()) {
                        val preset = TaskCategory.findByValue(task.categoria)
                        val (catBg, catFg) = when (preset) {
                            TaskCategory.TRABAJO -> appColors.blueContainer to appColors.blue
                            TaskCategory.CASA -> appColors.tealContainer to appColors.teal
                            TaskCategory.SALUD -> appColors.greenContainer to appColors.green
                            TaskCategory.TRAMITES -> appColors.amberContainer to appColors.amber
                            TaskCategory.COMPRAS -> appColors.purpleContainer to appColors.purple
                            null -> appColors.cardSubtleBg to appColors.headingText
                        }
                        val catEmoji = preset?.emoji ?: "🏷️"
                        StatusPill(
                            text = "$catEmoji ${task.categoria}",
                            containerColor = catBg,
                            contentColor = catFg,
                        )
                    }

                    if (task.repeticion != TaskRecurrence.NINGUNA) {
                        val wdLabel = if (task.repeticion == TaskRecurrence.DIARIA) {
                            DateUtils.formatWeekdaysLabel(task.diasSemana)
                        } else ""
                        val recText = if (wdLabel.isNotEmpty()) {
                            "🔁 ${task.repeticion.shortLabel} ($wdLabel)"
                        } else {
                            "🔁 ${task.repeticion.shortLabel}"
                        }
                        StatusPill(
                            text = recText,
                            containerColor = appColors.purpleContainer,
                            contentColor = appColors.purple,
                        )
                    }

                    if (task.repeticion == TaskRecurrence.DIARIA && task.rachaDiaria > 0) {
                        StatusPill(
                            text = "🔥 ${task.rachaDiaria}d racha",
                            containerColor = appColors.amberContainer,
                            contentColor = appColors.amber,
                        )
                    }

                    val checkProgress = ChecklistUtils.progress(task.notas)
                    if (checkProgress != null) {
                        val allDone = checkProgress.first == checkProgress.second
                        StatusPill(
                            text = "☑️ ${checkProgress.first}/${checkProgress.second}",
                            containerColor = if (allDone) appColors.greenContainer else appColors.blueContainer,
                            contentColor = if (allDone) appColors.green else appColors.blue,
                        )
                    }

                    if (task.traspasado) {
                        StatusPill(
                            text = "En Google",
                            containerColor = appColors.tealContainer,
                            contentColor = appColors.teal,
                        )
                    }

                    if (!task.completada && !isProjected) {
                        if (diffDays < 0) {
                            StatusPill(
                                text = "Vencida (${kotlin.math.abs(diffDays)}d)",
                                containerColor = appColors.red,
                                contentColor = Color.White,
                            )
                        } else if (diffDays == 0) {
                            StatusPill(
                                text = "Para Hoy",
                                containerColor = appColors.green,
                                contentColor = Color.White,
                            )
                        }
                    }

                    if (isProjected) {
                        StatusPill(
                            text = "Próxima repetición",
                            containerColor = appColors.purpleContainer,
                            contentColor = appColors.purple,
                        )
                    }
                }

                // Date, Time & Notification indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = buildString {
                            append("📅 ${DateUtils.formatDateES(displayDate)}")
                            if (task.hora.isNotBlank()) append("  ⏰ ${task.hora}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!task.completada && task.notificacionActiva) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(appColors.blueContainer.copy(alpha = 0.65f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = "Notificación local activa",
                                tint = appColors.blue,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (task.minutosAntes > 0) "-${task.minutosAntes}m" else "Aviso",
                                style = MaterialTheme.typography.labelSmall,
                                color = appColors.blue,
                            )
                        }
                    }
                }

                // Notes / Interactive Checklist
                if (task.notas.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = appColors.cardSubtleBg,
                        border = BorderStroke(1.dp, appColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (ChecklistUtils.hasChecklist(task.notas) && !isProjected) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                ChecklistUtils.parseNotes(task.notas).forEach { line ->
                                    when (line) {
                                        is NoteLine.ChecklistItem -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { onToggleChecklistLine(line.lineIndex) }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(
                                                    checked = line.checked,
                                                    onCheckedChange = { onToggleChecklistLine(line.lineIndex) },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = appColors.green,
                                                    ),
                                                    modifier = Modifier.size(34.dp),
                                                )
                                                Text(
                                                    text = line.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (line.checked) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                                                    modifier = Modifier.padding(start = 4.dp),
                                                )
                                            }
                                        }
                                        is NoteLine.PlainText -> {
                                            if (line.text.isNotBlank()) {
                                                Text(
                                                    text = line.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = task.notas,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }

                // Closure note & completed timestamp
                if (task.completada && task.notaCierre.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = appColors.bannerOkBg,
                        border = BorderStroke(1.dp, appColors.green.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Cierre: ${task.notaCierre}",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors.green,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                if (task.completada && !task.fechaCompletada.isNullOrBlank()) {
                    Text(
                        text = "✓ Realizada: ${DateUtils.formatDateTimeES(task.fechaCompletada)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = appColors.green,
                    )
                }

                HorizontalDivider(color = appColors.borderSubtle)

                // Clean Action Bar
                if (isProjected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onJumpToCurrentTurn != null) {
                            OutlinedButton(
                                onClick = onJumpToCurrentTurn,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize(),
                            ) {
                                Text("Ir al turno actual", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        OutlinedButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                        ) {
                            Text("Editar", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else if (!task.completada) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = onCompleteClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.green,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("complete_task_${task.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Completar", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onPostponeOneDay,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Text("+1d", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Editar tarea",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("task_more_menu_${task.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Más acciones",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Posponer +7 días") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onPostponeSevenDays()
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                                    text = { Text(if (task.traspasado) "✓ Abrir en Google Calendar" else "Enviar a Google Calendar") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onGoogleCalendarClick()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copiar para Google Tasks") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onGoogleTasksClick()
                                    },
                                )
                                if (onDuplicateClick != null) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                        text = { Text("Duplicar tarea") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onDuplicateClick()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    text = { Text("Compartir") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onShareClick()
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = appColors.red,
                                        )
                                    },
                                    text = { Text("Eliminar", color = appColors.red) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onDeleteClick()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onReopenClick,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reabrir")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onShareClick, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Outlined.Share, contentDescription = "Compartir")
                        }
                        IconButton(onClick = onEditClick, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = onDeleteClick, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = appColors.red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
