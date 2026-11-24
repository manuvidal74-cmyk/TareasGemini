package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudSyncState
import com.example.data.model.AppSettings
import com.example.data.model.AppTab
import com.example.data.model.CalendarTaskProjection
import com.example.data.model.CalendarViewMode
import com.example.data.model.DiaryEntry
import com.example.data.model.SortOrder
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.data.model.TemaMode
import com.example.data.repository.CompletionUndoInfo
import com.example.data.repository.TareasRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationScheduler
import com.example.util.DateUtils
import com.example.util.ExternalIntents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilterState(
    val searchPendientes: String = "",
    val filterRelevance: TaskRelevance? = null,
    val filterType: TaskType? = null,
    val filterRecurrence: String = "todas", // "todas" | "repetitivas" | "diaria" | "semanal" | "mensual" | "anual" | "ninguna"
    val filterCategory: String = "",
    val sortOrder: SortOrder = SortOrder.FECHA,
    val filtersExpanded: Boolean = false,
    val diaryDateFilter: String = "",
    val diarySearch: String = "",
    val diaryFiltersExpanded: Boolean = false,
    val historySearch: String = "",
    val historyRelevance: TaskRelevance? = null,
    val historyFiltersExpanded: Boolean = false,
)

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MES,
    val cursorDateISO: String = DateUtils.todayISO(),
    val selectedDateISO: String = DateUtils.todayISO(),
    val userOverrodeViewMode: Boolean = false,
)

data class SnackbarMessage(
    val message: String,
    val canUndo: Boolean = false,
)

sealed interface UndoAction {
    data class Completed(val info: CompletionUndoInfo) : UndoAction
    data class Deleted(val task: TaskItem) : UndoAction
}

