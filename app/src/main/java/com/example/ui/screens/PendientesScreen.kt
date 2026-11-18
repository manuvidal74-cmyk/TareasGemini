package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SortOrder
import com.example.data.model.TaskCategory
import com.example.data.model.TaskItem
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.ui.TareasUiState
import com.example.ui.components.DailyTasksSection
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskCard
import com.example.ui.theme.LocalAppColors
import com.example.util.DateUtils

private data class PendingDateGroup(
    val key: String,
    val title: String,
    val accentColor: Color,
    val containerColor: Color,
    val tasks: List<TaskItem>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendientesScreen(
    uiState: TareasUiState,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onBackupNow: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleFiltersExpanded: () -> Unit,
    onFilterRelevanceChange: (TaskRelevance?) -> Unit,
    onFilterTypeChange: (TaskType?) -> Unit,
    onFilterRecurrenceChange: (String) -> Unit,
    onFilterCategoryChange: (String) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onResetFilters: () -> Unit,
    onCompleteClick: (TaskItem) -> Unit,
    onQuickCompleteDaily: (TaskItem) -> Unit,
    onUndoCompleteDaily: (TaskItem) -> Unit,
    onAddDailyTask: () -> Unit,
    onPostponeTask: (String, Int) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDuplicateTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onToggleChecklistLine: (String, Int) -> Unit,
    onGoogleCalendarClick: (TaskItem) -> Unit,
    onGoogleTasksClick: (TaskItem) -> Unit,
    onShareClick: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val filteredList = uiState.filteredPendingTasks
    val filteredDaily = uiState.filteredDailyTasks
    val urgentAlerts = uiState.urgentAlerts
    val backupWarn = uiState.backupWarningMessage
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val categoryOptions = remember(uiState.availableCategories) {
        (TaskCategory.entries.map { it.storageValue } + uiState.availableCategories).distinct()
    }

    val groupedByDate = remember(filteredList, uiState.filters.sortOrder, appColors) {
        if (uiState.filters.sortOrder != SortOrder.FECHA) {
            emptyList()
        } else {
            val overdue = mutableListOf<TaskItem>()
            val today = mutableListOf<TaskItem>()
            val thisWeek = mutableListOf<TaskItem>()
            val upcoming = mutableListOf<TaskItem>()

            filteredList.forEach { task ->
                val diff = DateUtils.diffDaysFromToday(task.fecha)
                when {
                    diff < 0 -> overdue.add(task)
                    diff == 0 -> today.add(task)
                    diff in 1..7 -> thisWeek.add(task)
                    else -> upcoming.add(task)
                }
            }

            listOfNotNull(
                overdue.takeIf { it.isNotEmpty() }?.let {
                    PendingDateGroup("overdue", "🔴 Vencidas", appColors.red, appColors.redContainer, it)
                },
                today.takeIf { it.isNotEmpty() }?.let {
                    PendingDateGroup("today", "🟢 Para hoy", appColors.green, appColors.greenContainer, it)
                },
                thisWeek.takeIf { it.isNotEmpty() }?.let {
                    PendingDateGroup("week", "📅 Esta semana", appColors.blue, appColors.blueContainer, it)
                },
                upcoming.takeIf { it.isNotEmpty() }?.let {
                    PendingDateGroup("upcoming", "🗓️ Próximas", appColors.headingText, appColors.cardSubtleBg, it)
                },
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pendientes_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Notification Permission Banner if denied on Android 13+
        if (!hasNotificationPermission) {
            item(key = "perm_banner") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = appColors.amberContainer,
                    border = BorderStroke(1.dp, appColors.amber),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = appColors.amber,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Activa las notificaciones locales",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Necesarias para avisarte de tus tareas diarias y recordatorios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.testTag("grant_notifications_button"),
                        ) {
                            Text("Permitir")
                        }
                    }
                }
            }
        }

        // Backup reminder banner
        if (backupWarn != null) {
            item(key = "backup_banner") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = appColors.amberContainer,
                    border = BorderStroke(1.dp, appColors.amber),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "💾 $backupWarn",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onBackupNow) {
                            Text("Guardar .json")
                        }
                    }
                }
            }
        }

        // Urgent Alerts Banner
        item(key = "urgent_banner") {
            if (urgentAlerts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = appColors.bannerOkBg,
                    border = BorderStroke(1.dp, appColors.green.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = appColors.green)
                        Column {
                            Text(
                                "Todo al día",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Sin tareas vencidas ni avisos próximos (${uiState.settings.diasPreaviso}d).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = appColors.bannerAlertBg,
                    border = BorderStroke(1.dp, appColors.red.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "🔔 ${urgentAlerts.size} ${if (urgentAlerts.size == 1) "aviso prioritario" else "avisos prioritarios"}",
                            style = MaterialTheme.typography.titleSmall,
                            color = appColors.red,
                        )
                        urgentAlerts.take(4).forEachIndexed { idx, alertTask ->
                            val diff = DateUtils.diffDaysFromToday(alertTask.fecha)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alertTask.titulo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        when {
                                            diff < 0 -> StatusPill("Vencida (${kotlin.math.abs(diff)}d)", appColors.red, Color.White)
                                            diff == 0 -> StatusPill("Hoy", appColors.green, Color.White)
                                            else -> StatusPill("En ${diff}d", appColors.amberContainer, appColors.amber)
                                        }
                                        Text(
                                            text = "${DateUtils.formatDateES(alertTask.fecha)}${if (alertTask.hora.isNotBlank()) " ${alertTask.hora}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onPostponeTask(alertTask.id, 1) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("+1d", style = MaterialTheme.typography.labelMedium)
                                }
                                Button(
                                    onClick = { onCompleteClick(alertTask) },
                                    colors = ButtonDefaults.buttonColors(containerColor = appColors.green, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text("✓ Hecho", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (idx < urgentAlerts.take(4).lastIndex) {
                                HorizontalDivider(color = appColors.red.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Daily Tasks Section
        item(key = "daily_tasks_card") {
            DailyTasksSection(
                dailyTasks = filteredDaily,
                completedTodayCount = uiState.dailyCompletedTodayCount,
                onQuickCompleteDaily = onQuickCompleteDaily,
                onUndoCompleteDaily = onUndoCompleteDaily,
                onEditDaily = onEditTask,
                onAddDailyTask = onAddDailyTask,
            )
        }

        // Search & Expandable Filters
        item(key = "search_filters_card") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, appColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.filters.searchPendientes,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Buscar en título, notas o categoría...") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (uiState.filters.searchPendientes.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Limpiar búsqueda")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_pendientes_input"),
                        )

                        OutlinedButton(
                            onClick = onToggleFiltersExpanded,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_filters_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Filtros", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            val count = uiState.activeFilterCount
                            Text(if (count > 0) "($count)" else "Filtros")
                        }
                    }

                    AnimatedVisibility(visible = uiState.filters.filtersExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = appColors.borderSubtle)

                            Text("Categoría", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = uiState.filters.filterCategory.isBlank(),
                                    onClick = { onFilterCategoryChange("") },
                                    label = { Text("Todas") },
                                )
                                categoryOptions.forEach { catName ->
                                    val emoji = TaskCategory.findByValue(catName)?.emoji ?: "🏷️"
                                    FilterChip(
                                        selected = uiState.filters.filterCategory.equals(catName, ignoreCase = true),
                                        onClick = {
                                            val next = if (uiState.filters.filterCategory.equals(catName, ignoreCase = true)) "" else catName
                                            onFilterCategoryChange(next)
                                        },
                                        label = { Text("$emoji $catName") },
                                    )
                                }
                            }

                            Text("Relevancia", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = uiState.filters.filterRelevance == null,
                                    onClick = { onFilterRelevanceChange(null) },
                                    label = { Text("Todas") },
                                )
                                TaskRelevance.entries.forEach { rel ->
                                    FilterChip(
                                        selected = uiState.filters.filterRelevance == rel,
                                        onClick = { onFilterRelevanceChange(rel) },
                                        label = { Text(rel.label) },
                                    )
                                }
                            }

                            Text("Tipo y Frecuencia", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = uiState.filters.filterType == null,
                                    onClick = { onFilterTypeChange(null) },
                                    label = { Text("Todos tipos") },
                                )
                                FilterChip(
                                    selected = uiState.filters.filterType == TaskType.TAREA,
                                    onClick = { onFilterTypeChange(TaskType.TAREA) },
                                    label = { Text("📌 Tareas") },
                                )
                                FilterChip(
                                    selected = uiState.filters.filterType == TaskType.AVISO,
                                    onClick = { onFilterTypeChange(TaskType.AVISO) },
                                    label = { Text("🔔 Avisos") },
                                )
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "todas" to "Todas frec.",
                                    "diaria" to "🔁 Diaria",
                                    "semanal" to "🔁 Semanal",
                                    "mensual" to "🔁 Mensual",
                                    "anual" to "🔁 Anual",
                                    "ninguna" to "1 vez",
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = uiState.filters.filterRecurrence == key,
                                        onClick = { onFilterRecurrenceChange(key) },
                                        label = { Text(label) },
                                    )
                                }
                            }

                            Text("Ordenar por", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortOrder.entries.forEach { ord ->
                                    FilterChip(
                                        selected = uiState.filters.sortOrder == ord,
                                        onClick = { onSortOrderChange(ord) },
                                        label = { Text(ord.label) },
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onResetFilters,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("↺ Restablecer filtros y orden")
                            }
                        }
                    }

                    Text(
                        text = "${filteredList.size} pendientes generales · ${filteredDaily.size} rutinas diarias arriba",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (filteredList.isEmpty()) {
            item(key = "empty_pending") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (filteredDaily.isNotEmpty() && uiState.activeFilterCount == 0 && uiState.filters.searchPendientes.isBlank()) {
                            "No hay más tareas puntuales pendientes. Tus rutinas diarias están arriba."
                        } else {
                            "No hay tareas pendientes con estos filtros."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else if (uiState.filters.sortOrder == SortOrder.FECHA) {
            groupedByDate.forEach { group ->
                val isCollapsed = collapsedGroups[group.key] == true
                item(key = "group_header_${group.key}") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = group.containerColor.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, group.accentColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { collapsedGroups[group.key] = !isCollapsed },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = group.accentColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                StatusPill(
                                    text = group.tasks.size.toString(),
                                    containerColor = group.accentColor,
                                    contentColor = Color.White,
                                )
                            }
                            Icon(
                                imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                contentDescription = if (isCollapsed) "Expandir grupo" else "Contraer grupo",
                                tint = group.accentColor,
                            )
                        }
                    }
                }
                if (!isCollapsed) {
                    items(group.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onCompleteClick = { onCompleteClick(task) },
                            onPostponeOneDay = { onPostponeTask(task.id, 1) },
                            onPostponeSevenDays = { onPostponeTask(task.id, 7) },
                            onEditClick = { onEditTask(task) },
                            onDuplicateClick = { onDuplicateTask(task) },
                            onDeleteClick = { onDeleteTask(task) },
                            onReopenClick = {},
                            onToggleChecklistLine = { idx -> onToggleChecklistLine(task.id, idx) },
                            onGoogleCalendarClick = { onGoogleCalendarClick(task) },
                            onGoogleTasksClick = { onGoogleTasksClick(task) },
                            onShareClick = { onShareClick(task) },
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onCompleteClick = { onCompleteClick(task) },
                    onPostponeOneDay = { onPostponeTask(task.id, 1) },
                    onPostponeSevenDays = { onPostponeTask(task.id, 7) },
                    onEditClick = { onEditTask(task) },
                    onDuplicateClick = { onDuplicateTask(task) },
                    onDeleteClick = { onDeleteTask(task) },
                    onReopenClick = {},
                    onToggleChecklistLine = { idx -> onToggleChecklistLine(task.id, idx) },
                    onGoogleCalendarClick = { onGoogleCalendarClick(task) },
                    onGoogleTasksClick = { onGoogleTasksClick(task) },
                    onShareClick = { onShareClick(task) },
                )
            }
        }
    }
}
