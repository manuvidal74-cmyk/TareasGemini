package com.example.util

import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.example.data.model.TaskType

object ExcelExporter {
    private fun escapeXml(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildSheetXml(
        sheetName: String,
        headers: List<String>,
        rows: List<List<String>>,
        columnWidths: List<Int>,
    ): String {
        val colsXml = columnWidths.joinToString("") { """<Column ss:AutoFitWidth="0" ss:Width="$it"/>""" }
        val headerCells = headers.joinToString("") {
            """<Cell ss:StyleID="sHeader"><Data ss:Type="String">${escapeXml(it)}</Data></Cell>"""
        }
        val rowsXml = rows.joinToString("\n") { row ->
            val cells = row.joinToString("") { value ->
                """<Cell ss:StyleID="sCell"><Data ss:Type="String">${escapeXml(value)}</Data></Cell>"""
            }
            "<Row>$cells</Row>"
        }

        return """
  <Worksheet ss:Name="${escapeXml(sheetName)}">
    <Table>
      $colsXml
      <Row ss:Height="22">$headerCells</Row>
      $rowsXml
    </Table>
  </Worksheet>"""
    }

    fun generateSpreadsheetXml(
        tasks: List<TaskItem>,
        diaryEntries: List<DiaryEntry>,
    ): String {
        val taskHeaders = listOf(
            "ID", "Nombre", "Tipo", "Categoría", "Repetición", "Descripción", "Fecha creación",
            "Estado", "Traspasado a Google", "Fecha de finalización",
            "Relevancia", "Fecha prevista"
        )

        val taskRows = if (tasks.isNotEmpty()) {
            tasks.map { t ->
                listOf(
                    t.id,
                    t.titulo,
                    if (t.tipo == TaskType.AVISO) "Aviso" else "Tarea",
                    t.categoria,
                    if (t.repeticion.shortLabel.isNotBlank()) t.repeticion.shortLabel else "Una vez",
                    t.notas,
                    DateUtils.formatDateTimeES(t.fechaCreacion),
                    if (t.completada) "Completada" else "Pendiente",
                    if (t.traspasado) "Sí" else "No",
                    DateUtils.formatDateTimeES(t.fechaCompletada),
                    t.relevancia.storageValue.uppercase(),
                    DateUtils.formatDateES(t.fecha) + if (t.hora.isNotBlank()) " ${t.hora}" else ""
                )
            }
        } else {
            listOf(listOf("", "Sin tareas registradas", "", "", "", "", "", "", "", "", "", ""))
        }

        val sortedDiary = diaryEntries.sortedByDescending { "${it.fecha} ${it.hora}" }
        val diaryHeaders = listOf(
            "Fecha", "Hora", "Tipo de Registro", "Tareas vinculadas", "Observaciones/Notas"
        )
        val diaryRows = if (sortedDiary.isNotEmpty()) {
            sortedDiary.map { d ->
                listOf(
                    DateUtils.formatDateES(d.fecha),
                    d.hora,
                    if (d.tipo == "completada") "Tarea completada" else "Observación manual",
                    d.tituloTarea,
                    d.texto
                )
            }
        } else {
            listOf(listOf("", "", "Sin observaciones en el diario", "", ""))
        }

        val sheet1 = buildSheetXml(
            sheetName = "Tareas",
            headers = taskHeaders,
            rows = taskRows,
            columnWidths = listOf(110, 200, 75, 90, 85, 230, 115, 90, 110, 115, 80, 115)
        )
        val sheet2 = buildSheetXml(
            sheetName = "Agenda y Diario",
            headers = diaryHeaders,
            rows = diaryRows,
            columnWidths = listOf(90, 65, 130, 200, 320)
        )

        return """<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
 <Styles>
  <Style ss:ID="Default" ss:Name="Normal">
   <Alignment ss:Vertical="Top" ss:WrapText="1"/>
   <Font ss:FontName="Calibri" ss:Size="11"/>
  </Style>
  <Style ss:ID="sHeader">
   <Alignment ss:Vertical="Center" ss:WrapText="1"/>
   <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>
   <Interior ss:Color="#0F2742" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="sCell">
   <Alignment ss:Vertical="Top" ss:WrapText="1"/>
  </Style>
 </Styles>
$sheet1
$sheet2
</Workbook>"""
    }
}
