package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskCategory
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.ui.theme.LocalAppColors
import com.example.util.ChecklistUtils
import com.example.util.DateUtils
import com.example.util.EditableChecklistItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    initialTask: TaskItem?,
    defaultDateISO: String,
    defaultRecurrence: TaskRecurrence = TaskRecurrence.NINGUNA,
    onDismiss: () -> Unit,
    onSave: (TaskItem) -> Unit,
) {
    val appColors = LocalAppColors.current
    val initialParts = remember(initialTask?.id) {
        ChecklistUtils.extractParts(initialTask?.notas ?: "")
    }

    var titulo by remember { mutableStateOf(initialTask?.titulo ?: "") }
    var tipo by remember { mutableStateOf(initialTask?.tipo ?: TaskType.TAREA) }
    var relevancia by remember { mutableStateOf(initialTask?.relevancia ?: TaskRelevance.MEDIA) }
    var categoria by remember { mutableStateOf(initialTask?.categoria ?: "") }
    var fecha by remember { mutableStateOf(initialTask?.fecha?.ifBlank { defaultDateISO } ?: defaultDateISO) }
    var hora by remember { mutableStateOf(initialTask?.hora ?: "") }
    var repeticion by remember { mutableStateOf(initialTask?.repeticion ?: defaultRecurrence) }
    var selectedWeekdays by remember {
        mutableStateOf(DateUtils.parseWeekdaysSet(initialTask?.diasSemana ?: ""))
    }
    var plainNotes by remember { mutableStateOf(initialParts.first) }
    var checklistItems by remember { mutableStateOf(initialParts.second) }
    var newStepText by remember { mutableStateOf("") }

    var notificacionActiva by remember { mutableStateOf(initialTask?.notificacionActiva ?: true) }
    var minutosAntes by remember { mutableIntStateOf(initialTask?.minutosAntes ?: 0) }
    var titleError by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val todayISO = remember { DateUtils.todayISO() }
    val tomorrowISO = remember { DateUtils.addDays(todayISO, 1) }
    val nextMondayISO = remember { DateUtils.nextMondayISO(todayISO) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (initialTask != null) "Editar tarea o aviso" else "Nueva tarea o aviso",
                    style = MaterialTheme.typography.titleLarge,
                    color = appColors.headingText,
                )

                OutlinedTextField(
                    value = titulo,
                    onValueChange = {
                        titulo = it
                        titleError = false
                    },
                    label = { Text("Título *") },
                    placeholder = { Text("Ej: Revisar seguro del coche") },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Introduce un título") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                )

                Text("Tipo de registro", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskType.entries.forEach { item ->
                        FilterChip(
                            selected = tipo == item,
                            onClick = { tipo = item },
                            label = { Text(if (item == TaskType.TAREA) "📌 Tarea" else "🔔 Aviso") },
                        )
                    }
                }

                Text("Relevancia", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRelevance.entries.forEach { item ->
                        FilterChip(
                            selected = relevancia == item,
                            onClick = { relevancia = item },
                            label = {
                                Text(
                                    when (item) {
                                        TaskRelevance.ALTA -> "🔴 Alta"
                                        TaskRelevance.MEDIA -> "🟠 Media"
                                        TaskRelevance.BAJA -> "🔵 Baja"
                                    }
                                )
                            },
                        )
                    }
                }

                Text("Categoría (opcional)", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = categoria.isBlank(),
                        onClick = { categoria = "" },
                        label = { Text("Sin categoría") },
                    )
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = categoria.equals(cat.storageValue, ignoreCase = true),
                            onClick = {
                                categoria = if (categoria.equals(cat.storageValue, ignoreCase = true)) "" else cat.storageValue
                            },
                            label = { Text("${cat.emoji} ${cat.storageValue}") },
                        )
                    }
                }

                // Quick Date Chips + Date/Time Picker Buttons
                Text("Fecha y hora", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = fecha == todayISO,
                        onClick = { fecha = todayISO },
                        label = { Text("Hoy") },
                    )
                    FilterChip(
                        selected = fecha == tomorrowISO,
                        onClick = { fecha = tomorrowISO },
                        label = { Text("Mañana") },
                    )
                    FilterChip(
                        selected = fecha == nextMondayISO,
                        onClick = { fecha = nextMondayISO },
                        label = { Text("Próx. lunes") },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("task_date_button"),
                    ) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(DateUtils.formatDateES(fecha))
                    }

                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("task_time_button"),
                    ) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(hora.ifBlank { "Sin hora" })
                    }

                    if (hora.isNotBlank()) {
                        IconButton(onClick = { hora = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Quitar hora")
                        }
                    }
                }

                Text("🔁 Repetir periódicamente", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TaskRecurrence.entries.forEach { rec ->
                        FilterChip(
                            selected = repeticion == rec,
                            onClick = { repeticion = rec },
                            label = {
                                Text(
                                    when (rec) {
                                        TaskRecurrence.NINGUNA -> "1 vez"
                                        TaskRecurrence.DIARIA -> "🔁 Diaria"
                                        TaskRecurrence.SEMANAL -> "🔁 Semanal"
                                        TaskRecurrence.MENSUAL -> "🔁 Mensual"
                                        TaskRecurrence.ANUAL -> "🔁 Anual"
                                    }
                                )
                            },
                        )
                    }
                }

                // Weekday Selector for Daily Tasks
                if (repeticion == TaskRecurrence.DIARIA) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = appColors.purpleContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, appColors.purple.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Días activos de la rutina",
                                style = MaterialTheme.typography.labelLarge,
                                color = appColors.purple,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = selectedWeekdays.isEmpty() || selectedWeekdays.size == 7,
                                    onClick = { selectedWeekdays = emptySet() },
                                    label = { Text("Todos") },
                                )
                                FilterChip(
                                    selected = selectedWeekdays == setOf(1, 2, 3, 4, 5),
                                    onClick = { selectedWeekdays = setOf(1, 2, 3, 4, 5) },
                                    label = { Text("L–V (Laborables)") },
                                )
                                FilterChip(
                                    selected = selectedWeekdays == setOf(6, 7),
                                    onClick = { selectedWeekdays = setOf(6, 7) },
                                    label = { Text("S–D (Finde)") },
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                DateUtils.shortWeekDays.forEachIndexed { idx, label ->
                                    val dayNum = idx + 1
                                    val isSelected = selectedWeekdays.isEmpty() || dayNum in selectedWeekdays
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val current = if (selectedWeekdays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else selectedWeekdays
                                            val next = if (dayNum in current) current - dayNum else current + dayNum
                                            selectedWeekdays = when {
                                                next.isEmpty() -> setOf(dayNum)
                                                next.size == 7 -> emptySet()
                                                else -> next
                                            }
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Local Notification configuration card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = appColors.blueContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, appColors.blue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = appColors.blue,
                                )
                                Column {
                                    Text(
                                        "Notificación local",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        if (hora.isBlank()) "Si no hay hora, avisará a las 09:00"
                                        else "Programada para las $hora",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = notificacionActiva,
                                onCheckedChange = { notificacionActiva = it },
                                modifier = Modifier.testTag("task_notification_switch"),
                            )
                        }

                        if (notificacionActiva) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(0 to "A la hora", 10 to "10m antes", 30 to "30m antes", 60 to "1h antes").forEach { (mins, label) ->
                                    FilterChip(
                                        selected = minutosAntes == mins,
                                        onClick = { minutosAntes = mins },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Visual Checklist Builder
                Text("☑️ Pasos de checklist", style = MaterialTheme.typography.labelLarge)
                if (checklistItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        checklistItems.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Checkbox(
                                    checked = item.checked,
                                    onCheckedChange = { checked ->
                                        checklistItems = checklistItems.toMutableList().also {
                                            it[idx] = item.copy(checked = checked)
                                        }
                                    },
                                )
                                OutlinedTextField(
                                    value = item.text,
                                    onValueChange = { updatedText ->
                                        checklistItems = checklistItems.toMutableList().also {
                                            it[idx] = item.copy(text = updatedText)
                                        }
                                    },
                                    singleLine = true,
                                    placeholder = { Text("Paso ${idx + 1}...") },
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        checklistItems = checklistItems.toMutableList().also { it.removeAt(idx) }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Quitar paso")
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = newStepText,
                        onValueChange = { newStepText = it },
                        placeholder = { Text("Nuevo paso de checklist...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            val clean = newStepText.trim()
                            checklistItems = checklistItems + EditableChecklistItem(
                                checked = false,
                                text = clean.ifEmpty { "Paso ${checklistItems.size + 1}" },
                            )
                            newStepText = ""
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Paso")
                    }
                }

                Text("Descripción / Notas adicionales", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = plainNotes,
                    onValueChange = { plainNotes = it },
                    placeholder = { Text("Detalles o comentarios opcionales...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_notes_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val cleanTitle = titulo.trim()
                            if (cleanTitle.isEmpty()) {
                                titleError = true
                                return@Button
                            }
                            val finalItems = if (newStepText.isNotBlank()) {
                                checklistItems + EditableChecklistItem(false, newStepText.trim())
                            } else {
                                checklistItems
                            }
                            val composedNotes = ChecklistUtils.composeNotes(plainNotes, finalItems)
                            val diasSemanaStr = if (repeticion == TaskRecurrence.DIARIA && selectedWeekdays.isNotEmpty() && selectedWeekdays.size < 7) {
                                selectedWeekdays.sorted().joinToString(",")
                            } else {
                                ""
                            }

                            val result = (initialTask ?: TaskItem(
                                id = "",
                                titulo = cleanTitle,
                                fecha = fecha,
                            )).copy(
                                titulo = cleanTitle,
                                tipo = tipo,
                                relevancia = relevancia,
                                fecha = fecha,
                                hora = hora,
                                repeticion = repeticion,
                                notas = composedNotes,
                                notificacionActiva = notificacionActiva,
                                minutosAntes = minutosAntes,
                                categoria = categoria.trim(),
                                diasSemana = diasSemanaStr,
                            )
                            onSave(result)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_task_button"),
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val utcMillis = remember(fecha) { isoToUtcMillis(fecha) }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            fecha = utcMillisToIso(millis)
                        }
                        showDatePicker = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val parts = hora.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val initMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val timeState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Seleccionar hora") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hora = String.format(Locale.US, "%02d:%02d", timeState.hour, timeState.minute)
                        showTimePicker = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
fun CompleteTaskDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onConfirm: (notaCierre: String, scheduleNext: Boolean) -> Unit,
) {
    val appColors = LocalAppColors.current
    var notaCierre by remember { mutableStateOf(task.notaCierre) }
    var scheduleNext by remember { mutableStateOf(true) }
    val isRecurring = task.repeticion != TaskRecurrence.NINGUNA

    val baseForNext = if (task.repeticion == TaskRecurrence.DIARIA && task.fecha < DateUtils.todayISO()) {
        DateUtils.todayISO()
    } else {
        task.fecha
    }
    val nextDate = if (isRecurring) {
        DateUtils.nextRecurrenceDate(baseForNext, task.repeticion, task.diasSemana)
    } else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✅ Marcar como realizada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = task.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (isRecurring) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = appColors.purpleContainer,
                        border = BorderStroke(1.dp, appColors.purple.copy(alpha = 0.4f)),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "🔁 Tarea ${task.repeticion.shortLabel.lowercase()}: se registrará en el Diario e Histórico y se programará para el ${DateUtils.formatDateES(nextDate)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Checkbox(
                                    checked = scheduleNext,
                                    onCheckedChange = { scheduleNext = it },
                                )
                                Text(
                                    "Programar automáticamente siguiente fecha",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notaCierre,
                    onValueChange = { notaCierre = it },
                    label = { Text("Observación de cierre (opcional)") },
                    placeholder = { Text("Ej: Pagado, factura archivada, todo OK...") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complete_note_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(notaCierre.trim(), scheduleNext) },
                colors = ButtonDefaults.buttonColors(containerColor = appColors.green, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_complete_button"),
            ) {
                Text("Confirmar realizada")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDiaryObservationDialog(
    initialDateISO: String,
    existingManualText: String?,
    onDismiss: () -> Unit,
    onConfirm: (fechaISO: String, textoNuevo: String) -> Unit,
) {
    val appColors = LocalAppColors.current
    var fecha by remember { mutableStateOf(initialDateISO) }
    var texto by remember { mutableStateOf("") }
    var textError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .imePadding()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "📖 Añadir nota al Diario",
                    style = MaterialTheme.typography.titleLarge,
                    color = appColors.headingText,
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fecha: ${DateUtils.formatDateES(fecha)}")
                    }

                    if (!existingManualText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = appColors.blueContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Ya anotado:\n$existingManualText",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 4,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = texto,
                        onValueChange = {
                            texto = it
                            textError = false
                        },
                        label = { Text("Nueva nota u observación *") },
                        placeholder = { Text("Escribe aquí tu nota (se añadirá con la hora actual)...") },
                        isError = textError,
                        supportingText = if (textError) {
                            { Text("Escribe el texto de la nota antes de confirmar") }
                        } else null,
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diary_add_obs_input"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val clean = texto.trim()
                            if (clean.isEmpty()) {
                                textError = true
                            } else {
                                onConfirm(fecha, clean)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.blue,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("diary_add_obs_save"),
                    ) {
                        Text("Confirmar nota")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(fecha))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { fecha = utcMillisToIso(it) }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDiaryObservationDialog(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onConfirm: (id: String, fechaISO: String, texto: String) -> Unit,
) {
    val appColors = LocalAppColors.current
    var fecha by remember { mutableStateOf(entry.fecha) }
    var texto by remember { mutableStateOf(entry.texto) }
    var textError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .imePadding()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "✏️ Editar observaciones del día",
                    style = MaterialTheme.typography.titleLarge,
                    color = appColors.headingText,
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fecha: ${DateUtils.formatDateES(fecha)}")
                    }
                    OutlinedTextField(
                        value = texto,
                        onValueChange = {
                            texto = it
                            textError = false
                        },
                        label = { Text("Texto completo *") },
                        isError = textError,
                        supportingText = if (textError) {
                            { Text("El texto no puede estar vacío") }
                        } else null,
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val clean = texto.trim()
                            if (clean.isEmpty()) {
                                textError = true
                            } else {
                                onConfirm(entry.id, fecha, clean)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.blue,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Text("Guardar cambios")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(fecha))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { fecha = utcMillisToIso(it) }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
fun ExcelExportDialog(
    onDismiss: () -> Unit,
    onSaveToDevice: (fileName: String) -> Unit,
    onShareFile: (fileName: String) -> Unit,
) {
    var fileName by remember {
        mutableStateOf("informe_tareas_diario_${DateUtils.todayISO()}.xls")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 Exportar consolidado a Excel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LocalAppColors.current.blueContainer.copy(alpha = 0.45f),
                ) {
                    Text(
                        text = "Archivo Excel (.xls) multihoja 100 % sin conexión con las pestañas «Tareas» y «Agenda y Diario».",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nombre del archivo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onShareFile(fileName) }) {
                    Text("Compartir")
                }
                Button(
                    onClick = { onSaveToDevice(fileName) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.green,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Sí, continuar",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalAppColors.current.red,
                    contentColor = Color.White,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

fun isoToUtcMillis(isoDate: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        sdf.parse(isoDate)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

fun utcMillisToIso(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(millis))
}