data class TareasUiState(
    val tasks: List<TaskItem> = emptyList(),
    val dailyNotes: Map<String, String> = emptyMap(),
    val diaryEntries: List<DiaryEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val filters: FilterState = FilterState(),
    val calendar: CalendarUiState = CalendarUiState(),
    val currentTab: AppTab = AppTab.PENDIENTES,
    val isLoading: Boolean = true,
) {
    val activeTasks: List<TaskItem>
        get() = tasks.filter { !it.completada }

    val completedTasks: List<TaskItem>
        get() = tasks.filter { it.completada }

    val dailyTasks: List<TaskItem>
        get() = activeTasks.filter { it.repeticion == TaskRecurrence.DIARIA }
            .sortedWith(compareBy<TaskItem> { it.hora.ifBlank { "23:59" } }.thenByDescending { it.relevancia.weight })

    val filteredDailyTasks: List<TaskItem>
        get() {
            val q = filters.searchPendientes.trim().lowercase()
            return dailyTasks.filter { t ->
                val relOk = filters.filterRelevance == null || t.relevancia == filters.filterRelevance
                val typeOk = filters.filterType == null || t.tipo == filters.filterType
                val catOk = filters.filterCategory.isBlank() || t.categoria.equals(filters.filterCategory, ignoreCase = true)
                val textOk = q.isEmpty() ||
                    t.titulo.lowercase().contains(q) ||
                    t.notas.lowercase().contains(q) ||
                    t.categoria.lowercase().contains(q)
                relOk && typeOk && catOk && textOk
            }
        }

    val dailyCompletedTodayCount: Int
        get() {
            val today = DateUtils.todayISO()
            val doneFromLog = completedTasks.count {
                it.repeticion == TaskRecurrence.DIARIA &&
                    (it.fechaCompletada?.startsWith(today) == true)
            }
            val activeScheduledLater = dailyTasks.count { it.ultimaFechaCompletadaDiaria == today }
            return maxOf(doneFromLog, activeScheduledLater)
        }

    val urgentAlerts: List<TaskItem>
        get() {
            val limitDays = settings.diasPreaviso
            return activeTasks
                .filter { DateUtils.diffDaysFromToday(it.fecha) <= limitDays }
                .sortedBy { it.fecha }
        }

    val availableCategories: List<String>
        get() = tasks.mapNotNull { it.categoria.trim().takeIf { c -> c.isNotEmpty() } }
            .distinct()
            .sorted()

    val filteredPendingTasks: List<TaskItem>
        get() {
            val q = filters.searchPendientes.trim().lowercase()
            val includeDailyInGeneral = filters.filterRecurrence == "diaria" || filters.filterRecurrence == "repetitivas"
            return activeTasks
                .filter { t ->
                    if (!includeDailyInGeneral && t.repeticion == TaskRecurrence.DIARIA) {
                        return@filter false
                    }
                    val relOk = filters.filterRelevance == null || t.relevancia == filters.filterRelevance
                    val typeOk = filters.filterType == null || t.tipo == filters.filterType
                    val recOk = when (filters.filterRecurrence) {
                        "todas" -> true
                        "repetitivas" -> t.repeticion != TaskRecurrence.NINGUNA
                        else -> t.repeticion.storageValue == filters.filterRecurrence
                    }
                    val catOk = filters.filterCategory.isBlank() || t.categoria.equals(filters.filterCategory, ignoreCase = true)
                    val textOk = q.isEmpty() ||
                        t.titulo.lowercase().contains(q) ||
                        t.notas.lowercase().contains(q) ||
                        t.categoria.lowercase().contains(q)
                    relOk && typeOk && recOk && catOk && textOk
                }
                .sortedWith { a, b ->
                    if (filters.sortOrder == SortOrder.RELEVANCIA) {
                        val relCmp = b.relevancia.weight.compareTo(a.relevancia.weight)
                        if (relCmp != 0) return@sortedWith relCmp
                    }
                    val dateA = (a.fecha.ifBlank { "9999-99-99" }) + (a.hora.ifBlank { "23:59" })
                    val dateB = (b.fecha.ifBlank { "9999-99-99" }) + (b.hora.ifBlank { "23:59" })
                    val dateCmp = dateA.compareTo(dateB)
                    if (dateCmp != 0) dateCmp else a.titulo.compareTo(b.titulo, ignoreCase = true)
                }
        }

    val activeFilterCount: Int
        get() {
            var count = 0
            if (filters.filterRelevance != null) count++
            if (filters.filterType != null) count++
            if (filters.filterRecurrence != "todas") count++
            if (filters.filterCategory.isNotBlank()) count++
            if (filters.sortOrder != SortOrder.FECHA) count++
            return count
        }

    val filteredHistoryTasks: List<TaskItem>
        get() {
            val q = filters.historySearch.trim().lowercase()
            return completedTasks
                .filter { t ->
                    val relOk = filters.historyRelevance == null || t.relevancia == filters.historyRelevance
                    val textOk = q.isEmpty() ||
                        t.titulo.lowercase().contains(q) ||
                        t.notas.lowercase().contains(q) ||
                        t.notaCierre.lowercase().contains(q) ||
                        t.categoria.lowercase().contains(q)
                    relOk && textOk
                }
                .sortedByDescending { it.fechaCompletada ?: it.fecha }
        }

    val completedThisMonthCount: Int
        get() {
            val prefix = DateUtils.currentYearMonthPrefix()
            return completedTasks.count {
                (it.fechaCompletada?.startsWith(prefix) == true) || it.fecha.startsWith(prefix)
            }
        }

    val completedThisWeekCount: Int
        get() = completedTasks.count { t ->
            val datePart = t.fechaCompletada?.take(10) ?: t.fecha
            DateUtils.isInCurrentWeek(datePart)
        }

    val bestDailyStreak: Int
        get() = tasks.maxOfOrNull { it.rachaDiaria } ?: 0

    val backupWarningMessage: String?
        get() {
            if (tasks.isEmpty() && diaryEntries.isEmpty()) return null
            val last = settings.ultimoBackup
            if (last.isNullOrBlank()) {
                return if (tasks.size >= 3) {
                    "Aún no has guardado ninguna copia de seguridad (.json)."
                } else null
            }
            val lastDatePart = last.take(10)
            val diff = DateUtils.diffDaysBetween(lastDatePart, DateUtils.todayISO())
            return if (diff >= 15) {
                "Han pasado $diff días desde tu última copia de seguridad."
            } else null
        }

    fun getItemsForDate(dateISO: String): List<CalendarTaskProjection> {
        val showCompleted = settings.mostrarRealizadasCal
        return tasks.mapNotNull { t ->
            if (!showCompleted && t.completada) return@mapNotNull null
            when {
                t.fecha == dateISO -> CalendarTaskProjection(t, isProjected = false, displayDate = dateISO)
                DateUtils.matchesFutureRecurrence(t, dateISO) -> CalendarTaskProjection(t, isProjected = true, displayDate = dateISO)
                else -> null
            }
        }.sortedWith(
            compareBy<CalendarTaskProjection> { it.task.completada }
                .thenBy { it.isProjected }
                .thenByDescending { it.task.relevancia.weight }
        )
    }
}

sealed interface ExportDocumentRequest {
    data class SaveExcel(val fileName: String, val xmlContent: String) : ExportDocumentRequest
    data class SaveBackupJson(val fileName: String, val jsonContent: String) : ExportDocumentRequest
}

