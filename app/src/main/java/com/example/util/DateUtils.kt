package com.example.util

import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

object DateUtils {
    private val esLocale = Locale.forLanguageTag("es-ES")
    private val monthNames = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )
    val shortWeekDays = listOf("L", "M", "X", "J", "V", "S", "D")

    fun todayISO(): String = formatCalendarToISO(Calendar.getInstance())

    fun nowHHMM(): String {
        val cal = Calendar.getInstance()
        return String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    fun nowISO8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun currentYearMonthPrefix(): String = todayISO().substring(0, 7)

    fun formatCalendarToISO(cal: Calendar): String {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun parseISOToCalendar(isoDate: String?): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (!isoDate.isNullOrBlank()) {
            val parts = isoDate.split("-")
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null) {
                    cal.set(y, m - 1, d, 0, 0, 0)
                }
            }
        }
        return cal
    }

    fun formatDateES(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        val parts = isoDate.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate
    }

    fun formatDateTimeES(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            var parsed: Date? = null
            for (pattern in formats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    if (pattern.endsWith("'Z'")) {
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                    }
                    parsed = sdf.parse(isoString)
                    if (parsed != null) break
                } catch (_: Exception) {
                }
            }
            if (parsed != null) {
                SimpleDateFormat("dd/MM/yyyy HH:mm", esLocale).format(parsed)
            } else {
                isoString
            }
        } catch (_: Exception) {
            isoString
        }
    }

    fun diffDaysFromToday(isoDate: String?): Int {
        if (isoDate.isNullOrBlank()) return 999
        val todayCal = parseISOToCalendar(todayISO())
        val targetCal = parseISOToCalendar(isoDate)
        val diffMs = targetCal.timeInMillis - todayCal.timeInMillis
        return (diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)).roundToInt()
    }

    fun diffDaysBetween(fromISO: String, toISO: String): Int {
        val c1 = parseISOToCalendar(fromISO)
        val c2 = parseISOToCalendar(toISO)
        val diffMs = c2.timeInMillis - c1.timeInMillis
        return (diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)).roundToInt()
    }

    fun addDays(isoDate: String?, days: Int): String {
        val cal = parseISOToCalendar(isoDate ?: todayISO())
        cal.add(Calendar.DAY_OF_MONTH, days)
        return formatCalendarToISO(cal)
    }

    fun isoWeekday(isoDate: String): Int {
        val cal = parseISOToCalendar(isoDate)
        // Calendar.MONDAY=2..SUNDAY=1 -> 1..7 (1=Lunes..7=Domingo)
        return ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    }

    fun nextMondayISO(fromISO: String = todayISO()): String {
        val wd = isoWeekday(fromISO)
        val delta = if (wd == 1) 7 else (8 - wd)
        return addDays(fromISO, delta)
    }

    fun isInCurrentWeek(isoDate: String?): Boolean {
        if (isoDate.isNullOrBlank()) return false
        val weekDays = getWeekDays(todayISO())
        return isoDate in weekDays.first()..weekDays.last()
    }

    fun parseWeekdaysSet(diasSemana: String): Set<Int> {
        if (diasSemana.isBlank()) return emptySet()
        return diasSemana.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
    }

    fun formatWeekdaysLabel(diasSemana: String): String {
        val set = parseWeekdaysSet(diasSemana)
        if (set.isEmpty() || set.size == 7) return ""
        if (set == setOf(1, 2, 3, 4, 5)) return "L-V"
        if (set == setOf(6, 7)) return "S-D"
        return set.sorted().joinToString(" ") { shortWeekDays[it - 1] }
    }

    fun addRecurrenceSteps(isoDateBase: String, recurrence: TaskRecurrence, steps: Int): String {
        if (isoDateBase.isBlank() || recurrence == TaskRecurrence.NINGUNA) return isoDateBase
        val cal = parseISOToCalendar(isoDateBase)
        val yBase = cal.get(Calendar.YEAR)
        val mBase = cal.get(Calendar.MONTH)
        val dBase = cal.get(Calendar.DAY_OF_MONTH)

        return when (recurrence) {
            TaskRecurrence.DIARIA -> {
                cal.add(Calendar.DAY_OF_MONTH, steps)
                formatCalendarToISO(cal)
            }
            TaskRecurrence.SEMANAL -> {
                cal.add(Calendar.DAY_OF_MONTH, 7 * steps)
                formatCalendarToISO(cal)
            }
            TaskRecurrence.MENSUAL -> {
                val totalMonths = mBase + steps
                val newYear = yBase + Math.floorDiv(totalMonths, 12)
                val newMonth = ((totalMonths % 12) + 12) % 12
                val targetCal = Calendar.getInstance().apply {
                    set(newYear, newMonth, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val maxDay = targetCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                targetCal.set(Calendar.DAY_OF_MONTH, minOf(dBase, maxDay))
                formatCalendarToISO(targetCal)
            }
            TaskRecurrence.ANUAL -> {
                val newYear = yBase + steps
                val targetCal = Calendar.getInstance().apply {
                    set(newYear, mBase, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val maxDay = targetCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                targetCal.set(Calendar.DAY_OF_MONTH, minOf(dBase, maxDay))
                formatCalendarToISO(targetCal)
            }
            TaskRecurrence.NINGUNA -> isoDateBase
        }
    }

    fun nextRecurrenceDate(
        isoDateCurrent: String,
        recurrence: TaskRecurrence,
        diasSemana: String = "",
    ): String {
        if (recurrence == TaskRecurrence.DIARIA) {
            val allowed = parseWeekdaysSet(diasSemana)
            if (allowed.isNotEmpty() && allowed.size < 7) {
                for (offset in 1..7) {
                    val candidate = addDays(isoDateCurrent, offset)
                    if (isoWeekday(candidate) in allowed) return candidate
                }
            }
        }
        return addRecurrenceSteps(isoDateCurrent, recurrence, 1)
    }

    fun matchesFutureRecurrence(task: TaskItem, targetDateISO: String): Boolean {
        if (task.completada) return false
        if (task.repeticion == TaskRecurrence.NINGUNA || task.fecha.isBlank()) return false
        if (targetDateISO <= task.fecha) return false

        return when (task.repeticion) {
            TaskRecurrence.DIARIA -> {
                val allowed = parseWeekdaysSet(task.diasSemana)
                allowed.isEmpty() || isoWeekday(targetDateISO) in allowed
            }
            TaskRecurrence.SEMANAL -> {
                val diff = diffDaysBetween(task.fecha, targetDateISO)
                diff > 0 && diff % 7 == 0
            }
            TaskRecurrence.MENSUAL -> {
                for (k in 1..60) {
                    val next = addRecurrenceSteps(task.fecha, TaskRecurrence.MENSUAL, k)
                    if (next == targetDateISO) return true
                    if (next > targetDateISO) return false
                }
                false
            }
            TaskRecurrence.ANUAL -> {
                for (k in 1..25) {
                    val next = addRecurrenceSteps(task.fecha, TaskRecurrence.ANUAL, k)
                    if (next == targetDateISO) return true
                    if (next > targetDateISO) return false
                }
                false
            }
            TaskRecurrence.NINGUNA -> false
        }
    }

    fun getMonthYearLabel(cursorISO: String): String {
        val cal = parseISOToCalendar(cursorISO)
        val m = cal.get(Calendar.MONTH)
        val y = cal.get(Calendar.YEAR)
        return "${monthNames[m]} $y"
    }

    fun getWeekRangeLabel(cursorISO: String): String {
        val days = getWeekDays(cursorISO)
        val first = parseISOToCalendar(days.first())
        val last = parseISOToCalendar(days.last())
        val m1 = monthNames[first.get(Calendar.MONTH)].take(3)
        val m2 = monthNames[last.get(Calendar.MONTH)].take(3)
        return "${first.get(Calendar.DAY_OF_MONTH)} $m1 - ${last.get(Calendar.DAY_OF_MONTH)} $m2"
    }

    fun getWeekDays(cursorISO: String): List<String> {
        val cal = parseISOToCalendar(cursorISO)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Convert Sunday=1..Saturday=7 to Monday=0..Sunday=6
        val offsetFromMonday = (dayOfWeek + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -offsetFromMonday)
        return List(7) { idx ->
            val dayStr = formatCalendarToISO(cal)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            dayStr
        }
    }

    fun getMonthGridDays(cursorISO: String): List<String?> {
        val cal = parseISOToCalendar(cursorISO)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val result = mutableListOf<String?>()
        repeat(offset) { result.add(null) }
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            result.add(formatCalendarToISO(cal))
        }
        return result
    }

    fun shiftMonth(cursorISO: String, deltaMonths: Int): String {
        val cal = parseISOToCalendar(cursorISO)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, deltaMonths)
        return formatCalendarToISO(cal)
    }

    fun computeTriggerMillis(fechaISO: String, horaHHMM: String, minutesBefore: Int): Long {
        val cal = parseISOToCalendar(fechaISO)
        val timeParts = horaHHMM.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, -minutesBefore)
        return cal.timeInMillis
    }

    fun computeNextDailySummaryMillis(horaHHMM: String): Long {
        val parts = horaHHMM.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis
    }
}
