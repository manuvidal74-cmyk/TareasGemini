package com.example.data.repository

import android.content.Context
import com.example.data.cloud.CloudSyncManager
import com.example.data.cloud.CloudSyncState
import com.example.data.cloud.sanitizeFirestoreDocId
import com.example.data.local.AppDatabase
import com.example.data.local.DailyNoteEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.model.AppSettings
import com.example.data.model.CalendarViewMode
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.data.model.TemaMode
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationScheduler
import com.example.util.ChecklistUtils
import com.example.util.DateUtils
import com.example.util.ExcelExporter
import com.example.util.JsonBackupManager
import com.example.widget.TareasWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CompletionUndoInfo(
    val previousTask: TaskItem,
    val createdHistoryTaskId: String?,
    val createdDiaryEntryId: String,
    val nextDate: String?,
)

class TareasRepository(
    private val context: Context,
    private val cloudSyncManager: CloudSyncManager = CloudSyncManager(context),
) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val taskDao = db.taskDao()
    private val noteDao = db.dailyNoteDao()
    private val diaryDao = db.diaryEntryDao()
    private val settingsStore = SettingsDataStore(appContext)

    private var syncJob: Job? = null
    private var cloudWriteScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var verifiedRemoteBaseline = false

    val tasksFlow: Flow<List<TaskItem>> =
        taskDao.observeAllTasks().map { list -> list.map { it.toDomain() } }

    val dailyNotesFlow: Flow<Map<String, String>> =
        noteDao.observeAllNotes().map { list -> list.associate { it.fecha to it.texto } }

    val diaryEntriesFlow: Flow<List<DiaryEntry>> =
        diaryDao.observeAllEntries().map { list -> list.map { it.toDomain() } }

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow

    val cloudSyncStateFlow: StateFlow<CloudSyncState> = cloudSyncManager.syncState

    fun startCloudSyncForUser(uid: String, scope: CoroutineScope) {
        if (activeUserId == uid && syncJob?.isActive == true) return
        syncJob?.cancel()
        cloudWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        activeUserId = uid
        verifiedRemoteBaseline = false
        cloudSyncManager.markSyncStarted()

        syncJob = scope.launch {
            val previousUid = settingsStore.getLastSyncedUid()
            val isAccountSwitch = previousUid != null && previousUid != uid
            if (isAccountSwitch) {
                taskDao.getAllTasksOnce().forEach {
                    NotificationScheduler.cancelTaskNotification(appContext, it.id)
                }
                taskDao.clearAll()
                noteDao.clearAll()
                diaryDao.clearAll()
            }

            val localTasks = taskDao.getAllTasksOnce().map { it.toDomain() }
            val localNotes = noteDao.getAllNotesOnce().associate { it.fecha to it.texto }
            val localDiary = diaryDao.getAllEntriesOnce().map { it.toDomain() }

            val bootstrapResult = cloudSyncManager.bootstrapAndMergeOnSignIn(
                uid = uid,
                localTasks = localTasks,
                localNotes = localNotes,
                localDiary = localDiary,
                canUploadExistingLocalData = !isAccountSwitch,
            )
            verifiedRemoteBaseline = bootstrapResult.isSuccess
            settingsStore.setLastSyncedUid(uid)

            launch {
                cloudSyncManager.observeTasks(uid).collect { remoteTasks ->
                    if (remoteTasks.isNotEmpty() || verifiedRemoteBaseline) {
                        val currentLocal = taskDao.getAllTasksOnce().map { it.toDomain() }
                        if (currentLocal != remoteTasks) {
                            taskDao.replaceAll(remoteTasks.map { it.toEntity() })
                            rescheduleAllNotifications()
                            TareasWidgetProvider.requestRefresh(appContext)
                        }
                    }
                }
            }

            launch {
                cloudSyncManager.observeDailyNotes(uid).collect { remoteNotes ->
                    if (remoteNotes.isNotEmpty() || verifiedRemoteBaseline) {
                        val currentLocal = noteDao.getAllNotesOnce().associate { it.fecha to it.texto }
                        if (currentLocal != remoteNotes) {
                            noteDao.replaceAll(remoteNotes.map { DailyNoteEntity(it.key, it.value) })
                        }
                    }
                }
            }

            launch {
                cloudSyncManager.observeDiaryEntries(uid).collect { remoteDiary ->
                    if (remoteDiary.isNotEmpty() || verifiedRemoteBaseline) {
                        val currentLocal = diaryDao.getAllEntriesOnce().map { it.toDomain() }
                        if (currentLocal != remoteDiary) {
                            diaryDao.replaceAll(remoteDiary.map { it.toEntity() })
                        }
                    }
                }
            }

            if (bootstrapResult.isSuccess && taskDao.getAllTasksOnce().isEmpty()) {
                ensureInitialSamplesIfNeeded()
            }
        }
    }

    suspend fun forceCloudSync(): Result<Unit> {
        val uid = activeUserId
            ?: return Result.failure(IllegalStateException("Inicia sesión con Google para sincronizar"))
        val localTasks = taskDao.getAllTasksOnce().map { it.toDomain() }
        val localNotes = noteDao.getAllNotesOnce().associate { it.fecha to it.texto }
        val localDiary = diaryDao.getAllEntriesOnce().map { it.toDomain() }
        val result = cloudSyncManager.forceSyncNow(uid, localTasks, localNotes, localDiary)
        if (result.isSuccess) {
            verifiedRemoteBaseline = true
        }
        return result
    }

    suspend fun clearSessionBeforeSignOut() = withContext(NonCancellable) {
        activeUserId = null
        verifiedRemoteBaseline = false
        syncJob?.cancel()
        syncJob = null
        cloudWriteScope.coroutineContext[Job]?.cancel()
        cloudSyncManager.resetStateOnSignOut()
        taskDao.getAllTasksOnce().forEach {
            NotificationScheduler.cancelTaskNotification(appContext, it.id)
        }
        taskDao.clearAll()
        noteDao.clearAll()
        diaryDao.clearAll()
        settingsStore.setLastSyncedUid(null)
        TareasWidgetProvider.requestRefresh(appContext)
    }

    private fun enqueueCloudWrite(block: suspend (String) -> Unit) {
        val uid = activeUserId ?: return
        cloudWriteScope.launch {
            if (activeUserId == uid) {
                block(uid)
            }
        }
    }

    suspend fun ensureInitialSamplesIfNeeded() {
        if (settingsStore.isInitialSeeded()) return
        val existing = taskDao.getAllTasksOnce()
        if (existing.isEmpty()) {
            loadSampleData()
        }
        settingsStore.markInitialSeeded()
    }

    suspend fun loadSampleData() {
        val today = DateUtils.todayISO()
        val inTwoDays = DateUtils.addDays(today, 2)
        val nowHour = DateUtils.nowHHMM()
        val nowIso = DateUtils.nowISO8601()

        val samples = listOf(
            TaskItem(
                id = "ej-${System.currentTimeMillis()}-1",
                titulo = "[Ejemplo] Rutina diaria: revisar correo y agenda",
                tipo = TaskType.TAREA,
                relevancia = TaskRelevance.ALTA,
                repeticion = TaskRecurrence.DIARIA,
                fecha = today,
                hora = "09:00",
                notas = "- [ ] Revisar bandeja de entrada\n- [ ] Priorizar 3 tareas clave del día",
                completada = false,
                traspasado = false,
                fechaCreacion = nowIso,
                notificacionActiva = true,
                minutosAntes = 0,
                rachaDiaria = 2,
                categoria = "Trabajo",
                diasSemana = "1,2,3,4,5",
            ),
            TaskItem(
                id = "ej-${System.currentTimeMillis()}-2",
                titulo = "[Ejemplo] Revisar recibo mensual e impuestos",
                tipo = TaskType.AVISO,
                relevancia = TaskRelevance.ALTA,
                repeticion = TaskRecurrence.MENSUAL,
                fecha = today,
                hora = "10:00",
                notas = "- [ ] Comprobar cargo en banco\n- [ ] Archivar recibo en carpeta",
                completada = false,
                traspasado = false,
                fechaCreacion = nowIso,
                notificacionActiva = true,
                minutosAntes = 10,
                categoria = "Trámites",
            ),
            TaskItem(
                id = "ej-${System.currentTimeMillis()}-3",
                titulo = "[Ejemplo] Renovar seguro anual del vehículo",
                tipo = TaskType.TAREA,
                relevancia = TaskRelevance.MEDIA,
                repeticion = TaskRecurrence.ANUAL,
                fecha = inTwoDays,
                hora = "",
                notas = "Revisar coberturas un mes antes del vencimiento.",
                completada = false,
                traspasado = false,
                fechaCreacion = nowIso,
                notificacionActiva = true,
                minutosAntes = 0,
                categoria = "Casa",
            ),
        )
        taskDao.upsertTasks(samples.map { it.toEntity() })

        val sampleDiary = DiaryEntry(
            id = "d-${System.currentTimeMillis()}-1",
            fecha = today,
            hora = nowHour,
            fechaHoraISO = nowIso,
            tipo = "manual",
            tituloTarea = "",
            texto = "[$nowHour] Inicio de jornada: revisión de tareas diarias y pendientes.",
        )
        diaryDao.upsertEntry(sampleDiary.toEntity())
        rescheduleAllNotifications()
        TareasWidgetProvider.requestRefresh(appContext)

        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTasks(uid, samples)
            cloudSyncManager.upsertDiaryEntry(uid, sampleDiary, preferCreate = true)
        }
    }

    suspend fun saveTask(task: TaskItem) {
        val settings = settingsStore.getSettingsOnce()
        val isNew = task.id.isBlank()
        val idToUse = if (isNew) {
            "t-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}"
        } else {
            sanitizeFirestoreDocId(task.id)
        }
        val finalTask = task.copy(
            id = idToUse,
            fechaCreacion = task.fechaCreacion.ifBlank { DateUtils.nowISO8601() },
        )
        taskDao.upsertTask(finalTask.toEntity())
        NotificationScheduler.scheduleTaskNotification(
            appContext,
            finalTask,
            settings.notificacionesGlobalesActivas
        )
        TareasWidgetProvider.requestRefresh(appContext)
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, finalTask, preferCreate = isNew)
        }
    }

    suspend fun duplicateTask(task: TaskItem): TaskItem {
        val copy = task.copy(
            id = "t-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            titulo = "${task.titulo} (copia)",
            completada = false,
            traspasado = false,
            fechaCreacion = DateUtils.nowISO8601(),
            fechaCompletada = null,
            notaCierre = "",
            rachaDiaria = 0,
            ultimaFechaCompletadaDiaria = null,
        )
        val settings = settingsStore.getSettingsOnce()
        taskDao.upsertTask(copy.toEntity())
        NotificationScheduler.scheduleTaskNotification(
            appContext,
            copy,
            settings.notificacionesGlobalesActivas
        )
        TareasWidgetProvider.requestRefresh(appContext)
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, copy, preferCreate = true)
        }
        return copy
    }

    suspend fun toggleChecklistLine(taskId: String, lineIndex: Int) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val updatedNotes = ChecklistUtils.toggleLine(entity.notas, lineIndex)
        val updated = entity.copy(notas = updatedNotes)
        taskDao.upsertTask(updated)
        val domainUpdated = updated.toDomain()
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, domainUpdated, preferCreate = false)
        }
    }

    suspend fun postponeTask(taskId: String, days: Int): String? {
        val entity = taskDao.getTaskById(taskId) ?: return null
        val task = entity.toDomain()
        val baseDate = if (DateUtils.diffDaysFromToday(task.fecha) < 0) {
            DateUtils.todayISO()
        } else {
            task.fecha
        }
        val newDate = DateUtils.addDays(baseDate, days)
        val updated = task.copy(fecha = newDate)
        taskDao.upsertTask(updated.toEntity())
        val settings = settingsStore.getSettingsOnce()
        NotificationScheduler.scheduleTaskNotification(appContext, updated, settings.notificacionesGlobalesActivas)
        TareasWidgetProvider.requestRefresh(appContext)
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, updated, preferCreate = false)
        }
        return newDate
    }

    suspend fun markTransferredToGoogle(taskId: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val updated = entity.copy(traspasado = true)
        taskDao.upsertTask(updated)
        val domainUpdated = updated.toDomain()
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, domainUpdated, preferCreate = false)
        }
    }

    suspend fun completeTask(
        taskId: String,
        notaCierre: String,
        scheduleNextRecurrence: Boolean,
    ): String? {
        return completeTaskWithUndo(taskId, notaCierre, scheduleNextRecurrence)?.nextDate
    }

    suspend fun completeTaskWithUndo(
        taskId: String,
        notaCierre: String,
        scheduleNextRecurrence: Boolean,
    ): CompletionUndoInfo? {
        val entity = taskDao.getTaskById(taskId) ?: return null
        val task = entity.toDomain()
        val nowIso = DateUtils.nowISO8601()
        val today = DateUtils.todayISO()
        val nowHour = DateUtils.nowHHMM()

        val detail = buildString {
            append("Finalizada a las $nowHour (prevista para el ${DateUtils.formatDateES(task.fecha)}).")
            if (notaCierre.isNotBlank()) {
                append("\nObservación de cierre: $notaCierre")
            } else if (task.notas.isNotBlank()) {
                append("\nDetalles: ${task.notas}")
            }
        }

        val diaryId = "d-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}"
        val diaryEntry = DiaryEntry(
            id = diaryId,
            fecha = today,
            hora = nowHour,
            fechaHoraISO = nowIso,
            tipo = "completada",
            tareaId = task.id,
            tituloTarea = task.titulo,
            texto = detail,
        )
        diaryDao.upsertEntry(diaryEntry.toEntity())

        val settings = settingsStore.getSettingsOnce()
        val isRecurring = task.repeticion != TaskRecurrence.NINGUNA

        return if (isRecurring && scheduleNextRecurrence) {
            val histId = "hist-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}"
            val historyRecord = task.copy(
                id = histId,
                completada = true,
                fechaCompletada = nowIso,
                notaCierre = notaCierre,
            )
            taskDao.upsertTask(historyRecord.toEntity())

            val baseForNext = if (task.repeticion == TaskRecurrence.DIARIA && task.fecha < today) {
                today
            } else {
                task.fecha
            }
            val nextDate = DateUtils.nextRecurrenceDate(baseForNext, task.repeticion, task.diasSemana)

            val newStreak = if (task.repeticion == TaskRecurrence.DIARIA) {
                val yesterday = DateUtils.addDays(today, -1)
                when (task.ultimaFechaCompletadaDiaria) {
                    today -> task.rachaDiaria.coerceAtLeast(1)
                    yesterday -> task.rachaDiaria + 1
                    else -> 1
                }
            } else {
                task.rachaDiaria
            }

            val resetChecklistNotes = if (task.repeticion == TaskRecurrence.DIARIA && ChecklistUtils.hasChecklist(task.notas)) {
                task.notas.lines().joinToString("\n") { line ->
                    line.replace(Regex("""^(\s*-\s*)\[[xX]\]"""), "$1[ ]")
                }
            } else {
                task.notas
            }

            val updatedActive = task.copy(
                fecha = nextDate,
                notas = resetChecklistNotes,
                notaCierre = "",
                traspasado = false,
                rachaDiaria = newStreak,
                ultimaFechaCompletadaDiaria = if (task.repeticion == TaskRecurrence.DIARIA) today else task.ultimaFechaCompletadaDiaria,
            )
            taskDao.upsertTask(updatedActive.toEntity())
            NotificationScheduler.scheduleTaskNotification(
                appContext,
                updatedActive,
                settings.notificacionesGlobalesActivas
            )
            TareasWidgetProvider.requestRefresh(appContext)
            enqueueCloudWrite { uid ->
                cloudSyncManager.upsertDiaryEntry(uid, diaryEntry, preferCreate = true)
                cloudSyncManager.upsertTask(uid, historyRecord, preferCreate = true)
                cloudSyncManager.upsertTask(uid, updatedActive, preferCreate = false)
            }
            CompletionUndoInfo(
                previousTask = task,
                createdHistoryTaskId = histId,
                createdDiaryEntryId = diaryId,
                nextDate = nextDate,
            )
        } else {
            val completed = task.copy(
                completada = true,
                fechaCompletada = nowIso,
                notaCierre = notaCierre,
            )
            taskDao.upsertTask(completed.toEntity())
            NotificationScheduler.cancelTaskNotification(appContext, task.id)
            TareasWidgetProvider.requestRefresh(appContext)
            enqueueCloudWrite { uid ->
                cloudSyncManager.upsertDiaryEntry(uid, diaryEntry, preferCreate = true)
                cloudSyncManager.upsertTask(uid, completed, preferCreate = false)
            }
            CompletionUndoInfo(
                previousTask = task,
                createdHistoryTaskId = null,
                createdDiaryEntryId = diaryId,
                nextDate = null,
            )
        }
    }

    suspend fun undoCompletion(info: CompletionUndoInfo) {
        if (info.createdHistoryTaskId != null) {
            taskDao.deleteTaskById(info.createdHistoryTaskId)
        }
        diaryDao.deleteEntryById(info.createdDiaryEntryId)
        taskDao.upsertTask(info.previousTask.toEntity())
        val settings = settingsStore.getSettingsOnce()
        NotificationScheduler.scheduleTaskNotification(appContext, info.previousTask, settings.notificacionesGlobalesActivas)
        TareasWidgetProvider.requestRefresh(appContext)

        enqueueCloudWrite { uid ->
            if (info.createdHistoryTaskId != null) {
                cloudSyncManager.deleteTask(uid, info.createdHistoryTaskId)
            }
            cloudSyncManager.deleteDiaryEntry(uid, info.createdDiaryEntryId)
            cloudSyncManager.upsertTask(uid, info.previousTask, preferCreate = false)
        }
    }

    suspend fun uncheckDailyTaskToday(taskId: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val task = entity.toDomain()
        val today = DateUtils.todayISO()
        val reverted = task.copy(
            fecha = today,
            rachaDiaria = (task.rachaDiaria - 1).coerceAtLeast(0),
            ultimaFechaCompletadaDiaria = if (task.ultimaFechaCompletadaDiaria == today) null else task.ultimaFechaCompletadaDiaria,
        )
        taskDao.upsertTask(reverted.toEntity())

        val allTasks = taskDao.getAllTasksOnce().map { it.toDomain() }
        val deletedHistoryTask = allTasks.filter {
            it.completada && it.titulo == task.titulo && it.repeticion == TaskRecurrence.DIARIA &&
                it.fechaCompletada?.startsWith(today) == true
        }.maxByOrNull { it.fechaCompletada ?: "" }
        deletedHistoryTask?.let { taskDao.deleteTaskById(it.id) }

        val allDiary = diaryDao.getAllEntriesOnce().map { it.toDomain() }
        val deletedDiary = allDiary.filter {
            it.tipo == "completada" && it.tareaId == task.id && it.fecha == today
        }.maxByOrNull { it.fechaHoraISO }
        deletedDiary?.let { diaryDao.deleteEntryById(it.id) }

        val settings = settingsStore.getSettingsOnce()
        NotificationScheduler.scheduleTaskNotification(appContext, reverted, settings.notificacionesGlobalesActivas)
        TareasWidgetProvider.requestRefresh(appContext)

        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, reverted, preferCreate = false)
            deletedHistoryTask?.let { cloudSyncManager.deleteTask(uid, it.id) }
            deletedDiary?.let { cloudSyncManager.deleteDiaryEntry(uid, it.id) }
        }
    }

    suspend fun restoreDeletedTask(task: TaskItem) {
        taskDao.upsertTask(task.toEntity())
        val settings = settingsStore.getSettingsOnce()
        if (!task.completada) {
            NotificationScheduler.scheduleTaskNotification(appContext, task, settings.notificacionesGlobalesActivas)
        }
        TareasWidgetProvider.requestRefresh(appContext)
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, task, preferCreate = true)
        }
    }

    suspend fun reopenTask(taskId: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val reopened = entity.toDomain().copy(completada = false, fechaCompletada = null)
        taskDao.upsertTask(reopened.toEntity())
        val settings = settingsStore.getSettingsOnce()
        NotificationScheduler.scheduleTaskNotification(appContext, reopened, settings.notificacionesGlobalesActivas)
        TareasWidgetProvider.requestRefresh(appContext)
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertTask(uid, reopened, preferCreate = false)
        }
    }

    suspend fun deleteTask(taskId: String): TaskItem? {
        val existing = taskDao.getTaskById(taskId)?.toDomain()
        taskDao.deleteTaskById(taskId)
        NotificationScheduler.cancelTaskNotification(appContext, taskId)
        TareasWidgetProvider.requestRefresh(appContext)
        enqueueCloudWrite { uid ->
            cloudSyncManager.deleteTask(uid, taskId)
        }
        return existing
    }

    suspend fun clearCompletedTasks() {
        val completedIds = taskDao.getAllTasksOnce().filter { it.completada }.map { it.id }
        taskDao.deleteCompletedTasks()
        if (completedIds.isNotEmpty()) {
            enqueueCloudWrite { uid ->
                cloudSyncManager.deleteTasksByIds(uid, completedIds)
            }
        }
    }

    suspend fun saveQuickDailyNote(fecha: String, text: String) {
        if (text.isBlank()) {
            noteDao.deleteNoteByDate(fecha)
            enqueueCloudWrite { uid ->
                cloudSyncManager.deleteDailyNote(uid, fecha)
            }
        } else {
            noteDao.upsertNote(DailyNoteEntity(fecha = fecha, texto = text))
            enqueueCloudWrite { uid ->
                cloudSyncManager.upsertDailyNote(uid, fecha, text)
            }
        }
    }

    suspend fun addCumulativeDiaryNote(fecha: String, textoNuevo: String) {
        val trimmed = textoNuevo.trim()
        if (trimmed.isEmpty()) return
        val horaAhora = DateUtils.nowHHMM()
        val lineWithTime = "[$horaAhora] $trimmed"

        val existing = diaryDao.getManualEntryForDate(fecha)
        if (existing != null) {
            val updatedText = if (existing.texto.isNotBlank()) {
                "${existing.texto}\n$lineWithTime"
            } else {
                lineWithTime
            }
            val updatedEntity = existing.copy(texto = updatedText, hora = horaAhora)
            diaryDao.upsertEntry(updatedEntity)
            val updatedDomain = updatedEntity.toDomain()
            enqueueCloudWrite { uid ->
                cloudSyncManager.upsertDiaryEntry(uid, updatedDomain, preferCreate = false)
            }
        } else {
            val newEntry = DiaryEntry(
                id = "d-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
                fecha = fecha,
                hora = horaAhora,
                fechaHoraISO = DateUtils.nowISO8601(),
                tipo = "manual",
                tareaId = null,
                tituloTarea = "",
                texto = lineWithTime,
            )
            diaryDao.upsertEntry(newEntry.toEntity())
            enqueueCloudWrite { uid ->
                cloudSyncManager.upsertDiaryEntry(uid, newEntry, preferCreate = true)
            }
        }
    }

    suspend fun updateDiaryEntry(id: String, fecha: String, texto: String) {
        val existing = diaryDao.getAllEntriesOnce().firstOrNull { it.id == id } ?: return
        val updated = existing.copy(fecha = fecha, texto = texto.trim())
        diaryDao.upsertEntry(updated)
        val domainUpdated = updated.toDomain()
        enqueueCloudWrite { uid ->
            cloudSyncManager.upsertDiaryEntry(uid, domainUpdated, preferCreate = false)
        }
    }

    suspend fun deleteDiaryEntry(id: String) {
        diaryDao.deleteEntryById(id)
        enqueueCloudWrite { uid ->
            cloudSyncManager.deleteDiaryEntry(uid, id)
        }
    }

    suspend fun updateTema(tema: TemaMode) = settingsStore.updateTema(tema)
    suspend fun updateVistaDefecto(vista: CalendarViewMode) = settingsStore.updateVistaDefecto(vista)
    suspend fun updateDiasPreaviso(dias: Int) = settingsStore.updateDiasPreaviso(dias)
    suspend fun updateMostrarRealizadasCal(mostrar: Boolean) = settingsStore.updateMostrarRealizadasCal(mostrar)

    suspend fun updateNotificacionesGlobales(enabled: Boolean) {
        settingsStore.updateNotificacionesGlobales(enabled)
        rescheduleAllNotifications()
    }

    suspend fun updateResumenDiario(enabled: Boolean, hora: String? = null) {
        settingsStore.updateResumenDiario(enabled, hora)
        rescheduleAllNotifications()
    }

    fun ensureNotificationChannels() {
        NotificationHelper.ensureChannels(appContext)
    }

    suspend fun rescheduleAllNotifications() {
        val tasks = taskDao.getAllTasksOnce().map { it.toDomain() }
        val settings = settingsStore.getSettingsOnce()
        NotificationScheduler.rescheduleAll(appContext, tasks, settings)
    }

    suspend fun generateExcelXml(): String {
        val tasks = taskDao.getAllTasksOnce().map { it.toDomain() }
        val diary = diaryDao.getAllEntriesOnce().map { it.toDomain() }
        return ExcelExporter.generateSpreadsheetXml(tasks, diary)
    }

    suspend fun generateBackupJson(): String {
        val nowIso = DateUtils.nowISO8601()
        settingsStore.updateUltimoBackup(nowIso)
        val tasks = taskDao.getAllTasksOnce().map { it.toDomain() }
        val notes = noteDao.getAllNotesOnce().associate { it.fecha to it.texto }
        val diary = diaryDao.getAllEntriesOnce().map { it.toDomain() }
        val settings = settingsStore.getSettingsOnce().copy(ultimoBackup = nowIso)
        return JsonBackupManager.exportJson(tasks, notes, diary, settings, nowIso)
    }

    suspend fun restoreFromBackupJson(rawJson: String): Pair<Int, Int> {
        val payload = JsonBackupManager.parseBackupJson(rawJson)
        val sanitizedTasks = payload.tasks.map { it.copy(id = sanitizeFirestoreDocId(it.id)) }
        val sanitizedDiary = payload.diaryEntries.map { it.copy(id = sanitizeFirestoreDocId(it.id)) }

        taskDao.clearAll()
        noteDao.clearAll()
        diaryDao.clearAll()

        taskDao.upsertTasks(sanitizedTasks.map { it.toEntity() })
        noteDao.upsertNotes(payload.dailyNotes.map { DailyNoteEntity(it.key, it.value) })
        diaryDao.upsertEntries(sanitizedDiary.map { it.toEntity() })
        payload.settings?.let { settingsStore.applyRestoredSettings(it) }
        rescheduleAllNotifications()
        TareasWidgetProvider.requestRefresh(appContext)

        enqueueCloudWrite { uid ->
            cloudSyncManager.replaceAllFromBackup(uid, sanitizedTasks, payload.dailyNotes, sanitizedDiary)
        }
        return sanitizedTasks.size to sanitizedDiary.size
    }
}
