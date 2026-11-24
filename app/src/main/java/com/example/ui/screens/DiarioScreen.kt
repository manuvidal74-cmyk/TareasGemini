package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DiaryEntry
import com.example.ui.TareasUiState
import com.example.ui.components.CompactExpandableSearchField
import com.example.ui.components.SearchFilterButton
import com.example.ui.components.StatusPill
import com.example.ui.components.isoToUtcMillis
import com.example.ui.components.utcMillisToIso
import com.example.ui.theme.LocalAppColors
import com.example.util.DateUtils

private data class DiaryDayGroup(
    val fecha: String,
    val quickCalendarNote: String,
    val manualEntry: DiaryEntry?,
    val completedEntries: List<DiaryEntry>,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiarioScreen(
    uiState: TareasUiState,
    onDateFilterChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleFiltersExpanded: () -> Unit,
    onClearFilters: () -> Unit,
    onAddObservation: (String) -> Unit,
    onEditObservation: (DiaryEntry) -> Unit,
    onDeleteObservation: (DiaryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var focusSearchOnExpand by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dayGroups = remember(
        uiState.diaryEntries,
        uiState.dailyNotes,
        uiState.filters.diaryDateFilter,
        uiState.filters.diarySearch,
    ) {
        val allDates = (uiState.diaryEntries.map { it.fecha } +
            uiState.dailyNotes.filterValues { it.isNotBlank() }.keys)
            .distinct()
            .sortedDescending()

        val filteredByDate = if (uiState.filters.diaryDateFilter.isNotBlank()) {
            allDates.filter { it == uiState.filters.diaryDateFilter }
        } else {
            allDates
        }

        val q = uiState.filters.diarySearch.trim().lowercase()

        filteredByDate.mapNotNull { fecha ->
            var itemsForDate = uiState.diaryEntries.filter { it.fecha == fecha }
            var calNote = uiState.dailyNotes[fecha]?.trim().orEmpty()

            if (q.isNotEmpty()) {
                itemsForDate = itemsForDate.filter {
                    it.texto.lowercase().contains(q) || it.tituloTarea.lowercase().contains(q)
                }
                if (!calNote.lowercase().contains(q)) {
                    calNote = ""
                }
                if (itemsForDate.isEmpty() && calNote.isEmpty()) return@mapNotNull null
            }

            DiaryDayGroup(
                fecha = fecha,
                quickCalendarNote = calNote,
                manualEntry = itemsForDate.firstOrNull { it.tipo == "manual" },
                completedEntries = itemsForDate
                    .filter { it.tipo == "completada" }
                    .sortedByDescending { it.hora },
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("diario_screen"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "diario_header_card") {
            val hasDateFilter = uiState.filters.diaryDateFilter.isNotBlank()
            val hasSearch = uiState.filters.diarySearch.isNotBlank()
            val activeCount = (if (hasDateFilter) 1 else 0) + (if (hasSearch) 1 else 0)
            val hasAnyActive = activeCount > 0

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
                            text = "📖 Agenda Diario",
                            style = MaterialTheme.typography.titleMedium,
                            color = appColors.headingText,
                        )
                        Button(
                            onClick = {
                                val targetDate = uiState.filters.diaryDateFilter.ifBlank { DateUtils.todayISO() }
                                onAddObservation(targetDate)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_diary_note_button"),
                        ) {
                            Text("➕ Añadir nota")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusPill(
                                text = "✅ Esta semana: ${uiState.completedThisWeekCount}",
                                containerColor = appColors.greenContainer,
                                contentColor = appColors.green,
                            )
                            StatusPill(
                                text = "🔥 Mejor racha: ${uiState.bestDailyStreak}d",
                                containerColor = appColors.amberContainer,
                                contentColor = appColors.amber,
                            )
                        }

                        SearchFilterButton(
                            expanded = uiState.filters.diaryFiltersExpanded,
                            activeCount = activeCount,
                            hasActiveSearch = hasSearch,
                            onClick = {
                                if (!uiState.filters.diaryFiltersExpanded) {
                                    focusSearchOnExpand = true
                                } else {
                                    keyboardController?.hide()
                                }
                                onToggleFiltersExpanded()
                            },
                            modifier = Modifier.testTag("diario_toggle_search_button"),
                        )
                    }

                    if (!uiState.filters.diaryFiltersExpanded && hasAnyActive) {
                        val activeSummary = buildList {
                            if (hasDateFilter) add("📅 ${DateUtils.formatDateES(uiState.filters.diaryDateFilter)}")
                            if (hasSearch) add("🔍 \"${uiState.filters.diarySearch.trim()}\"")
                        }.joinToString(" · ")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = activeSummary,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onClearFilters,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Todo")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.filters.diaryFiltersExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        LaunchedEffect(focusSearchOnExpand) {
                            if (focusSearchOnExpand) {
                                focusSearchOnExpand = false
                                try {
                                    searchFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = appColors.borderSubtle)

                            CompactExpandableSearchField(
                                query = uiState.filters.diarySearch,
                                onQueryChange = onSearchChange,
                                placeholder = "Buscar en notas, tareas o calendario...",
                                focusRequester = searchFocusRequester,
                                modifier = Modifier.testTag("search_diario_input"),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.width(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.filters.diaryDateFilter.isBlank()) "Filtrar por fecha"
                                        else DateUtils.formatDateES(uiState.filters.diaryDateFilter),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                OutlinedButton(
                                    onClick = onClearFilters,
                                    enabled = hasAnyActive,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text(stringResource(R.string.action_clear_all))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (dayGroups.isEmpty()) {
            item(key = "diario_empty") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No hay registros en el Diario con los filtros actuales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            items(dayGroups, key = { it.fecha }) { group ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, appColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "📅 ${DateUtils.formatDateES(group.fecha)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = appColors.headingText,
                            )
                            OutlinedButton(
                                onClick = { onAddObservation(group.fecha) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("➕ Añadir nota")
                            }
                        }

                        if (group.quickCalendarNote.isNotEmpty()) {
                            HorizontalDivider(color = appColors.borderSubtle)
                            Surface(
                                color = appColors.blueContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "📝 Nota fija del calendario:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = appColors.blue,
                                    )
                                    Text(
                                        text = group.quickCalendarNote,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        group.manualEntry?.let { manual ->
                            HorizontalDivider(color = appColors.borderSubtle)
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = appColors.cardSubtleBg,
                                    border = BorderStroke(1.dp, appColors.borderSubtle),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = manual.texto,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(onClick = { onAddObservation(group.fecha) }) {
                                        Text("➕ Añadir")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(onClick = { onEditObservation(manual) }) {
                                        Text("✏️ Editar todo")
                                    }
                                    IconButton(onClick = { onDeleteObservation(manual) }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Eliminar apunte",
                                            tint = appColors.red,
                                        )
                                    }
                                }
                            }
                        }

                        if (group.completedEntries.isNotEmpty()) {
                            HorizontalDivider(color = appColors.borderSubtle)
                            Surface(
                                color = appColors.bannerOkBg,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "TAREAS COMPLETADAS ESTE DÍA (${group.completedEntries.size}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appColors.green,
                                    )
                                    group.completedEntries.forEachIndexed { idx, entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = "✓ ${entry.tituloTarea}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = entry.texto,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                StatusPill(
                                                    text = entry.hora,
                                                    containerColor = appColors.greenContainer,
                                                    contentColor = appColors.green,
                                                )
                                                IconButton(onClick = { onEditObservation(entry) }) {
                                                    Icon(Icons.Outlined.Edit, contentDescription = "Editar registro")
                                                }
                                            }
                                        }
                                        if (idx < group.completedEntries.lastIndex) {
                                            HorizontalDivider(color = appColors.green.copy(alpha = 0.2f))
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

    if (showDatePicker) {
        val initDate = uiState.filters.diaryDateFilter.ifBlank { DateUtils.todayISO() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(initDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateFilterChange(utcMillisToIso(it)) }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = pickerState) }
    }
}
