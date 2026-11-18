package com.example.data.cloud

import androidx.annotation.Keep
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.UUID

enum class CloudSyncStatus {
    SYNCING,
    SYNCED,
    ERROR,
}

data class CloudSyncState(
    val status: CloudSyncStatus = CloudSyncStatus.SYNCING,
    val lastSyncedAtMillis: Long? = null,
    val errorMessage: String? = null,
)

private val INVALID_DOC_ID_REGEX = Regex("[^a-zA-Z0-9_\\-]")

fun sanitizeFirestoreDocId(rawId: String): String {
    val cleaned = rawId.trim().replace(INVALID_DOC_ID_REGEX, "_").take(128)
    return cleaned.ifBlank { "id-${UUID.randomUUID().toString().take(12)}" }
}

@Keep
@IgnoreExtraProperties
data class CloudTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val titulo: String = "",
    val tipo: String = "tarea",
    val relevancia: String = "media",
    val repeticion: String = "ninguna",
    val fecha: String = "",
    val hora: String = "",
    val notas: String = "",
    val completada: Boolean = false,
    val traspasado: Boolean = false,
    val fechaCreacion: String = "",
    val fechaCompletada: String = "",
    val notaCierre: String = "",
    val notificacionActiva: Boolean = true,
    val minutosAntes: Long = 0L,
    val rachaDiaria: Long = 0L,
    val ultimaFechaCompletadaDiaria: String = "",
    val categoria: String = "",
    val diasSemana: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toDomain(): TaskItem = TaskItem(
        id = id,
        titulo = titulo,
        tipo = TaskType.fromStorage(tipo),
        relevancia = TaskRelevance.fromStorage(relevancia),
        repeticion = TaskRecurrence.fromStorage(repeticion),
        fecha = fecha,
        hora = hora,
        notas = notas,
        completada = completada,
        traspasado = traspasado,
        fechaCreacion = fechaCreacion,
        fechaCompletada = fechaCompletada.takeIf { it.isNotBlank() },
        notaCierre = notaCierre,
        notificacionActiva = notificacionActiva,
        minutosAntes = minutosAntes.toInt().coerceIn(0, 10080),
        rachaDiaria = rachaDiaria.toInt().coerceIn(0, 100000),
        ultimaFechaCompletadaDiaria = ultimaFechaCompletadaDiaria.takeIf { it.isNotBlank() },
        categoria = categoria,
        diasSemana = diasSemana,
    )
}

fun TaskItem.toCloudCreateMap(userId: String): Map<String, Any> = mapOf(
    "userId" to userId,
    "titulo" to titulo.trim().ifBlank { "Sin título" }.take(300),
    "tipo" to tipo.storageValue,
    "relevancia" to relevancia.storageValue,
    "repeticion" to repeticion.storageValue,
    "fecha" to fecha.take(20),
    "hora" to hora.take(10),
    "notas" to notas.take(10000),
    "completada" to completada,
    "traspasado" to traspasado,
    "fechaCreacion" to fechaCreacion.take(64),
    "fechaCompletada" to (fechaCompletada ?: "").take(64),
    "notaCierre" to notaCierre.take(4000),
    "notificacionActiva" to notificacionActiva,
    "minutosAntes" to minutosAntes.coerceIn(0, 10080),
    "rachaDiaria" to rachaDiaria.coerceIn(0, 100000),
    "ultimaFechaCompletadaDiaria" to (ultimaFechaCompletadaDiaria ?: "").take(20),
    "categoria" to categoria.take(60),
    "diasSemana" to diasSemana.take(30),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
)

fun TaskItem.toCloudUpdateMap(): Map<String, Any> = mapOf(
    "titulo" to titulo.trim().ifBlank { "Sin título" }.take(300),
    "tipo" to tipo.storageValue,
    "relevancia" to relevancia.storageValue,
    "repeticion" to repeticion.storageValue,
    "fecha" to fecha.take(20),
    "hora" to hora.take(10),
    "notas" to notas.take(10000),
    "completada" to completada,
    "traspasado" to traspasado,
    "fechaCreacion" to fechaCreacion.take(64),
    "fechaCompletada" to (fechaCompletada ?: "").take(64),
    "notaCierre" to notaCierre.take(4000),
    "notificacionActiva" to notificacionActiva,
    "minutosAntes" to minutosAntes.coerceIn(0, 10080),
    "rachaDiaria" to rachaDiaria.coerceIn(0, 100000),
    "ultimaFechaCompletadaDiaria" to (ultimaFechaCompletadaDiaria ?: "").take(20),
    "categoria" to categoria.take(60),
    "diasSemana" to diasSemana.take(30),
    "updatedAt" to FieldValue.serverTimestamp(),
)

@Keep
@IgnoreExtraProperties
data class CloudDailyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fecha: String = "",
    val texto: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

fun dailyNoteCreateMap(userId: String, fecha: String, texto: String): Map<String, Any> = mapOf(
    "userId" to userId,
    "fecha" to fecha.trim().ifBlank { "2026-01-01" }.take(20),
    "texto" to texto.take(20000),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
)

fun dailyNoteUpdateMap(fecha: String, texto: String): Map<String, Any> = mapOf(
    "fecha" to fecha.trim().ifBlank { "2026-01-01" }.take(20),
    "texto" to texto.take(20000),
    "updatedAt" to FieldValue.serverTimestamp(),
)

@Keep
@IgnoreExtraProperties
data class CloudDiaryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fecha: String = "",
    val hora: String = "",
    val fechaHoraISO: String = "",
    val tipo: String = "manual",
    val tareaId: String = "",
    val tituloTarea: String = "",
    val texto: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toDomain(): DiaryEntry = DiaryEntry(
        id = id,
        fecha = fecha,
        hora = hora,
        fechaHoraISO = fechaHoraISO,
        tipo = if (tipo == "completada") "completada" else "manual",
        tareaId = tareaId.takeIf { it.isNotBlank() },
        tituloTarea = tituloTarea,
        texto = texto,
    )
}

fun DiaryEntry.toCloudCreateMap(userId: String): Map<String, Any> = mapOf(
    "userId" to userId,
    "fecha" to fecha.trim().ifBlank { "2026-01-01" }.take(20),
    "hora" to hora.take(10),
    "fechaHoraISO" to fechaHoraISO.take(64),
    "tipo" to if (tipo == "completada") "completada" else "manual",
    "tareaId" to (tareaId ?: "").take(128),
    "tituloTarea" to tituloTarea.take(300),
    "texto" to texto.take(20000),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
)

fun DiaryEntry.toCloudUpdateMap(): Map<String, Any> = mapOf(
    "fecha" to fecha.trim().ifBlank { "2026-01-01" }.take(20),
    "hora" to hora.take(10),
    "fechaHoraISO" to fechaHoraISO.take(64),
    "tipo" to if (tipo == "completada") "completada" else "manual",
    "tareaId" to (tareaId ?: "").take(128),
    "tituloTarea" to tituloTarea.take(300),
    "texto" to texto.take(20000),
    "updatedAt" to FieldValue.serverTimestamp(),
)