class TareasViewModel(
    private val repository: TareasRepository,
    private val currentUserId: String,
    initialHasNotifPermission: Boolean,
    initialCanExactAlarms: Boolean,
) : ViewModel() {

    private val _filters = MutableStateFlow(FilterState())
    private val _calendar = MutableStateFlow(CalendarUiState())
    private val _currentTab = MutableStateFlow(AppTab.PENDIENTES)

    private val _toastEvents = MutableSharedFlow<SnackbarMessage>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<SnackbarMessage> = _toastEvents.asSharedFlow()

    private val _exportEvents = MutableSharedFlow<ExportDocumentRequest>(extraBufferCapacity = 4)
    val exportEvents: SharedFlow<ExportDocumentRequest> = _exportEvents.asSharedFlow()

    private val _hasNotificationPermission = MutableStateFlow(initialHasNotifPermission)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _canExactAlarms = MutableStateFlow(initialCanExactAlarms)
    val canExactAlarms: StateFlow<Boolean> = _canExactAlarms.asStateFlow()

    val cloudSyncState: StateFlow<CloudSyncState> = repository.cloudSyncStateFlow

    private var pendingUndo: UndoAction? = null

    val uiState: StateFlow<TareasUiState> = combine(
        repository.tasksFlow,
        repository.dailyNotesFlow,
        repository.diaryEntriesFlow,
        repository.settingsFlow,
        combine(_filters, _calendar, _currentTab) { f, c, t -> Triple(f, c, t) },
    ) { tasks, notes, diary, settings, (filters, cal, tab) ->
        val resolvedCal = if (cal.userOverrodeViewMode) cal else cal.copy(viewMode = settings.vistaDefecto)
        TareasUiState(
            tasks = tasks,
            dailyNotes = notes,
            diaryEntries = diary,
            settings = settings,
            filters = filters,
            calendar = resolvedCal,
            currentTab = tab,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TareasUiState(),
    )

    init {
        repository.ensureNotificationChannels()
        repository.startCloudSyncForUser(currentUserId, viewModelScope)
        viewModelScope.launch {
            repository.rescheduleAllNotifications()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val result = repository.forceCloudSync()
            if (result.isSuccess) {
                emitToast("Sincronización con la nube completada")
            } else {
                emitToast(result.exceptionOrNull()?.message ?: "No se pudo sincronizar ahora")
            }
        }
    }

    suspend fun prepareSignOut() {
        repository.clearSessionBeforeSignOut()
    }

    fun refreshNotificationPermission(context: Context) {
        _hasNotificationPermission.value = NotificationHelper.hasNotificationPermission(context)
        _canExactAlarms.value = NotificationScheduler.canScheduleExactAlarms(context)
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // Filter updates
    fun updateSearchPendientes(query: String) {
        _filters.update { it.copy(searchPendientes = query) }
    }

    fun toggleFiltersExpanded() {
        _filters.update { it.copy(filtersExpanded = !it.filtersExpanded) }
    }

    fun updateFilterRelevance(rel: TaskRelevance?) {
        _filters.update { it.copy(filterRelevance = rel) }
    }

    fun updateFilterType(type: TaskType?) {
        _filters.update { it.copy(filterType = type) }
    }

    fun updateFilterRecurrence(rec: String) {
        _filters.update { it.copy(filterRecurrence = rec) }
    }

    fun updateFilterCategory(category: String) {
        _filters.update { it.copy(filterCategory = category) }
    }

    fun updateSortOrder(order: SortOrder) {
        _filters.update { it.copy(sortOrder = order) }
    }

    fun resetFilters() {
        _filters.update {
            it.copy(
                searchPendientes = "",
                filterRelevance = null,
                filterType = null,
                filterRecurrence = "todas",
                filterCategory = "",
                sortOrder = SortOrder.FECHA,
            )
        }
        emitToast("Filtros y orden restablecidos")
    }

    fun updateDiaryDateFilter(dateISO: String) {
        _filters.update { it.copy(diaryDateFilter = dateISO) }
    }

    fun updateDiarySearch(query: String) {
        _filters.update { it.copy(diarySearch = query) }
    }

    fun toggleDiaryFiltersExpanded() {
        _filters.update { it.copy(diaryFiltersExpanded = !it.diaryFiltersExpanded) }
    }

    fun clearDiaryFilters() {
        _filters.update { it.copy(diaryDateFilter = "", diarySearch = "") }
    }

    fun updateHistorySearch(query: String) {
        _filters.update { it.copy(historySearch = query) }
    }

    fun updateHistoryRelevance(rel: TaskRelevance?) {
        _filters.update { it.copy(historyRelevance = rel) }
    }

    fun toggleHistoryFiltersExpanded() {
        _filters.update { it.copy(historyFiltersExpanded = !it.historyFiltersExpanded) }
    }

    fun clearHistoryFilters() {
        _filters.update { it.copy(historySearch = "", historyRelevance = null) }
    }

    // Calendar navigation
    fun setCalendarViewMode(mode: CalendarViewMode) {
        _calendar.update { it.copy(viewMode = mode, userOverrodeViewMode = true) }
    }

    fun selectCalendarDate(dateISO: String) {
        _calendar.update { it.copy(selectedDateISO = dateISO, cursorDateISO = dateISO) }
    }

    fun shiftCalendarPeriod(delta: Int) {
        _calendar.update { cal ->
            val newCursor = when (cal.viewMode) {
                CalendarViewMode.MES -> DateUtils.shiftMonth(cal.cursorDateISO, delta)
                CalendarViewMode.SEMANA -> DateUtils.addDays(cal.cursorDateISO, delta * 7)
                CalendarViewMode.AGENDA -> cal.cursorDateISO
            }
            cal.copy(cursorDateISO = newCursor)
        }
    }

    fun jumpCalendarToday() {
        val today = DateUtils.todayISO()
        _calendar.update { it.copy(cursorDateISO = today, selectedDateISO = today) }
    }

    // Task operations
    fun saveTask(task: TaskItem) {
        val isEdit = task.id.isNotBlank()
        viewModelScope.launch {
            repository.saveTask(task)
            val msg = when {
                isEdit -> "Registro actualizado"
                task.notificacionActiva -> "Guardado con notificación local programada"
                else -> "Guardado correctamente"
            }
            emitToast(msg)
        }
    }

    fun duplicateTask(task: TaskItem) {
        viewModelScope.launch {
            repository.duplicateTask(task)
            emitToast("Tarea duplicada")
        }
    }

    fun toggleChecklistLine(taskId: String, lineIndex: Int) {
        viewModelScope.launch {
            repository.toggleChecklistLine(taskId, lineIndex)
        }
    }

    fun postponeTask(taskId: String, days: Int) {
        viewModelScope.launch {
            val newDate = repository.postponeTask(taskId, days)
            if (newDate != null) {
                emitToast("Pospuesta al ${DateUtils.formatDateES(newDate)}")
            }
        }
    }

    fun completeTask(taskId: String, notaCierre: String, scheduleNext: Boolean) {
        viewModelScope.launch {
            val undoInfo = repository.completeTaskWithUndo(taskId, notaCierre, scheduleNext)
            if (undoInfo != null) {
                pendingUndo = UndoAction.Completed(undoInfo)
                val msg = if (undoInfo.nextDate != null) {
                    "Completada. Próxima: ${DateUtils.formatDateES(undoInfo.nextDate)}"
                } else {
                    "Tarea completada y registrada en el Diario"
                }
                _toastEvents.emit(SnackbarMessage(msg, canUndo = true))
            }
        }
    }

    fun uncheckDailyTaskToday(taskId: String) {
        viewModelScope.launch {
            repository.uncheckDailyTaskToday(taskId)
            emitToast("Tarea diaria marcada como pendiente hoy")
        }
    }

    fun reopenTask(taskId: String) {
        viewModelScope.launch {
            repository.reopenTask(taskId)
            emitToast("Devuelta a pendientes")
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val deleted = repository.deleteTask(taskId)
            if (deleted != null) {
                pendingUndo = UndoAction.Deleted(deleted)
                _toastEvents.emit(SnackbarMessage("Tarea eliminada", canUndo = true))
            } else {
                emitToast("Tarea eliminada")
            }
        }
    }

    fun undoLastAction() {
        val action = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch {
            when (action) {
                is UndoAction.Completed -> repository.undoCompletion(action.info)
                is UndoAction.Deleted -> repository.restoreDeletedTask(action.task)
            }
            emitToast("Acción deshecha")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
            emitToast("Histórico vaciado")
        }
    }

    fun transferToGoogleCalendar(context: Context, task: TaskItem) {
        viewModelScope.launch {
            repository.markTransferredToGoogle(task.id)
            ExternalIntents.openGoogleCalendar(context, task)
            emitToast("Abriendo Google Calendar con los datos listos")
        }
    }

    fun transferToGoogleTasks(context: Context, task: TaskItem) {
        viewModelScope.launch {
            repository.markTransferredToGoogle(task.id)
            ExternalIntents.openGoogleTasks(context, task)
            emitToast("Datos copiados al portapapeles para Google Tasks")
        }
    }

    // Notes & Diary
    fun saveQuickDailyNote(fecha: String, text: String) {
        viewModelScope.launch {
            repository.saveQuickDailyNote(fecha, text)
        }
    }

    fun addCumulativeDiaryNote(fecha: String, text: String) {
        viewModelScope.launch {
            repository.addCumulativeDiaryNote(fecha, text)
            emitToast("Observación añadida al Diario")
        }
    }

    fun updateDiaryEntry(id: String, fecha: String, text: String) {
        viewModelScope.launch {
            repository.updateDiaryEntry(id, fecha, text)
            emitToast("Observaciones del día actualizadas")
        }
    }

    fun deleteDiaryEntry(id: String) {
        viewModelScope.launch {
            repository.deleteDiaryEntry(id)
            emitToast("Apunte del diario eliminado")
        }
    }

    // Settings & Notifications
    fun updateTema(tema: TemaMode) {
        viewModelScope.launch {
            repository.updateTema(tema)
            emitToast("Tema actualizado")
        }
    }

    fun updateVistaDefecto(vista: CalendarViewMode) {
        viewModelScope.launch {
            repository.updateVistaDefecto(vista)
            _calendar.update { it.copy(viewMode = vista, userOverrodeViewMode = false) }
            emitToast("Preferencia guardada")
        }
    }

    fun updateDiasPreaviso(dias: Int) {
        viewModelScope.launch {
            repository.updateDiasPreaviso(dias)
            emitToast("Preferencia guardada")
        }
    }

    fun updateMostrarRealizadasCal(mostrar: Boolean) {
        viewModelScope.launch {
            repository.updateMostrarRealizadasCal(mostrar)
            emitToast("Preferencia guardada")
        }
    }

    fun updateNotificacionesGlobales(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotificacionesGlobales(enabled)
            emitToast(if (enabled) "Notificaciones locales activadas" else "Notificaciones locales desactivadas")
        }
    }

    fun updateResumenDiario(enabled: Boolean, hora: String? = null) {
        viewModelScope.launch {
            repository.updateResumenDiario(enabled, hora)
            emitToast("Resumen diario actualizado")
        }
    }

    fun triggerTestNotification(context: Context) {
        if (!NotificationHelper.hasNotificationPermission(context)) {
            emitToast("Concede el permiso de notificaciones para probarlas")
            return
        }
        NotificationHelper.showTestNotification(context)
        emitToast("Notificación local de prueba enviada")
    }

    fun loadSampleData() {
        viewModelScope.launch {
            repository.loadSampleData()
            emitToast("Tareas de ejemplo añadidas")
        }
    }

    // Export / Import
    fun requestSaveExcel(fileNameInput: String) {
        viewModelScope.launch {
            val cleanName = fileNameInput.trim().ifBlank { "informe_tareas_diario_${DateUtils.todayISO()}.xls" }
                .let { if (it.endsWith(".xls", true) || it.endsWith(".xlsx", true)) it else "$it.xls" }
            val xml = repository.generateExcelXml()
            _exportEvents.emit(ExportDocumentRequest.SaveExcel(cleanName, xml))
        }
    }

    fun shareExcel(context: Context, fileNameInput: String) {
        viewModelScope.launch {
            val cleanName = fileNameInput.trim().ifBlank { "informe_tareas_diario_${DateUtils.todayISO()}.xls" }
                .let { if (it.endsWith(".xls", true) || it.endsWith(".xlsx", true)) it else "$it.xls" }
            val xml = repository.generateExcelXml()
            ExternalIntents.shareExportedFile(context, cleanName, xml, "application/vnd.ms-excel")
        }
    }

    fun requestSaveBackupJson() {
        viewModelScope.launch {
            val json = repository.generateBackupJson()
            val fileName = "copia_tareasyavisos_${DateUtils.todayISO()}.json"
            _exportEvents.emit(ExportDocumentRequest.SaveBackupJson(fileName, json))
        }
    }

    fun shareBackupJson(context: Context) {
        viewModelScope.launch {
            val json = repository.generateBackupJson()
            val fileName = "copia_tareasyavisos_${DateUtils.todayISO()}.json"
            ExternalIntents.shareExportedFile(context, fileName, json, "application/json")
            emitToast("Copia JSON generada para compartir")
        }
    }

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            try {
                val (taskCount, diaryCount) = repository.restoreFromBackupJson(rawJson)
                emitToast("Copia restaurada: $taskCount tareas y $diaryCount apuntes")
            } catch (e: Exception) {
                emitToast("Error al leer el archivo JSON: ${e.message ?: "formato inválido"}")
            }
        }
    }

    fun emitToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(SnackbarMessage(message, canUndo = false))
        }
    }
}
