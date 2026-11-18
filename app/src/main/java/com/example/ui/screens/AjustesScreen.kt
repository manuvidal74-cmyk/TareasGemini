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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.cloud.CloudSyncState
import com.example.data.cloud.CloudSyncStatus
import com.example.data.model.CalendarViewMode
import com.example.data.model.TemaMode
import com.example.ui.TareasUiState
import com.example.ui.theme.LocalAppColors
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AjustesScreen(
    uiState: TareasUiState,
    userEmail: String?,
    userDisplayName: String?,
    cloudSyncState: CloudSyncState,
    onSyncNow: () -> Unit,
    onSignOutClick: () -> Unit,
    hasNotificationPermission: Boolean,
    canExactAlarms: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onToggleGlobalNotifications: (Boolean) -> Unit,
    onToggleDailySummary: (Boolean, String?) -> Unit,
    onTestNotification: () -> Unit,
    onExportExcelClick: () -> Unit,
    onTemaChange: (TemaMode) -> Unit,
    onVistaDefectoChange: (CalendarViewMode) -> Unit,
    onDiasPreavisoChange: (Int) -> Unit,
    onMostrarRealizadasCalChange: (Boolean) -> Unit,
    onSaveBackupJson: () -> Unit,
    onShareBackupJson: () -> Unit,
    onRestoreBackupJson: () -> Unit,
    onLoadSampleData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val settings = uiState.settings
    var showSummaryTimePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ajustes_screen"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Google Account & Cloud Sync Card
        item(key = "cloud_account_card") {
            SettingsCard(title = "☁️ Cuenta de Google y Nube") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = appColors.cardSubtleBg,
                    border = BorderStroke(1.dp, appColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (cloudSyncState.status) {
                            CloudSyncStatus.SYNCING -> Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = "Sincronizando",
                                tint = appColors.blue,
                                modifier = Modifier.size(26.dp),
                            )
                            CloudSyncStatus.SYNCED -> Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = "Sincronizado",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(26.dp),
                            )
                            CloudSyncStatus.ERROR -> Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = "Sin conexión",
                                tint = appColors.amber,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (!userDisplayName.isNullOrBlank()) {
                                Text(
                                    text = userDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = userEmail ?: "Cuenta Google conectada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val lastSyncText = cloudSyncState.lastSyncedAtMillis?.let {
                                SimpleDateFormat("HH:mm:ss", Locale("es", "ES")).format(Date(it))
                            }
                            Text(
                                text = when (cloudSyncState.status) {
                                    CloudSyncStatus.SYNCING -> "Sincronizando con la nube..."
                                    CloudSyncStatus.SYNCED -> if (lastSyncText != null) {
                                        "✓ Nube sincronizada ($lastSyncText)"
                                    } else {
                                        "✓ Nube sincronizada"
                                    }
                                    CloudSyncStatus.ERROR -> cloudSyncState.errorMessage ?: "Pendiente de conexión"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (cloudSyncState.status) {
                                    CloudSyncStatus.SYNCED -> appColors.green
                                    CloudSyncStatus.ERROR -> appColors.amber
                                    CloudSyncStatus.SYNCING -> appColors.blue
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = cloudSyncState.status != CloudSyncStatus.SYNCING,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("ajustes_sync_now_button"),
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sincronizar")
                    }
                    OutlinedButton(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.red),
                        border = BorderStroke(1.dp, appColors.red.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("ajustes_sign_out_button"),
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cerrar sesión")
                    }
                }
            }
        }

        // Local Notifications Card
        item(key = "notif_card") {
            SettingsCard(title = "🔔 Notificaciones Locales") {
                if (!hasNotificationPermission) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
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
                                "Permiso de notificaciones pendiente en Android.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = onRequestNotificationPermission) {
                                Text("Conceder")
                            }
                        }
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (canExactAlarms) appColors.bannerOkBg else appColors.amberContainer,
                        border = BorderStroke(
                            1.dp,
                            if (canExactAlarms) appColors.green.copy(alpha = 0.4f) else appColors.amber
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (canExactAlarms) {
                                    "✓ Alarmas exactas activas: los avisos llegarán puntuales."
                                } else {
                                    "Alarmas exactas desactivadas en Android (los avisos pueden retrasarse)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canExactAlarms) appColors.green else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (!canExactAlarms) {
                                OutlinedButton(onClick = onOpenExactAlarmSettings) {
                                    Text("Configurar")
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Recordatorios de tareas y avisos", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Notificaciones locales programadas aunque la app esté cerrada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.notificacionesGlobalesActivas,
                        onCheckedChange = onToggleGlobalNotifications,
                        modifier = Modifier.testTag("global_notifications_switch"),
                    )
                }

                HorizontalDivider(color = appColors.borderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Resumen matinal de tareas diarias", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Notifica cada día a las ${settings.horaResumenDiario} tus rutinas diarias y vencimientos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.resumenDiarioActivo,
                        onCheckedChange = { onToggleDailySummary(it, null) },
                        modifier = Modifier.testTag("daily_summary_switch"),
                    )
                }

                if (settings.resumenDiarioActivo) {
                    OutlinedButton(
                        onClick = { showSummaryTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cambiar hora del resumen diario (${settings.horaResumenDiario})")
                    }
                }

                OutlinedButton(
                    onClick = onTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("test_notification_button"),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Probar notificación local ahora")
                }
            }
        }

        // Excel Export Card
        item(key = "excel_card") {
            SettingsCard(title = "📊 Exportación a Excel (100 % sin conexión)") {
                Text(
                    "Genera un archivo de hoja de cálculo (.xls) estructurado en dos pestañas: «Tareas» y «Agenda y Diario».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onExportExcelClick,
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.green, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Text("📊 Exportar archivo consolidado Excel")
                }
            }
        }

        // Appearance & Calendar Card
        item(key = "appearance_card") {
            SettingsCard(title = "⚙️ Apariencia, Calendario y Avisos") {
                Text("Tema de color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TemaMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.tema == mode,
                            onClick = { onTemaChange(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        TemaMode.SISTEMA -> "🌓 Automático"
                                        TemaMode.CLARO -> "☀️ Claro"
                                        TemaMode.OSCURO -> "🌙 Oscuro"
                                    }
                                )
                            },
                        )
                    }
                }

                Text("Vista de calendario preferida", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalendarViewMode.entries.forEach { viewMode ->
                        FilterChip(
                            selected = settings.vistaDefecto == viewMode,
                            onClick = { onVistaDefectoChange(viewMode) },
                            label = { Text(viewMode.label) },
                        )
                    }
                }

                Text("Días de antelación para avisos próximos", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 7).forEach { days ->
                        FilterChip(
                            selected = settings.diasPreaviso == days,
                            onClick = { onDiasPreavisoChange(days) },
                            label = { Text("$days ${if (days == 1) "día" else "días"}") },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Mostrar tareas realizadas en el calendario", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.mostrarRealizadasCal,
                        onCheckedChange = onMostrarRealizadasCalChange,
                    )
                }
            }
        }

        // JSON Backup Card
        item(key = "backup_card") {
            SettingsCard(title = "💾 Copia de seguridad (.json)") {
                Text(
                    "Compatible al 100 % con copias exportadas desde tu versión web index.html.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (settings.ultimoBackup.isNullOrBlank()) {
                        "Última copia de seguridad: Nunca realizada"
                    } else {
                        "Última copia de seguridad: ${DateUtils.formatDateTimeES(settings.ultimoBackup)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = appColors.headingText,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSaveBackupJson,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                    ) {
                        Text("⬇️ Guardar .json")
                    }
                    OutlinedButton(
                        onClick = onShareBackupJson,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                    ) {
                        Text("📤 Compartir")
                    }
                }
                OutlinedButton(
                    onClick = onRestoreBackupJson,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                ) {
                    Text("⬆️ Restaurar copia (.json)")
                }
            }
        }

        // Sample data & About
        item(key = "sample_and_about") {
            SettingsCard(title = "🧪 Datos de prueba e información") {
                OutlinedButton(
                    onClick = onLoadSampleData,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                ) {
                    Text("Cargar tareas diarias y ejemplos")
                }
                HorizontalDivider(color = appColors.borderSubtle)
                Text("Tareas — Versión Android Nativa", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Aplicación creada por Manuel Vidal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSummaryTimePicker) {
        val parts = settings.horaResumenDiario.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val initMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val timeState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showSummaryTimePicker = false },
            title = { Text("Hora del resumen diario") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = String.format(Locale.US, "%02d:%02d", timeState.hour, timeState.minute)
                    onToggleDailySummary(true, formatted)
                    showSummaryTimePicker = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showSummaryTimePicker = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val appColors = LocalAppColors.current
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = appColors.headingText,
            )
            content()
        }
    }
}
