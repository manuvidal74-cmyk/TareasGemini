package com.example.util

import com.example.data.model.AppSettings
import com.example.data.model.CalendarViewMode
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.data.model.TemaMode
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val tasks: List<TaskItem>,
    val dailyNotes: Map<String, String>,
    val diaryEntries: List<DiaryEntry>,
    val settings: AppSettings?,
)

object JsonBackupManager {
    fun exportJson(
        tasks: List<TaskItem>,
        dailyNotes: Map<String, String>,
        diaryEntries: List<DiaryEntry>,
        settings: AppSettings,
        exportedAtISO: String,
    ): String {
        val root = JSONObject()
        root.put("app", "es.manuel.tareasyavisos")
        root.put("version", 10)
        root.put("fechaExportacion", exportedAtISO)

        val tasksArray = JSONArray()
        tasks.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("titulo", t.titulo)
                put("tipo", t.tipo.storageValue)
                put("relevancia", t.relevancia.storageValue)
                put("repeticion", t.repeticion.storageValue)
                put("fecha", t.fecha)
                put("hora", t.hora)
                put("notas", t.notas)
                put("completada", t.completada)
                put("traspasado", t.traspasado)
                put("fechaCreacion", t.fechaCreacion)
                put("fechaCompletada", t.fechaCompletada ?: JSONObject.NULL)
                put("notaCierre", t.notaCierre)
                put("notificacionActiva", t.notificacionActiva)
                put("minutosAntes", t.minutosAntes)
                put("rachaDiaria", t.rachaDiaria)
                put("ultimaFechaCompletadaDiaria", t.ultimaFechaCompletadaDiaria ?: JSONObject.NULL)
                put("categoria", t.categoria)
                put("diasSemana", t.diasSemana)
            }
            tasksArray.put(obj)
        }
        root.put("tareas", tasksArray)

        val notesObj = JSONObject()
        dailyNotes.forEach { (date, text) ->
            if (text.isNotBlank()) notesObj.put(date, text)
        }
        root.put("notasDia", notesObj)

        val diaryArray = JSONArray()
        diaryEntries.forEach { d ->
            val obj = JSONObject().apply {
                put("id", d.id)
                put("fecha", d.fecha)
                put("hora", d.hora)
                put("fechaHoraISO", d.fechaHoraISO)
                put("tipo", d.tipo)
                put("tareaId", d.tareaId ?: JSONObject.NULL)
                put("tituloTarea", d.tituloTarea)
                put("texto", d.texto)
            }
            diaryArray.put(obj)
        }
        root.put("diario", diaryArray)

        val cfgObj = JSONObject().apply {
            put("tema", settings.tema.storageValue)
            put("vistaDefecto", settings.vistaDefecto.storageValue)
            put("diasPreaviso", settings.diasPreaviso)
            put("mostrarRealizadasCal", if (settings.mostrarRealizadasCal) "si" else "no")
            put("ultimoBackup", exportedAtISO)
            put("notificacionesGlobalesActivas", settings.notificacionesGlobalesActivas)
            put("resumenDiarioActivo", settings.resumenDiarioActivo)
            put("horaResumenDiario", settings.horaResumenDiario)
        }
        root.put("config", cfgObj)

        return root.toString(2)
    }

    fun parseBackupJson(rawJson: String): BackupPayload {
        val root = JSONObject(rawJson)
        if (!root.has("tareas")) {
            throw IllegalArgumentException("Formato JSON no válido: falta el campo 'tareas'")
        }

        val tasksArray = root.getJSONArray("tareas")
        val tasks = mutableListOf<TaskItem>()
        for (i in 0 until tasksArray.length()) {
            val obj = tasksArray.getJSONObject(i)
            val id = obj.optString("id").ifBlank { "imp-${System.currentTimeMillis()}-$i" }
            tasks.add(
                TaskItem(
                    id = id,
                    titulo = obj.optString("titulo"),
                    tipo = TaskType.fromStorage(obj.optString("tipo", "tarea")),
                    relevancia = TaskRelevance.fromStorage(obj.optString("relevancia", "media")),
                    repeticion = TaskRecurrence.fromStorage(obj.optString("repeticion", "ninguna")),
                    fecha = obj.optString("fecha", DateUtils.todayISO()),
                    hora = obj.optString("hora", ""),
                    notas = obj.optString("notas", ""),
                    completada = obj.optBoolean("completada", false),
                    traspasado = obj.optBoolean("traspasado", false),
                    fechaCreacion = obj.optString("fechaCreacion", DateUtils.nowISO8601()),
                    fechaCompletada = if (obj.isNull("fechaCompletada")) null else obj.optString("fechaCompletada"),
                    notaCierre = obj.optString("notaCierre", ""),
                    notificacionActiva = obj.optBoolean("notificacionActiva", true),
                    minutosAntes = obj.optInt("minutosAntes", 0),
                    rachaDiaria = obj.optInt("rachaDiaria", 0),
                    ultimaFechaCompletadaDiaria = if (obj.isNull("ultimaFechaCompletadaDiaria")) null else obj.optString("ultimaFechaCompletadaDiaria"),
                    categoria = obj.optString("categoria", ""),
                    diasSemana = obj.optString("diasSemana", ""),
                )
            )
        }

        val dailyNotes = mutableMapOf<String, String>()
        val notesObj = root.optJSONObject("notasDia")
        if (notesObj != null) {
            val keys = notesObj.keys()
            while (keys.hasNext()) {
                val dateKey = keys.next()
                val text = notesObj.optString(dateKey, "")
                if (text.isNotBlank()) dailyNotes[dateKey] = text
            }
        }

        val diaryEntries = mutableListOf<DiaryEntry>()
        val diaryArray = root.optJSONArray("diario")
        if (diaryArray != null) {
            for (i in 0 until diaryArray.length()) {
                val obj = diaryArray.getJSONObject(i)
                val tipo = obj.optString("tipo", "manual")
                if (tipo == "creacion") continue
                diaryEntries.add(
                    DiaryEntry(
                        id = obj.optString("id").ifBlank { "dimp-${System.currentTimeMillis()}-$i" },
                        fecha = obj.optString("fecha", DateUtils.todayISO()),
                        hora = obj.optString("hora", "00:00"),
                        fechaHoraISO = obj.optString("fechaHoraISO", DateUtils.nowISO8601()),
                        tipo = tipo,
                        tareaId = if (obj.isNull("tareaId")) null else obj.optString("tareaId"),
                        tituloTarea = obj.optString("tituloTarea", ""),
                        texto = obj.optString("texto", ""),
                    )
                )
            }
        }

        val cfgObj = root.optJSONObject("config")
        val parsedSettings = cfgObj?.let {
            val mostrarCalRaw = it.opt("mostrarRealizadasCal")
            val mostrarCal = when (mostrarCalRaw) {
                is Boolean -> mostrarCalRaw
                is String -> mostrarCalRaw != "no"
                else -> true
            }
            AppSettings(
                tema = TemaMode.fromStorage(it.optString("tema", "sistema")),
                vistaDefecto = CalendarViewMode.fromStorage(it.optString("vistaDefecto", "mes")),
                diasPreaviso = it.optInt("diasPreaviso", 3),
                mostrarRealizadasCal = mostrarCal,
                ultimoBackup = if (it.isNull("ultimoBackup")) null else it.optString("ultimoBackup"),
                notificacionesGlobalesActivas = it.optBoolean("notificacionesGlobalesActivas", true),
                resumenDiarioActivo = it.optBoolean("resumenDiarioActivo", true),
                horaResumenDiario = it.optString("horaResumenDiario", "09:00"),
            )
        }

        return BackupPayload(
            tasks = tasks,
            dailyNotes = dailyNotes,
            diaryEntries = diaryEntries,
            settings = parsedSettings,
        )
    }
}
