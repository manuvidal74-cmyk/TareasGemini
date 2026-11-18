package com.example.util

sealed interface NoteLine {
    data class ChecklistItem(val lineIndex: Int, val checked: Boolean, val text: String) : NoteLine
    data class PlainText(val text: String) : NoteLine
}

data class EditableChecklistItem(val checked: Boolean, val text: String)

object ChecklistUtils {
    private val checklistRegex = Regex("""^\s*-\s*\[([ xX])\]\s*(.*)$""")
    private val uncheckedPrefixRegex = Regex("""^(\s*-\s*)\[\s\]""")
    private val checkedPrefixRegex = Regex("""^(\s*-\s*)\[[xX]\]""")

    fun hasChecklist(notes: String): Boolean {
        if (notes.isBlank()) return false
        return notes.lines().any { checklistRegex.matches(it) }
    }

    fun progress(notes: String): Pair<Int, Int>? {
        if (notes.isBlank()) return null
        var total = 0
        var done = 0
        notes.lines().forEach { line ->
            val match = checklistRegex.matchEntire(line)
            if (match != null) {
                total++
                if (match.groupValues[1].equals("x", ignoreCase = true)) done++
            }
        }
        return if (total > 0) done to total else null
    }

    fun extractParts(notes: String): Pair<String, List<EditableChecklistItem>> {
        if (notes.isBlank()) return "" to emptyList()
        val plainLines = mutableListOf<String>()
        val items = mutableListOf<EditableChecklistItem>()
        notes.lines().forEach { line ->
            val match = checklistRegex.matchEntire(line)
            if (match != null) {
                val checked = match.groupValues[1].equals("x", ignoreCase = true)
                val text = match.groupValues[2]
                items.add(EditableChecklistItem(checked, text))
            } else {
                plainLines.add(line)
            }
        }
        return plainLines.joinToString("\n").trim() to items
    }

    fun composeNotes(plainNotes: String, items: List<EditableChecklistItem>): String {
        val cleanPlain = plainNotes.trim()
        val checklistLines = items
            .mapNotNull { item ->
                val text = item.text.trim()
                if (text.isEmpty()) null
                else "- [${if (item.checked) "x" else " "}] $text"
            }
        return buildString {
            if (cleanPlain.isNotEmpty()) append(cleanPlain)
            if (checklistLines.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(checklistLines.joinToString("\n"))
            }
        }
    }

    fun parseNotes(notes: String): List<NoteLine> {
        return notes.lines().mapIndexed { idx, line ->
            val match = checklistRegex.matchEntire(line)
            if (match != null) {
                val checked = match.groupValues[1].equals("x", ignoreCase = true)
                val text = match.groupValues[2]
                NoteLine.ChecklistItem(idx, checked, text)
            } else {
                NoteLine.PlainText(line)
            }
        }
    }

    fun toggleLine(notes: String, lineIndex: Int): String {
        val lines = notes.lines().toMutableList()
        if (lineIndex !in lines.indices) return notes
        val line = lines[lineIndex]
        lines[lineIndex] = when {
            uncheckedPrefixRegex.containsMatchIn(line) ->
                line.replace(uncheckedPrefixRegex, "$1[x]")
            checkedPrefixRegex.containsMatchIn(line) ->
                line.replace(checkedPrefixRegex, "$1[ ]")
            else -> line
        }
        return lines.joinToString("\n")
    }
}
