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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TaskItem
import com.example.data.model.TaskRelevance
import com.example.ui.TareasUiState
import com.example.ui.components.CompactExpandableSearchField
import com.example.ui.components.SearchFilterButton
import com.example.ui.components.TaskCard
import com.example.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoricoScreen(
    uiState: TareasUiState,
    onSearchChange: (String) -> Unit,
    onRelevanceChange: (TaskRelevance?) -> Unit,
    onToggleFiltersExpanded: () -> Unit,
    onClearFilters: () -> Unit,
    onExportExcelClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onReopenTask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onShareTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    var focusSearchOnExpand by remember { mutableStateOf(false) }
    val historyList = uiState.filteredHistoryTasks

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("historico_screen"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Stats Row
        item(key = "stats_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBox(
                    value = uiState.completedTasks.size.toString(),
                    label = "Realizadas",
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = uiState.completedThisWeekCount.toString(),
                    label = "Semana",
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = uiState.completedThisMonthCount.toString(),
                    label = "Mes",
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = "🔥${uiState.bestDailyStreak}d",
                    label = "Racha máx.",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Filter & Export card
        item(key = "history_filters") {
            val hasSearch = uiState.filters.historySearch.isNotBlank()
            val hasRelevance = uiState.filters.historyRelevance != null
            val activeCount = (if (hasSearch) 1 else 0) + (if (hasRelevance) 1 else 0)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mostrando ${historyList.size} de ${uiState.completedTasks.size} realizadas",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!uiState.filters.historyFiltersExpanded && hasAnyActive) {
                                val summary = buildList {
                                    if (hasSearch) add("\"${uiState.filters.historySearch.trim()}\"")
                                    uiState.filters.historyRelevance?.let { add(it.label) }
                                }.joinToString(" · ")
                                Text(
                                    text = "Filtro: $summary",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (!uiState.filters.historyFiltersExpanded && hasAnyActive) {
                            TextButton(
                                onClick = onClearFilters,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(stringResource(R.string.action_clear_all))
                            }
                        }

                        SearchFilterButton(
                            expanded = uiState.filters.historyFiltersExpanded,
                            activeCount = activeCount,
                            hasActiveSearch = hasSearch,
                            onClick = {
                                if (!uiState.filters.historyFiltersExpanded) {
                                    focusSearchOnExpand = true
                                } else {
                                    keyboardController?.hide()
                                }
                                onToggleFiltersExpanded()
                            },
                            modifier = Modifier.testTag("historico_toggle_search_button"),
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.filters.historyFiltersExpanded,
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
                                query = uiState.filters.historySearch,
                                onQueryChange = onSearchChange,
                                placeholder = "Buscar en histórico...",
                                focusRequester = searchFocusRequester,
                                modifier = Modifier.testTag("search_historico_input"),
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilterChip(
                                    selected = uiState.filters.historyRelevance == null,
                                    onClick = { onRelevanceChange(null) },
                                    label = { Text("Todas") },
                                )
                                TaskRelevance.entries.forEach { rel ->
                                    FilterChip(
                                        selected = uiState.filters.historyRelevance == rel,
                                        onClick = { onRelevanceChange(rel) },
                                        label = { Text(rel.label) },
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onExportExcelClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.green,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                        ) {
                            Text("📊 Exportar consolidado Excel")
                        }
                        OutlinedButton(
                            onClick = onClearHistoryClick,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Vaciar histórico", tint = appColors.red)
                        }
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            item(key = "history_empty") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No hay tareas realizadas con estos filtros.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            items(historyList, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onCompleteClick = {},
                    onPostponeOneDay = {},
                    onPostponeSevenDays = {},
                    onEditClick = { onEditTask(task) },
                    onDeleteClick = { onDeleteTask(task) },
                    onReopenClick = { onReopenTask(task) },
                    onToggleChecklistLine = {},
                    onGoogleCalendarClick = {},
                    onGoogleTasksClick = {},
                    onShareClick = { onShareTask(task) },
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, appColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = appColors.headingText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
