package com.example.data.model

enum class TaskType(val storageValue: String, val label: String) {
    TAREA("tarea", "Tarea"),
    AVISO("aviso", "Aviso");

    companion object {
        fun fromStorage(value: String?): TaskType =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: TAREA
    }
}

enum class TaskRelevance(val storageValue: String, val label: String, val weight: Int) {
    ALTA("alta", "Alta", 3),
    MEDIA("media", "Media", 2),
    BAJA("baja", "Baja", 1);

    companion object {
        fun fromStorage(value: String?): TaskRelevance =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: MEDIA
    }
}

enum class TaskRecurrence(val storageValue: String, val label: String, val shortLabel: String) {
    NINGUNA("ninguna", "No repetir (una sola vez)", ""),
    DIARIA("diaria", "Cada día (Diaria)", "Diaria"),
    SEMANAL("semanal", "Cada semana (Semanal)", "Semanal"),
    MENSUAL("mensual", "Cada mes (Mensual)", "Mensual"),
    ANUAL("anual", "Cada año (Anual)", "Anual");

    companion object {
        fun fromStorage(value: String?): TaskRecurrence =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: NINGUNA
    }
}

enum class CalendarViewMode(val storageValue: String, val label: String) {
    MES("mes", "Mes"),
    SEMANA("semana", "Semana"),
    AGENDA("agenda", "Agenda");

    companion object {
        fun fromStorage(value: String?): CalendarViewMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: MES
    }
}

enum class TemaMode(val storageValue: String, val label: String) {
    SISTEMA("sistema", "Automático (según el móvil)"),
    CLARO("claro", "Siempre Claro"),
    OSCURO("oscuro", "Siempre Oscuro");

    companion object {
        fun fromStorage(value: String?): TemaMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: SISTEMA
    }
}

enum class SortOrder(val label: String) {
    FECHA("Por fecha"),
    RELEVANCIA("Relevancia"),
}

enum class AppTab(val label: String) {
    PENDIENTES("Pendientes"),
    CALENDARIO("Calendario"),
    DIARIO("Diario"),
    HISTORICO("Histórico"),
    AJUSTES("Ajustes"),
}

enum class TaskCategory(val storageValue: String, val emoji: String) {
    TRABAJO("Trabajo", "💼"),
    CASA("Casa", "🏠"),
    SALUD("Salud", "💚"),
    TRAMITES("Trámites", "📄"),
    COMPRAS("Compras", "🛒");

    companion object {
        fun findByValue(value: String?): TaskCategory? =
            entries.firstOrNull { it.storageValue.equals(value?.trim(), ignoreCase = true) }
    }
}

data class AppSettings(
    val tema: TemaMode = TemaMode.SISTEMA,
    val vistaDefecto: CalendarViewMode = CalendarViewMode.MES,
    val diasPreaviso: Int = 3,
    val mostrarRealizadasCal: Boolean = true,
    val ultimoBackup: String? = null,
    val notificacionesGlobalesActivas: Boolean = true,
    val resumenDiarioActivo: Boolean = true,
    val horaResumenDiario: String = "09:00",
)

data class TaskItem(
    val id: String,
    val titulo: String,
    val tipo: TaskType = TaskType.TAREA,
    val relevancia: TaskRelevance = TaskRelevance.MEDIA,
    val repeticion: TaskRecurrence = TaskRecurrence.NINGUNA,
    val fecha: String,
    val hora: String = "",
    val notas: String = "",
    val completada: Boolean = false,
    val traspasado: Boolean = false,
    val fechaCreacion: String = "",
    val fechaCompletada: String? = null,
    val notaCierre: String = "",
    val notificacionActiva: Boolean = true,
    val minutosAntes: Int = 0,
    val rachaDiaria: Int = 0,
    val ultimaFechaCompletadaDiaria: String? = null,
    val categoria: String = "",
    val diasSemana: String = "",
)

data class DiaryEntry(
    val id: String,
    val fecha: String,
    val hora: String,
    val fechaHoraISO: String,
    val tipo: String, // "manual" | "completada"
    val tareaId: String? = null,
    val tituloTarea: String = "",
    val texto: String = "",
)

data class CalendarTaskProjection(
    val task: TaskItem,
    val isProjected: Boolean,
    val displayDate: String,
)
