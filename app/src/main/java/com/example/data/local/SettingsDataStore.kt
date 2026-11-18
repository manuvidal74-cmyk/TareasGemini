package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.CalendarViewMode
import com.example.data.model.TemaMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tareas_y_avisos_prefs")

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val TEMA = stringPreferencesKey("tema")
        val VISTA_DEFECTO = stringPreferencesKey("vista_defecto")
        val DIAS_PREAVISO = intPreferencesKey("dias_preaviso")
        val MOSTRAR_REALIZADAS_CAL = booleanPreferencesKey("mostrar_realizadas_cal")
        val ULTIMO_BACKUP = stringPreferencesKey("ultimo_backup")
        val NOTIFICACIONES_GLOBALES = booleanPreferencesKey("notificaciones_globales")
        val RESUMEN_DIARIO_ACTIVO = booleanPreferencesKey("resumen_diario_activo")
        val HORA_RESUMEN_DIARIO = stringPreferencesKey("hora_resumen_diario")
        val SEEDED_INITIAL = booleanPreferencesKey("seeded_initial")
        val LAST_SYNCED_UID = stringPreferencesKey("last_synced_uid")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            tema = TemaMode.fromStorage(prefs[Keys.TEMA]),
            vistaDefecto = CalendarViewMode.fromStorage(prefs[Keys.VISTA_DEFECTO]),
            diasPreaviso = prefs[Keys.DIAS_PREAVISO] ?: 3,
            mostrarRealizadasCal = prefs[Keys.MOSTRAR_REALIZADAS_CAL] ?: true,
            ultimoBackup = prefs[Keys.ULTIMO_BACKUP],
            notificacionesGlobalesActivas = prefs[Keys.NOTIFICACIONES_GLOBALES] ?: true,
            resumenDiarioActivo = prefs[Keys.RESUMEN_DIARIO_ACTIVO] ?: true,
            horaResumenDiario = prefs[Keys.HORA_RESUMEN_DIARIO] ?: "09:00",
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun getLastSyncedUid(): String? =
        context.dataStore.data.first()[Keys.LAST_SYNCED_UID]

    suspend fun setLastSyncedUid(uid: String?) {
        context.dataStore.edit {
            if (uid.isNullOrBlank()) it.remove(Keys.LAST_SYNCED_UID)
            else it[Keys.LAST_SYNCED_UID] = uid
        }
    }

    suspend fun isInitialSeeded(): Boolean =
        context.dataStore.data.first()[Keys.SEEDED_INITIAL] ?: false

    suspend fun markInitialSeeded() {
        context.dataStore.edit { it[Keys.SEEDED_INITIAL] = true }
    }

    suspend fun updateTema(tema: TemaMode) {
        context.dataStore.edit { it[Keys.TEMA] = tema.storageValue }
    }

    suspend fun updateVistaDefecto(vista: CalendarViewMode) {
        context.dataStore.edit { it[Keys.VISTA_DEFECTO] = vista.storageValue }
    }

    suspend fun updateDiasPreaviso(dias: Int) {
        context.dataStore.edit { it[Keys.DIAS_PREAVISO] = dias }
    }

    suspend fun updateMostrarRealizadasCal(mostrar: Boolean) {
        context.dataStore.edit { it[Keys.MOSTRAR_REALIZADAS_CAL] = mostrar }
    }

    suspend fun updateUltimoBackup(isoTimestamp: String?) {
        context.dataStore.edit {
            if (isoTimestamp == null) it.remove(Keys.ULTIMO_BACKUP)
            else it[Keys.ULTIMO_BACKUP] = isoTimestamp
        }
    }

    suspend fun updateNotificacionesGlobales(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICACIONES_GLOBALES] = enabled }
    }

    suspend fun updateResumenDiario(enabled: Boolean, hora: String? = null) {
        context.dataStore.edit {
            it[Keys.RESUMEN_DIARIO_ACTIVO] = enabled
            if (hora != null) it[Keys.HORA_RESUMEN_DIARIO] = hora
        }
    }

    suspend fun applyRestoredSettings(settings: AppSettings) {
        context.dataStore.edit {
            it[Keys.TEMA] = settings.tema.storageValue
            it[Keys.VISTA_DEFECTO] = settings.vistaDefecto.storageValue
            it[Keys.DIAS_PREAVISO] = settings.diasPreaviso
            it[Keys.MOSTRAR_REALIZADAS_CAL] = settings.mostrarRealizadasCal
            if (settings.ultimoBackup != null) it[Keys.ULTIMO_BACKUP] = settings.ultimoBackup
            it[Keys.NOTIFICACIONES_GLOBALES] = settings.notificacionesGlobalesActivas
            it[Keys.RESUMEN_DIARIO_ACTIVO] = settings.resumenDiarioActivo
            it[Keys.HORA_RESUMEN_DIARIO] = settings.horaResumenDiario
        }
    }
}
