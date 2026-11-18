package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.cloud.CloudSyncManager
import com.example.data.model.AppTab
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.repository.TareasRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationScheduler
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.auth.authStateFlow
import com.example.ui.components.AddDiaryObservationDialog
import com.example.ui.components.CloudAccountStatusDialog
import com.example.ui.components.CompleteTaskDialog
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EditDiaryObservationDialog
import com.example.ui.components.ExcelExportDialog
import com.example.ui.components.TaskEditorDialog
import com.example.ui.components.TopBarCloudSyncPill
import com.example.ui.screens.AjustesScreen
import com.example.ui.screens.CalendarioScreen
import com.example.ui.screens.DiarioScreen
import com.example.ui.screens.HistoricoScreen
import com.example.ui.screens.PendientesScreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.TareasYAvisosTheme
import com.example.util.DateUtils
import com.example.util.ExternalIntents
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun TareasApp(
    openNewTaskTrigger: Int = 0,
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        TareasYAvisosTheme {
            AuthScreen()
        }
    } else {
        key(user.uid) {
            val viewModel: TareasViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val repository = TareasRepository(app, CloudSyncManager(db))
                        TareasViewModel(
                            repository = repository,
                            currentUserId = user.uid,
                            initialHasNotifPermission = NotificationHelper.hasNotificationPermission(app),
                            initialCanExactAlarms = NotificationScheduler.canScheduleExactAlarms(app),
                        )
                    }
                },
            )
            AuthenticatedTareasApp(
                user = user,
                openNewTaskTrigger = openNewTaskTrigger,
                viewModel = viewModel,
                onSignOutConfirmed = { context ->
                    GoogleAuthHelper.signOut(
                        context = context,
                        auth = auth,
                        beforeAuthSignOut = { viewModel.prepareSignOut() },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedTareasApp(
    user: FirebaseUser,
    openNewTaskTrigger: Int,
    viewModel: TareasViewModel,
    onSignOutConfirmed: suspend (Context) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val hasNotifPerm by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val canExactAlarms by viewModel.canExactAlarms.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var createTaskDefaultDate by remember { mutableStateOf(DateUtils.todayISO()) }
    var createTaskDefaultRecurrence by remember { mutableStateOf(TaskRecurrence.NINGUNA) }
    var showCloudStatusDialog by remember { mutableStateOf(false) }

    var completingTask by remember { mutableStateOf<TaskItem?>(null) }
    var addObsDate by remember { mutableStateOf<String?>(null) }
    var editingDiaryEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var showExcelDialog by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    // Pending export payloads for SAF CreateDocument
    var pendingExcelContent by remember { mutableStateOf<String?>(null) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openNewTaskTrigger) {
        if (openNewTaskTrigger > 0) {
            editingTask = null
            createTaskDefaultDate = DateUtils.todayISO()
            createTaskDefaultRecurrence = TaskRecurrence.NINGUNA
            showCreateTaskDialog = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshNotificationPermission(context)
        viewModel.emitToast(
            if (granted) "Permiso de notificaciones concedido"
            else "Puedes activar las notificaciones más adelante en Ajustes"
        )
    }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.ms-excel"),
    ) { uri ->
        val content = pendingExcelContent
        pendingExcelContent = null
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                viewModel.emitToast("Archivo Excel guardado correctamente")
            } catch (e: Exception) {
                viewModel.emitToast("Error al guardar Excel: ${e.message}")
            }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingBackupJson
        pendingBackupJson = null
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                viewModel.emitToast("Copia de seguridad guardada")
            } catch (e: Exception) {
                viewModel.emitToast("Error al guardar copia: ${e.message}")
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!raw.isNullOrBlank()) {
                    pendingConfirm = Triple(
                        "Restaurar copia de seguridad",
                        "Se reemplazarán las tareas, diario y notas actuales con los datos del archivo seleccionado. ¿Deseas continuar?"
                    ) {
                        viewModel.restoreBackupJson(raw)
                    }
                }
            } catch (e: Exception) {
                viewModel.emitToast("No se pudo leer el archivo JSON")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = msg.message,
                actionLabel = if (msg.canUndo) "Deshacer" else null,
                duration = if (msg.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && msg.canUndo) {
                viewModel.undoLastAction()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { request ->
            when (request) {
                is ExportDocumentRequest.SaveExcel -> {
                    pendingExcelContent = request.xmlContent
                    createExcelLauncher.launch(request.fileName)
                }
                is ExportDocumentRequest.SaveBackupJson -> {
                    pendingBackupJson = request.jsonContent
                    createBackupLauncher.launch(request.fileName)
                }
            }
        }
    }

    // BackHandler returns from secondary tabs to Pendientes
    BackHandler(enabled = uiState.currentTab != AppTab.PENDIENTES) {
        viewModel.selectTab(AppTab.PENDIENTES)
    }

    val requestNotifPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openExactAlarmSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (_: Exception) {
                viewModel.emitToast("Abre Ajustes del sistema para permitir alarmas exactas")
            }
        }
    }

    TareasYAvisosTheme(temaMode = uiState.settings.tema) {
        val appColors = LocalAppColors.current

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clickable { showCloudStatusDialog = true }
                                    .padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "📋 Tareas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                                TopBarCloudSyncPill(syncState = cloudSyncState)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = appColors.headerBg,
                            titleContentColor = Color.White,
                        ),
                        actions = {
                            FilledTonalButton(
                                onClick = { showExcelDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = appColors.green,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .testTag("header_excel_button"),
                            ) {
                                Icon(Icons.Outlined.TableView, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Excel")
                            }
                            FilledTonalButton(
                                onClick = {
                                    editingTask = null
                                    createTaskDefaultDate = DateUtils.todayISO()
                                    createTaskDefaultRecurrence = TaskRecurrence.NINGUNA
                                    showCreateTaskDialog = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = appColors.blue,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("header_new_task_button"),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(2.dp))
                                Text("Tarea")
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            AppTab.entries.forEach { tab ->
                                val selected = uiState.currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = tabIcon(tab, selected),
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (useNavigationRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            AppTab.entries.forEach { tab ->
                                val selected = uiState.currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = tabIcon(tab, selected),
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val contentModifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)

                        when (uiState.currentTab) {
                            AppTab.PENDIENTES -> PendientesScreen(
                                uiState = uiState,
                                hasNotificationPermission = hasNotifPerm,
                                onRequestNotificationPermission = requestNotifPermission,
                                onBackupNow = { viewModel.requestSaveBackupJson() },
                                onSearchChange = viewModel::updateSearchPendientes,
                                onToggleFiltersExpanded = viewModel::toggleFiltersExpanded,
                                onFilterRelevanceChange = viewModel::updateFilterRelevance,
                                onFilterTypeChange = viewModel::updateFilterType,
                                onFilterRecurrenceChange = viewModel::updateFilterRecurrence,
                                onFilterCategoryChange = viewModel::updateFilterCategory,
                                onSortOrderChange = viewModel::updateSortOrder,
                                onResetFilters = viewModel::resetFilters,
                                onCompleteClick = { completingTask = it },
                                onQuickCompleteDaily = { task ->
                                    viewModel.completeTask(task.id, "", scheduleNext = true)
                                },
                                onUndoCompleteDaily = { task ->
                                    viewModel.uncheckDailyTaskToday(task.id)
                                },
                                onAddDailyTask = {
                                    editingTask = null
                                    createTaskDefaultDate = DateUtils.todayISO()
                                    createTaskDefaultRecurrence = TaskRecurrence.DIARIA
                                    showCreateTaskDialog = true
                                },
                                onPostponeTask = viewModel::postponeTask,
                                onEditTask = {
                                    editingTask = it
                                    showCreateTaskDialog = true
                                },
                                onDuplicateTask = viewModel::duplicateTask,
                                onDeleteTask = { task ->
                                    pendingConfirm = Triple(
                                        "Eliminar tarea",
                                        "¿Seguro que quieres eliminar \"${task.titulo}\"?"
                                    ) { viewModel.deleteTask(task.id) }
                                },
                                onToggleChecklistLine = viewModel::toggleChecklistLine,
                                onGoogleCalendarClick = { viewModel.transferToGoogleCalendar(context, it) },
                                onGoogleTasksClick = { viewModel.transferToGoogleTasks(context, it) },
                                onShareClick = { ExternalIntents.shareTask(context, it) },
                                modifier = contentModifier,
                            )

                            AppTab.CALENDARIO -> CalendarioScreen(
                                uiState = uiState,
                                onChangeViewMode = viewModel::setCalendarViewMode,
                                onShiftPeriod = viewModel::shiftCalendarPeriod,
                                onJumpToday = viewModel::jumpCalendarToday,
                                onSelectDate = viewModel::selectCalendarDate,
                                onSaveQuickNote = viewModel::saveQuickDailyNote,
                                onAddTaskInDate = { dateISO ->
                                    editingTask = null
                                    createTaskDefaultDate = dateISO
                                    createTaskDefaultRecurrence = TaskRecurrence.NINGUNA
                                    showCreateTaskDialog = true
                                },
                                onAddObsInDate = { dateISO -> addObsDate = dateISO },
                                onEditDiaryEntry = { editingDiaryEntry = it },
                                onCompleteClick = { completingTask = it },
                                onPostponeTask = viewModel::postponeTask,
                                onEditTask = {
                                    editingTask = it
                                    showCreateTaskDialog = true
                                },
                                onDuplicateTask = viewModel::duplicateTask,
                                onDeleteTask = { task ->
                                    pendingConfirm = Triple(
                                        "Eliminar tarea",
                                        "¿Seguro que quieres eliminar \"${task.titulo}\"?"
                                    ) { viewModel.deleteTask(task.id) }
                                },
                                onReopenTask = { viewModel.reopenTask(it.id) },
                                onToggleChecklistLine = viewModel::toggleChecklistLine,
                                onGoogleCalendarClick = { viewModel.transferToGoogleCalendar(context, it) },
                                onGoogleTasksClick = { viewModel.transferToGoogleTasks(context, it) },
                                onShareClick = { ExternalIntents.shareTask(context, it) },
                                modifier = contentModifier,
                            )

                            AppTab.DIARIO -> DiarioScreen(
                                uiState = uiState,
                                onDateFilterChange = viewModel::updateDiaryDateFilter,
                                onSearchChange = viewModel::updateDiarySearch,
                                onClearFilters = viewModel::clearDiaryFilters,
                                onAddObservation = { dateISO -> addObsDate = dateISO },
                                onEditObservation = { editingDiaryEntry = it },
                                onDeleteObservation = { entry ->
                                    pendingConfirm = Triple(
                                        "Eliminar apunte del Diario",
                                        "¿Seguro que quieres eliminar esta anotación del Diario?"
                                    ) { viewModel.deleteDiaryEntry(entry.id) }
                                },
                                modifier = contentModifier,
                            )

                            AppTab.HISTORICO -> HistoricoScreen(
                                uiState = uiState,
                                onSearchChange = viewModel::updateHistorySearch,
                                onRelevanceChange = viewModel::updateHistoryRelevance,
                                onExportExcelClick = { showExcelDialog = true },
                                onClearHistoryClick = {
                                    val count = uiState.completedTasks.size
                                    if (count == 0) {
                                        viewModel.emitToast("El histórico ya está vacío")
                                    } else {
                                        pendingConfirm = Triple(
                                            "Vaciar histórico",
                                            "¿Seguro que quieres borrar las $count tareas realizadas? Las pendientes y el Diario no se eliminarán."
                                        ) { viewModel.clearHistory() }
                                    }
                                },
                                onReopenTask = { viewModel.reopenTask(it.id) },
                                onEditTask = {
                                    editingTask = it
                                    showCreateTaskDialog = true
                                },
                                onDeleteTask = { task ->
                                    pendingConfirm = Triple(
                                        "Eliminar tarea",
                                        "¿Seguro que quieres eliminar \"${task.titulo}\"?"
                                    ) { viewModel.deleteTask(task.id) }
                                },
                                onShareTask = { ExternalIntents.shareTask(context, it) },
                                modifier = contentModifier,
                            )

                            AppTab.AJUSTES -> AjustesScreen(
                                uiState = uiState,
                                userEmail = user.email,
                                userDisplayName = user.displayName,
                                cloudSyncState = cloudSyncState,
                                onSyncNow = { viewModel.syncNow() },
                                onSignOutClick = {
                                    pendingConfirm = Triple(
                                        "Cerrar sesión",
                                        "Tus datos seguirán guardados de forma segura en tu cuenta de Google (${user.email ?: ""}). ¿Quieres cerrar sesión en este dispositivo?"
                                    ) {
                                        appScope.launch {
                                            onSignOutConfirmed(context)
                                        }
                                    }
                                },
                                hasNotificationPermission = hasNotifPerm,
                                canExactAlarms = canExactAlarms,
                                onRequestNotificationPermission = requestNotifPermission,
                                onOpenExactAlarmSettings = openExactAlarmSettings,
                                onToggleGlobalNotifications = viewModel::updateNotificacionesGlobales,
                                onToggleDailySummary = viewModel::updateResumenDiario,
                                onTestNotification = { viewModel.triggerTestNotification(context) },
                                onExportExcelClick = { showExcelDialog = true },
                                onTemaChange = viewModel::updateTema,
                                onVistaDefectoChange = viewModel::updateVistaDefecto,
                                onDiasPreavisoChange = viewModel::updateDiasPreaviso,
                                onMostrarRealizadasCalChange = viewModel::updateMostrarRealizadasCal,
                                onSaveBackupJson = { viewModel.requestSaveBackupJson() },
                                onShareBackupJson = { viewModel.shareBackupJson(context) },
                                onRestoreBackupJson = { openBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                                onLoadSampleData = viewModel::loadSampleData,
                                modifier = contentModifier,
                            )
                        }
                    }
                }
            }
        }

        // Dialogs
        if (showCreateTaskDialog) {
            TaskEditorDialog(
                initialTask = editingTask,
                defaultDateISO = createTaskDefaultDate,
                defaultRecurrence = createTaskDefaultRecurrence,
                onDismiss = {
                    showCreateTaskDialog = false
                    editingTask = null
                },
                onSave = { task ->
                    viewModel.saveTask(task)
                    showCreateTaskDialog = false
                    editingTask = null
                    if (task.notificacionActiva && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifPermission()
                    }
                },
            )
        }

        completingTask?.let { task ->
            CompleteTaskDialog(
                task = task,
                onDismiss = { completingTask = null },
                onConfirm = { note, scheduleNext ->
                    viewModel.completeTask(task.id, note, scheduleNext)
                    completingTask = null
                },
            )
        }

        addObsDate?.let { dateISO ->
            val existingManual = uiState.diaryEntries
                .firstOrNull { it.fecha == dateISO && it.tipo == "manual" }?.texto
            AddDiaryObservationDialog(
                initialDateISO = dateISO,
                existingManualText = existingManual,
                onDismiss = { addObsDate = null },
                onConfirm = { fecha, texto ->
                    viewModel.addCumulativeDiaryNote(fecha, texto)
                    addObsDate = null
                },
            )
        }

        editingDiaryEntry?.let { entry ->
            EditDiaryObservationDialog(
                entry = entry,
                onDismiss = { editingDiaryEntry = null },
                onConfirm = { id, fecha, texto ->
                    viewModel.updateDiaryEntry(id, fecha, texto)
                    editingDiaryEntry = null
                },
            )
        }

        if (showExcelDialog) {
            ExcelExportDialog(
                onDismiss = { showExcelDialog = false },
                onSaveToDevice = { fileName ->
                    showExcelDialog = false
                    viewModel.requestSaveExcel(fileName)
                },
                onShareFile = { fileName ->
                    showExcelDialog = false
                    viewModel.shareExcel(context, fileName)
                },
            )
        }

        if (showCloudStatusDialog) {
            CloudAccountStatusDialog(
                userEmail = user.email,
                userDisplayName = user.displayName,
                syncState = cloudSyncState,
                onSyncNow = {
                    showCloudStatusDialog = false
                    viewModel.syncNow()
                },
                onSignOut = {
                    showCloudStatusDialog = false
                    pendingConfirm = Triple(
                        "Cerrar sesión",
                        "Tus datos seguirán guardados en tu cuenta de Google (${user.email ?: ""}). ¿Quieres cerrar sesión en este dispositivo?"
                    ) {
                        appScope.launch {
                            onSignOutConfirmed(context)
                        }
                    }
                },
                onDismiss = { showCloudStatusDialog = false },
            )
        }

        pendingConfirm?.let { (title, message, onConfirm) ->
            ConfirmActionDialog(
                title = title,
                message = message,
                onDismiss = { pendingConfirm = null },
                onConfirm = {
                    pendingConfirm = null
                    onConfirm()
                },
            )
        }
    }
}

private fun tabIcon(tab: AppTab, selected: Boolean) = when (tab) {
    AppTab.PENDIENTES -> if (selected) Icons.Filled.Checklist else Icons.Outlined.Checklist
    AppTab.CALENDARIO -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    AppTab.DIARIO -> if (selected) Icons.Filled.AutoStories else Icons.Outlined.AutoStories
    AppTab.HISTORICO -> if (selected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
    AppTab.AJUSTES -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
