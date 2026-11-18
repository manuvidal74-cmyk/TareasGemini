package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskType
import java.io.File

object ExternalIntents {
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun openGoogleCalendar(context: Context, task: TaskItem) {
        val summary = buildString {
            append(task.titulo)
            if (task.notas.isNotBlank()) {
                append("\n")
                append(task.notas)
            }
        }
        copyToClipboard(context, task.titulo, summary)

        val compactDate = task.fecha.replace("-", "")
        val datesParam = if (task.hora.contains(":")) {
            val parts = task.hora.split(":")
            val h = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            val start = "${compactDate}T${h}${m}00"
            val endHour = ((h.toIntOrNull() ?: 9) + 1) % 24
            val end = "${compactDate}T${endHour.toString().padStart(2, '0')}${m}00"
            "$start/$end"
        } else {
            "$compactDate/$compactDate"
        }

        val rruleParam = when (task.repeticion) {
            TaskRecurrence.DIARIA -> "&recur=RRULE:FREQ=DAILY"
            TaskRecurrence.SEMANAL -> "&recur=RRULE:FREQ=WEEKLY"
            TaskRecurrence.MENSUAL -> "&recur=RRULE:FREQ=MONTHLY"
            TaskRecurrence.ANUAL -> "&recur=RRULE:FREQ=YEARLY"
            TaskRecurrence.NINGUNA -> ""
        }

        val prefix = if (task.tipo == TaskType.TAREA) "[Tarea] " else "[Aviso] "
        val url = "https://calendar.google.com/calendar/render?action=TEMPLATE" +
            "&text=${Uri.encode(prefix + task.titulo)}" +
            "&details=${Uri.encode(task.notas)}" +
            "&dates=$datesParam$rruleParam"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openGoogleTasks(context: Context, task: TaskItem) {
        val text = buildString {
            append(task.titulo)
            if (task.fecha.isNotBlank()) append(" (${DateUtils.formatDateES(task.fecha)})")
            if (task.notas.isNotBlank()) append(" - ${task.notas}")
        }
        copyToClipboard(context, "Google Tasks", text)
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://tasks.google.com/embed/?origin=https://calendar.google.com")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun shareTask(context: Context, task: TaskItem) {
        val typeLabel = if (task.tipo == TaskType.AVISO) "🔔 Aviso" else "📌 Tarea"
        val shareText = buildString {
            appendLine("$typeLabel: ${task.titulo}")
            append("📅 Fecha: ${DateUtils.formatDateES(task.fecha)}")
            if (task.hora.isNotBlank()) append(" a las ${task.hora}")
            if (task.repeticion != TaskRecurrence.NINGUNA) {
                append("\n🔁 Frecuencia: ${task.repeticion.shortLabel}")
            }
            if (task.notas.isNotBlank()) {
                append("\n📝 Notas:\n${task.notas}")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, task.titulo)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir tarea").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun shareExportedFile(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String,
    ) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir archivo").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
