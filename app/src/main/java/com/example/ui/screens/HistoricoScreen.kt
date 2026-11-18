package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskItem
import com.example.data.model.TaskRelevance
import com.example.ui.TareasUiState
import com.example.ui.components.TaskCard
import com.example.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoricoScreen(
    uiState: TareasUiState,
    onSearchChange: (String) -> Unit,
    onRelevanceChange: (TaskRelevance?) -> Unit,
    onExportExcelClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onReopenTask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onShareTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
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
                    OutlinedTextField(
                        value = uiState.filters.historySearch,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Buscar en histórico...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                    Text(
                        text = "Mostrando ${historyList.size} de ${uiState.completedTasks.size} realizadas",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
