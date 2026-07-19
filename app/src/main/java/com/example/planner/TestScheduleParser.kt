package com.example.planner

import com.example.data.TestEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

data class TestImportResult(
    val imported: List<TestEntry>,
    val skippedLineNumbers: List<Int>
)

object TestScheduleParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)

    fun parse(rawText: String): TestImportResult {
        val imported = mutableListOf<TestEntry>()
        val skipped = mutableListOf<Int>()

        rawText.lines().forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) return@forEachIndexed

            try {
                val parts = trimmedLine.split("|")
                if (parts.size != 7) {
                    skipped.add(index + 1)
                    return@forEachIndexed
                }
                val date = LocalDate.parse(parts[0].trim(), dateFormatter)
                val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val testType = parts[1].trim()
                val testNumber = parts[2].trim().toIntOrNull() ?: run {
                    skipped.add(index + 1)
                    return@forEachIndexed
                }
                val level = parts[3].trim()
                val physics = parts[4].trim()
                val chemistry = parts[5].trim()
                val math = parts[6].trim()

                imported.add(
                    TestEntry(
                        dateMillis = dateMillis,
                        testType = testType,
                        testNumber = testNumber,
                        level = level,
                        physicsTopics = physics,
                        chemistryTopics = chemistry,
                        mathTopics = math
                    )
                )
            } catch (e: Exception) {
                skipped.add(index + 1)
            }
        }

        return TestImportResult(imported, skipped)
    }
}
