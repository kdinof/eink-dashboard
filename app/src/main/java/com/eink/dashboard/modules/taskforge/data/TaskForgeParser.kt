package com.eink.dashboard.modules.taskforge.data

import com.eink.dashboard.modules.taskforge.TaskForgeSettings
import com.eink.dashboard.modules.taskforge.model.TaskForgeBoard
import com.eink.dashboard.modules.taskforge.model.TaskForgePriority
import com.eink.dashboard.modules.taskforge.model.TaskForgeStatus
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import com.eink.dashboard.modules.taskforge.model.TaskSourceRef
import java.security.MessageDigest
import java.time.LocalDate

/** Pure UTF-8 parser for inline Obsidian Tasks. It never rewrites or normalizes the source. */
object TaskForgeParser {
    private val taskPattern = Regex("^(\\s*)[-*+]\\s+\\[([^]])]\\s+(.*)$")
    private val headingPattern = Regex("^(#{2,3})\\s+(.+?)\\s*$")
    private val duePattern = Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val tagPattern = Regex("(?<![\\p{L}\\p{N}_/])#([\\p{L}\\p{N}_/-]+)")
    private val datedMetadata = Regex("[⏳🛫➕✅]\\s*\\d{4}-\\d{2}-\\d{2}")
    private val recurrencePattern = Regex("🔁\\s*([^#📅⏳🛫➕✅]+)")
    private val wikiLink = Regex("\\[\\[([^]|]+)(?:\\|([^]]+))?]]")

    fun parse(bytes: ByteArray): List<TaskForgeTask> {
        val tasks = mutableListOf<TaskForgeTask>()
        val occurrences = mutableMapOf<String, Int>()
        var section: String? = null
        var subsection: String? = null
        var offset = 0
        var sourceOrder = 0

        while (offset <= bytes.size) {
            val end = bytes.indexOfByte('\n'.code.toByte(), offset).let { if (it < 0) bytes.size else it }
            val lineBytes = bytes.copyOfRange(offset, end).let { raw ->
                if (raw.lastOrNull() == '\r'.code.toByte()) raw.copyOf(raw.size - 1) else raw
            }
            val line = lineBytes.toString(Charsets.UTF_8)
            headingPattern.matchEntire(line)?.let { match ->
                val title = cleanMarkdown(match.groupValues[2])
                if (match.groupValues[1].length == 2) {
                    section = title
                    subsection = null
                } else {
                    subsection = title
                }
            }

            taskPattern.matchEntire(line)?.let { match ->
                val marker = match.groupValues[2].singleOrNull() ?: '?'
                val body = match.groupValues[3]
                val hash = sha256(lineBytes)
                val occurrence = occurrences.getOrDefault(hash, 0)
                occurrences[hash] = occurrence + 1
                val checkboxInLine = line.indexOf('[') + 1
                tasks += TaskForgeTask(
                    title = taskTitle(body),
                    status = TaskForgeStatus.from(marker),
                    due = duePattern.findAll(body).mapNotNull { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }.lastOrNull(),
                    priority = priority(body),
                    tags = tagPattern.findAll(body).map { it.groupValues[1] }.toSet(),
                    section = section,
                    subsection = subsection,
                    recurrence = recurrencePattern.find(body)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty),
                    indentLevel = match.groupValues[1].replace("\t", "    ").length / 2,
                    sourceOrder = sourceOrder++,
                    source = TaskSourceRef(hash, occurrence, (offset + checkboxInLine).toLong()),
                )
            }

            if (end == bytes.size) break
            offset = end + 1
        }
        return tasks
    }

    fun board(tasks: List<TaskForgeTask>, settings: TaskForgeSettings, today: LocalDate): TaskForgeBoard {
        val selectedTags = settings.selectedTags
        val filtered = tasks.asSequence()
            .filter { it.status.isOpen }
            .filter { selectedTags.isEmpty() || it.tags.any(selectedTags::contains) }
            .filter { task ->
                when (settings.view) {
                    TaskForgeView.TODAY -> task.due == today
                    TaskForgeView.TODAY_OVERDUE -> task.due?.let { !it.isAfter(today) } == true
                    TaskForgeView.NEXT_7_DAYS -> task.due?.let { !it.isBefore(today) && it.isBefore(today.plusDays(7)) } == true
                    TaskForgeView.ALL_OPEN -> true
                }
            }
            .sortedWith(compareBy<TaskForgeTask> { it.due ?: LocalDate.MAX }
                .thenByDescending { it.priority.rank }
                .thenBy { it.sourceOrder })
            .toList()
        return TaskForgeBoard(
            view = settings.view,
            tasks = filtered.take(settings.limit).toList(),
            totalMatches = filtered.size,
            availableTags = tasks.asSequence().filter { it.status.isOpen }.flatMap { it.tags.asSequence() }.toSortedSet(),
        )
    }

    fun resolveCheckboxOffset(bytes: ByteArray, source: TaskSourceRef): Long? =
        parse(bytes).filter { it.source.lineHash == source.lineHash }.getOrNull(source.occurrence)?.source?.checkboxByteOffset

    private fun priority(body: String): TaskForgePriority = when {
        "🔺" in body -> TaskForgePriority.HIGHEST
        "⏫" in body -> TaskForgePriority.HIGH
        "🔼" in body -> TaskForgePriority.MEDIUM
        "🔽" in body -> TaskForgePriority.LOW
        "⏬" in body -> TaskForgePriority.LOWEST
        else -> TaskForgePriority.NORMAL
    }

    private fun taskTitle(body: String): String {
        var result = body
        result = duePattern.replace(result, "")
        result = datedMetadata.replace(result, "")
        result = recurrencePattern.replace(result, "")
        TaskForgePriority.entries.filter { it.glyph.isNotEmpty() }.forEach { result = result.replace(it.glyph, "") }
        result = tagPattern.replace(result, "")
        result = result.replace("📅", "")
        return cleanMarkdown(result).replace(Regex("\\s+"), " ").trim(' ', '·')
    }

    private fun cleanMarkdown(value: String): String = wikiLink.replace(value) { it.groupValues[2].ifBlank { it.groupValues[1] } }
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .trim()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun ByteArray.indexOfByte(value: Byte, start: Int): Int {
        for (index in start until size) if (this[index] == value) return index
        return -1
    }
}
