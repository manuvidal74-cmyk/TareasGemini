package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val tipo: String,
    val relevancia: String,
    val repeticion: String,
    val fecha: String,
    val hora: String,
    val notas: String,
    val completada: Boolean,
    val traspasado: Boolean,
    val fechaCreacion: String,
    val fechaCompletada: String?,
    val notaCierre: String,
    val notificacionActiva: Boolean = true,
    val minutosAntes: Int = 0,
    val rachaDiaria: Int = 0,
    val ultimaFechaCompletadaDiaria: String? = null,
    val categoria: String = "",
    val diasSemana: String = "",
)

@Entity(tableName = "daily_notes")
data class DailyNoteEntity(
    @PrimaryKey val fecha: String,
    val texto: String,
)

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    val fecha: String,
    val hora: String,
    val fechaHoraISO: String,
    val tipo: String,
    val tareaId: String?,
    val tituloTarea: String,
    val texto: String,
)

fun TaskEntity.toDomain(): TaskItem = TaskItem(
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
    fechaCompletada = fechaCompletada,
    notaCierre = notaCierre,
    notificacionActiva = notificacionActiva,
    minutosAntes = minutosAntes,
    rachaDiaria = rachaDiaria,
    ultimaFechaCompletadaDiaria = ultimaFechaCompletadaDiaria,
    categoria = categoria,
    diasSemana = diasSemana,
)

fun TaskItem.toEntity(): TaskEntity = TaskEntity(
    id = id,
    titulo = titulo,
    tipo = tipo.storageValue,
    relevancia = relevancia.storageValue,
    repeticion = repeticion.storageValue,
    fecha = fecha,
    hora = hora,
    notas = notas,
    completada = completada,
    traspasado = traspasado,
    fechaCreacion = fechaCreacion,
    fechaCompletada = fechaCompletada,
    notaCierre = notaCierre,
    notificacionActiva = notificacionActiva,
    minutosAntes = minutosAntes,
    rachaDiaria = rachaDiaria,
    ultimaFechaCompletadaDiaria = ultimaFechaCompletadaDiaria,
    categoria = categoria,
    diasSemana = diasSemana,
)

fun DiaryEntryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    fecha = fecha,
    hora = hora,
    fechaHoraISO = fechaHoraISO,
    tipo = tipo,
    tareaId = tareaId,
    tituloTarea = tituloTarea,
    texto = texto,
)

fun DiaryEntry.toEntity(): DiaryEntryEntity = DiaryEntryEntity(
    id = id,
    fecha = fecha,
    hora = hora,
    fechaHoraISO = fechaHoraISO,
    tipo = tipo,
    tareaId = tareaId,
    tituloTarea = tituloTarea,
    texto = texto,
)
