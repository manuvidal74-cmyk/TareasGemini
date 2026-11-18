package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.CloudDiaryEntry
import com.example.data.cloud.CloudTask
import com.example.data.cloud.sanitizeFirestoreDocId
import com.example.data.cloud.toCloudCreateMap
import com.example.data.cloud.toCloudUpdateMap
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskRecurrence
import com.example.data.model.TaskRelevance
import com.example.data.model.TaskType
import com.example.util.ChecklistUtils
import com.example.util.DateUtils
import com.example.util.JsonBackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tareas", appName)
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    }

    @Test
    fun `daily recurrence advances one day and matches future dates`() {
        val next = DateUtils.nextRecurrenceDate("2026-11-12", TaskRecurrence.DIARIA)
        assertEquals("2026-11-13", next)

        val dailyTask = TaskItem(
            id = "t1",
            titulo = "Rutina",
            repeticion = TaskRecurrence.DIARIA,
            fecha = "2026-11-12",
        )
        assertTrue(DateUtils.matchesFutureRecurrence(dailyTask, "2026-11-15"))
    }

    @Test
    fun `checklist toggle and json backup roundtrip`() {
        val toggled = ChecklistUtils.toggleLine("- [ ] Paso 1\n- [ ] Paso 2", 0)
        assertEquals("- [x] Paso 1\n- [ ] Paso 2", toggled)

        val rawJson = """
            {
              "tareas": [
                {
                  "id": "legacy-1",
                  "titulo": "Tarea diaria importada",
                  "tipo": "tarea",
                  "relevancia": "alta",
                  "repeticion": "diaria",
                  "fecha": "2026-11-12",
                  "hora": "08:30",
                  "notas": "",
                  "completada": false,
                  "categoria": "Trabajo",
                  "diasSemana": "1,2,3,4,5"
                }
              ],
              "notasDia": {"2026-11-12": "Nota de prueba"},
              "diario": []
            }
        """.trimIndent()
        val payload = JsonBackupManager.parseBackupJson(rawJson)
        assertEquals(1, payload.tasks.size)
        assertEquals(TaskRecurrence.DIARIA, payload.tasks.first().repeticion)
        assertEquals("Trabajo", payload.tasks.first().categoria)
        assertEquals("1,2,3,4,5", payload.tasks.first().diasSemana)
        assertEquals("Nota de prueba", payload.dailyNotes["2026-11-12"])
    }

    @Test
    fun `weekday recurrence skips weekend and checklist progress works`() {
        // 2026-11-13 is Friday (5). With L-V (1,2,3,4,5), next date must jump to Monday 2026-11-16
        val next = DateUtils.nextRecurrenceDate("2026-11-13", TaskRecurrence.DIARIA, "1,2,3,4,5")
        assertEquals("2026-11-16", next)

        val notes = "Nota general\n- [x] Hecho\n- [ ] Pendiente"
        val progress = ChecklistUtils.progress(notes)
        assertEquals(1 to 2, progress)

        val (plain, items) = ChecklistUtils.extractParts(notes)
        assertEquals("Nota general", plain)
        assertEquals(2, items.size)
        val recomposed = ChecklistUtils.composeNotes(plain, items)
        assertEquals(notes, recomposed)
    }

    @Test
    fun `cloud task and diary payloads match firestore rules and exclude document id`() {
        val task = TaskItem(
            id = "t-123",
            titulo = "Llamar al dentista",
            tipo = TaskType.AVISO,
            relevancia = TaskRelevance.ALTA,
            repeticion = TaskRecurrence.NINGUNA,
            fecha = "2026-11-18",
            hora = "11:30",
            notas = "Pedir revisión",
            completada = false,
            traspasado = false,
            fechaCreacion = "2026-11-18T08:00:00",
            fechaCompletada = null,
            notaCierre = "",
            notificacionActiva = true,
            minutosAntes = 15,
            rachaDiaria = 0,
            ultimaFechaCompletadaDiaria = null,
            categoria = "Salud",
            diasSemana = "",
        )

        val createMap = task.toCloudCreateMap("uid_abc")
        assertFalse(createMap.containsKey("id"))
        assertEquals("uid_abc", createMap["userId"])
        assertEquals("", createMap["fechaCompletada"])
        assertEquals("", createMap["ultimaFechaCompletadaDiaria"])
        assertTrue(createMap.containsKey("createdAt"))
        assertTrue(createMap.containsKey("updatedAt"))

        val updateMap = task.toCloudUpdateMap()
        assertFalse(updateMap.containsKey("id"))
        assertFalse(updateMap.containsKey("userId"))
        assertFalse(updateMap.containsKey("createdAt"))
        assertTrue(updateMap.containsKey("updatedAt"))

        val restoredTask = CloudTask(
            id = "t-123",
            userId = "uid_abc",
            titulo = "Llamar al dentista",
            tipo = "aviso",
            relevancia = "alta",
            repeticion = "ninguna",
            fecha = "2026-11-18",
            hora = "11:30",
            notas = "Pedir revisión",
            fechaCompletada = "",
            ultimaFechaCompletadaDiaria = "",
            minutosAntes = 15L,
            categoria = "Salud",
        ).toDomain()
        assertNull(restoredTask.fechaCompletada)
        assertNull(restoredTask.ultimaFechaCompletadaDiaria)
        assertEquals(TaskType.AVISO, restoredTask.tipo)
        assertEquals(TaskRelevance.ALTA, restoredTask.relevancia)

        val diary = DiaryEntry(
            id = "d-1",
            fecha = "2026-11-18",
            hora = "12:00",
            fechaHoraISO = "2026-11-18T12:00:00",
            tipo = "manual",
            tareaId = null,
            tituloTarea = "",
            texto = "[12:00] Todo listo",
        )
        val diaryCreateMap = diary.toCloudCreateMap("uid_abc")
        assertFalse(diaryCreateMap.containsKey("id"))
        assertEquals("", diaryCreateMap["tareaId"])
        val restoredDiary = CloudDiaryEntry(
            id = "d-1",
            userId = "uid_abc",
            fecha = "2026-11-18",
            hora = "12:00",
            fechaHoraISO = "2026-11-18T12:00:00",
            tipo = "manual",
            tareaId = "",
            texto = "[12:00] Todo listo",
        ).toDomain()
        assertNull(restoredDiary.tareaId)

        val safeId = sanitizeFirestoreDocId("legacy.id/with spaces!@#")
        assertTrue(safeId.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
    }
}
