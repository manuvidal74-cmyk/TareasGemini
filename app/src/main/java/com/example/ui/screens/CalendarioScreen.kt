package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalendarTaskProjection
import com.example.data.model.CalendarViewMode
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRelevance
import com.example.ui.TareasUiState
import com.example.ui.components.TaskCard
import com.example.ui.theme.LocalAppColors
import com.example.util.DateUtils

@Composable
fun CalendarioScreen(
    uiState: TareasUiState,
    onChangeViewMode: (CalendarViewMode) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onJumpToday: () -> Unit,
    onSelectDate: (String) -> Unit,
    onSaveQuickNote: (String, String) -> Unit,
    onAddTaskInDate: (String) -> Unit,
    onAddObsInDate: (String) -> Unit,
    onEditDiaryEntry: (DiaryEntry) -> Unit,
    onCompleteClick: (TaskItem) -> Unit,
    onPostponeTask: (String, Int) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDuplicateTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onReopenTask: (TaskItem) -> Unit,
    onToggleChecklistLine: (String, Int) -> Unit,
    onGoogleCalendarClick: (TaskItem) -> Unit,
    onGoogleTasksClick: (TaskItem) -> Unit,
    onShareClick: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val calState = uiState.calendar
    val selectedDate = calState.selectedDateISO
    val todayISO = DateUtils.todayISO()

    var quickNoteText by remember(selectedDate) {
        mutableStateOf(uiState.dailyNotes[selectedDate] ?: "")
    }
    LaunchedEffect(uiState.dailyNotes[selectedDate], selectedDate) {
        val stored = uiState.dailyNotes[selectedDate] ?: ""
        if (stored != quickNoteText) {
            quickNoteText = stored
        }
    }

    val agendaItems = remember(uiState.tasks, uiState.settings.mostrarRealizadasCal, calState.viewMode) {
        if (calState.viewMode != CalendarViewMode.AGENDA) emptyList()
        else {
            val list = mutableListOf<CalendarTaskProjection>()
            for (i in 0..30) {
                val dateISO = DateUtils.addDays(todayISO, i)
                list.addAll(uiState.getItemsForDate(dateISO))
            }
            list
        }
    }

    val dayTasks = remember(uiState.tasks, uiState.settings.mostrarRealizadasCal, selectedDate) {
        uiState.getItemsForDate(selectedDate)
    }
    val dayDiary = remember(uiState.diaryEntries, selectedDate) {
        uiState.diaryEntries.filter { it.fecha == selectedDate }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendario_screen"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Calendar Header & Grid Card
        item(key = "calendar_card") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, appColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CalendarViewMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = calState.viewMode == mode,
                                onClick = { onChangeViewMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, CalendarViewMode.entries.size),
                                modifier = Modifier.testTag("cal_mode_${mode.storageValue}"),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (calState.viewMode != CalendarViewMode.AGENDA) {
                            IconButton(
                                onClick = { onShiftPeriod(-1) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Periodo anterior")
                            }
                        }

                        val titleText = when (calState.viewMode) {
                            CalendarViewMode.MES -> DateUtils.getMonthYearLabel(calState.cursorDateISO)
                                .replaceFirstChar { it.uppercase() }
                            CalendarViewMode.SEMANA -> DateUtils.getWeekRangeLabel(calState.cursorDateISO)
                            CalendarViewMode.AGENDA -> "Agenda (Próximos 30 días)"
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            color = appColors.headingText,
                            modifier = Modifier.weight(1f),
                            textAlign = if (calState.viewMode == CalendarViewMode.AGENDA) TextAlign.Start else TextAlign.Center,
                        )

                        OutlinedButton(
                            onClick = onJumpToday,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text("Hoy")
                        }

                        if (calState.viewMode != CalendarViewMode.AGENDA) {
                            IconButton(
                                onClick = { onShiftPeriod(1) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Periodo siguiente")
                            }
                        }
                    }

                    if (calState.viewMode == CalendarViewMode.MES || calState.viewMode == CalendarViewMode.SEMANA) {
                        // Weekday headers
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DateUtils.shortWeekDays.forEach { day ->
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        val cells: List<String?> = if (calState.viewMode == CalendarViewMode.MES) {
                            DateUtils.getMonthGridDays(calState.cursorDateISO)
                        } else {
                            DateUtils.getWeekDays(calState.cursorDateISO)
                        }

                        val rows = cells.chunked(7)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            rows.forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    for (i in 0 until 7) {
                                        val dateStr = week.getOrNull(i)
                                        if (dateStr == null) {
                                            Spacer(modifier = Modifier.weight(1f).height(50.dp))
                                        } else {
                                            val itemsDay = uiState.getItemsForDate(dateStr)
                                            val hasNote = !uiState.dailyNotes[dateStr].isNullOrBlank() ||
                                                uiState.diaryEntries.any { it.fecha == dateStr }
                                            CalendarDayCell(
                                                dateISO = dateStr,
                                                isToday = dateStr == todayISO,
                                                isSelected = dateStr == selectedDate,
                                                hasNote = hasNote,
                                                items = itemsDay,
                                                onClick = { onSelectDate(dateStr) },
                                                modifier = Modifier.weight(1f),
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

        if (calState.viewMode == CalendarViewMode.AGENDA) {
            if (agendaItems.isEmpty()) {
                item(key = "agenda_empty") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No hay tareas programadas para los próximos 30 días.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                val grouped = agendaItems.groupBy { it.displayDate }
                grouped.forEach { (dateISO, projections) ->
                    item(key = "agenda_header_$dateISO") {
                        Text(
                            text = "📅 ${DateUtils.formatDateES(dateISO)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = appColors.headingText,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(projections, key = { "${dateISO}_${it.task.id}_${it.isProjected}" }) { proj ->
                        TaskCard(
                            task = proj.task,
                            isProjected = proj.isProjected,
                            projectedDate = proj.displayDate,
                            onCompleteClick = { onCompleteClick(proj.task) },
                            onPostponeOneDay = { onPostponeTask(proj.task.id, 1) },
                            onPostponeSevenDays = { onPostponeTask(proj.task.id, 7) },
                            onEditClick = { onEditTask(proj.task) },
                            onDuplicateClick = { onDuplicateTask(proj.task) },
                            onDeleteClick = { onDeleteTask(proj.task) },
                            onReopenClick = { onReopenTask(proj.task) },
                            onToggleChecklistLine = { idx -> onToggleChecklistLine(proj.task.id, idx) },
                            onGoogleCalendarClick = { onGoogleCalendarClick(proj.task) },
                            onGoogleTasksClick = { onGoogleTasksClick(proj.task) },
                            onShareClick = { onShareClick(proj.task) },
                            onJumpToCurrentTurn = { onSelectDate(proj.task.fecha) },
                        )
                    }
                }
            }
        } else {
            // Selected Day Detail Card
            item(key = "selected_day_detail") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, appColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
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
                            Text(
                                text = "📅 Día ${DateUtils.formatDateES(selectedDate)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = appColors.headingText,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onAddObsInDate(selectedDate) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("📖 +Obs")
                                }
                                Button(
                                    onClick = { onAddTaskInDate(selectedDate) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("➕ Tarea")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = quickNoteText,
                            onValueChange = {
                                quickNoteText = it
                                onSaveQuickNote(selectedDate, it)
                            },
                            label = { Text("📝 Nota fija de este día (se guarda sola)") },
                            placeholder = { Text("Escribe aquí notas o apuntes del día...") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (dayTasks.isEmpty() && dayDiary.isEmpty()) {
                            Text(
                                text = "Sin tareas ni observaciones en este día.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            items(dayTasks, key = { "day_${selectedDate}_${it.task.id}_${it.isProjected}" }) { proj ->
                TaskCard(
                    task = proj.task,
                    isProjected = proj.isProjected,
                    projectedDate = proj.displayDate,
                    onCompleteClick = { onCompleteClick(proj.task) },
                    onPostponeOneDay = { onPostponeTask(proj.task.id, 1) },
                    onPostponeSevenDays = { onPostponeTask(proj.task.id, 7) },
                    onEditClick = { onEditTask(proj.task) },
                    onDuplicateClick = { onDuplicateTask(proj.task) },
                    onDeleteClick = { onDeleteTask(proj.task) },
                    onReopenClick = { onReopenTask(proj.task) },
                    onToggleChecklistLine = { idx -> onToggleChecklistLine(proj.task.id, idx) },
                    onGoogleCalendarClick = { onGoogleCalendarClick(proj.task) },
                    onGoogleTasksClick = { onGoogleTasksClick(proj.task) },
                    onShareClick = { onShareClick(proj.task) },
                    onJumpToCurrentTurn = { onSelectDate(proj.task.fecha) },
                )
            }

            if (dayDiary.isNotEmpty()) {
                item(key = "day_diary_header") {
                    Text(
                        text = "📖 Diario de este día (${dayDiary.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        color = appColors.headingText,
                    )
                }
                items(dayDiary, key = { "day_diary_${it.id}" }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (entry.tipo == "manual") MaterialTheme.colorScheme.surface else appColors.bannerOkBg,
                        border = BorderStroke(1.dp, appColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (entry.tipo == "manual") "📝 Nota manual" else "✅ ${entry.tituloTarea}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (entry.tipo == "manual") appColors.blue else appColors.green,
                                )
                                Text(
                                    text = entry.hora,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(text = entry.texto, style = MaterialTheme.typography.bodySmall)
                            if (entry.tipo == "manual") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { onAddObsInDate(entry.fecha) }) {
                                        Text("➕ Añadir")
                                    }
                                    OutlinedButton(onClick = { onEditDiaryEntry(entry) }) {
                                        Text("✏️ Editar")
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

@Composable
private fun CalendarDayCell(
    dateISO: String,
    isToday: Boolean,
    isSelected: Boolean,
    hasNote: Boolean,
    items: List<CalendarTaskProjection>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val dayNumber = dateISO.takeLast(2).toIntOrNull()?.toString() ?: dateISO

    val bgColor = when {
        isSelected -> appColors.headerBg
        isToday -> appColors.blueContainer
        else -> appColors.cardSubtleBg
    }
    val borderColor = when {
        isSelected -> appColors.blue
        isToday -> appColors.blue
        else -> appColors.borderSubtle
    }
    val numColor = when {
        isSelected -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isToday || isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = dayNumber,
            style = MaterialTheme.typography.labelMedium,
            color = numColor,
        )

        if (hasNote) {
            Text(text = "📝", fontSize = 9.sp, lineHeight = 10.sp)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.take(4).forEach { proj ->
                val dotColor = when {
                    proj.task.completada -> appColors.green
                    proj.isProjected -> appColors.purple
                    proj.task.relevancia == TaskRelevance.ALTA -> appColors.red
                    proj.task.relevancia == TaskRelevance.MEDIA -> appColors.amber
                    else -> appColors.blue
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}
