package com.example.planner

import java.util.Locale

data class SubjectInput(
    val name: String,
    val lectures: Int,
    val hrsPerLec: Double,
    val speed: Double,
    val isFoundational: Boolean,
    val colorHex: String = "#4CAF50"
)

data class StudyPlanInput(
    val normalDays: Int,
    val normalHoursPerDay: Double,
    val specialDays: Int,
    val specialHoursPerDay: Double,
    val practiceReservePercent: Double,
    val revisionLastDay: Boolean,
    val subjects: List<SubjectInput>
)

data class StudyPlanResult(
    val formattedText: String,
    val estimatedTotalHours: Double,
    val subjectAllocations: Map<String, Double>
)

object StudyPlanCalculator {
    fun generate(input: StudyPlanInput): StudyPlanResult {
        // Calculate Total Hours Available
        val totalNormalHours = input.normalDays * input.normalHoursPerDay
        val totalSpecialHours = input.specialDays * input.specialHoursPerDay
        val totalHoursAvailable = totalNormalHours + totalSpecialHours

        // Calculate Subject net hours
        val allocations = mutableMapOf<String, Double>()
        var totalSubjectHoursRequired = 0.0
        val subjectsRequiredHours = input.subjects.map { subject ->
            val netHours = if (subject.isFoundational) {
                subject.lectures * subject.hrsPerLec
            } else {
                val s = if (subject.speed > 0.0) subject.speed else 1.0
                (subject.lectures * subject.hrsPerLec) / s
            }
            allocations[subject.name] = netHours
            totalSubjectHoursRequired += netHours
            subject.name to netHours
        }.toMap()

        val sb = StringBuilder()
        val totalDays = input.normalDays + input.specialDays
        sb.append("📅 STUDY PLAN ROADMAP: $totalDays Days\n")
        sb.append("Total Study Budget: ${String.format(Locale.US, "%.1f", totalHoursAvailable)} Hours\n")
        sb.append("(Practice Reserve: ${String.format(Locale.US, "%.0f", input.practiceReservePercent)}% | Revision on Last Day: ${if (input.revisionLastDay) "Yes" else "No"})\n\n")

        sb.append("Subjects & Lectures Configuration:\n")
        if (input.subjects.isEmpty()) {
            sb.append("- No specific subjects configured. General study & revision active.\n")
        } else {
            input.subjects.forEach { subject ->
                val netHours = subjectsRequiredHours[subject.name] ?: 0.0
                sb.append("- ${subject.name}: ${subject.lectures} lectures @ ${String.format(Locale.US, "%.1f", subject.hrsPerLec)}h")
                if (!subject.isFoundational) {
                    sb.append(" (${subject.speed}x speed)")
                } else {
                    sb.append(" (Foundational)")
                }
                sb.append(" -> ${String.format(Locale.US, "%.1f", netHours)} Net Hours\n")
            }
            sb.append("Total Required Subject Hours: ${String.format(Locale.US, "%.1f", totalSubjectHoursRequired)} Hours\n")
        }
        sb.append("\n")

        // Construct Days config
        data class DayConfig(val dayNum: Int, val isSpecial: Boolean, val hours: Double, var isRevision: Boolean = false)
        val daysList = mutableListOf<DayConfig>()
        for (i in 1..input.normalDays) {
            daysList.add(DayConfig(i, isSpecial = false, hours = input.normalHoursPerDay))
        }
        for (i in 1..input.specialDays) {
            daysList.add(DayConfig(input.normalDays + i, isSpecial = true, hours = input.specialHoursPerDay))
        }

        if (input.revisionLastDay && daysList.isNotEmpty()) {
            daysList.last().isRevision = true
        }

        daysList.forEach { day ->
            val dayLabel = if (day.isSpecial) "Special/Mega Day" else "Normal Day"
            sb.append("📅 DAY ${day.dayNum} ($dayLabel)\n")
            
            if (day.isRevision) {
                sb.append("- Dedicated Revision & Practice Session\n")
                sb.append("- Target Hours: ${String.format(Locale.US, "%.1f", day.hours)} hours\n")
            } else {
                val dailyReserveFraction = input.practiceReservePercent / 100.0
                val dailyStudyHours = day.hours * (1.0 - dailyReserveFraction)
                val dailyPracticeHours = day.hours * dailyReserveFraction

                if (input.subjects.isEmpty()) {
                    sb.append("- General Subject Study: ${String.format(Locale.US, "%.1f", dailyStudyHours)} hours\n")
                    if (dailyPracticeHours > 0.0) {
                        sb.append("- Practice/Review Session: ${String.format(Locale.US, "%.1f", dailyPracticeHours)} hours\n")
                    }
                } else {
                    input.subjects.forEach { subject ->
                        val netReq = subjectsRequiredHours[subject.name] ?: 0.0
                        val subjectShare = if (totalSubjectHoursRequired > 0.0) netReq / totalSubjectHoursRequired else 0.0
                        val subjectHoursOnDay = dailyStudyHours * subjectShare
                        
                        val s = if (subject.speed > 0.0) subject.speed else 1.0
                        val lecTime = if (subject.isFoundational) subject.hrsPerLec else (subject.hrsPerLec / s)
                        val lecsCompletedOnDay = if (lecTime > 0.0) subjectHoursOnDay / lecTime else 0.0

                        if (subjectHoursOnDay > 0.0) {
                            sb.append("- ${subject.name}: ${String.format(Locale.US, "%.1f", subjectHoursOnDay)} hours (~${String.format(Locale.US, "%.1f", lecsCompletedOnDay)} lecs)\n")
                        }
                    }
                    if (dailyPracticeHours > 0.0) {
                        sb.append("- Daily Practice & Review: ${String.format(Locale.US, "%.1f", dailyPracticeHours)} hours\n")
                    }
                }
            }
            sb.append("\n")
        }

        return StudyPlanResult(
            formattedText = sb.toString(),
            estimatedTotalHours = totalHoursAvailable,
            subjectAllocations = allocations
        )
    }

    fun serializeInput(input: StudyPlanInput): String {
        val sb = StringBuilder()
        sb.append(input.normalDays).append("|")
        sb.append(input.normalHoursPerDay).append("|")
        sb.append(input.specialDays).append("|")
        sb.append(input.specialHoursPerDay).append("|")
        sb.append(input.practiceReservePercent).append("|")
        sb.append(if (input.revisionLastDay) "1" else "0").append("|")
        
        val subStr = input.subjects.joinToString("#") { s ->
            "${s.name};${s.lectures};${s.hrsPerLec};${s.speed};${if (s.isFoundational) "1" else "0"};${s.colorHex}"
        }
        sb.append(subStr)
        return sb.toString()
    }

    fun deserializeInput(raw: String): StudyPlanInput? {
        if (raw.isBlank()) return null
        return try {
            val parts = raw.split("|")
            val normalDays = parts[0].toInt()
            val normalHoursPerDay = parts[1].toDouble()
            val specialDays = parts[2].toInt()
            val specialHoursPerDay = parts[3].toDouble()
            val practiceReservePercent = parts[4].toDouble()
            val revisionLastDay = parts[5] == "1"
            
            val subjectsList = mutableListOf<SubjectInput>()
            if (parts.size >= 7 && parts[6].isNotBlank()) {
                val subs = parts[6].split("#")
                subs.forEach { subRaw ->
                    if (subRaw.isNotBlank()) {
                        val sParts = subRaw.split(";")
                        if (sParts.size >= 5) {
                            subjectsList.add(
                                SubjectInput(
                                    name = sParts[0],
                                    lectures = sParts[1].toInt(),
                                    hrsPerLec = sParts[2].toDouble(),
                                    speed = sParts[3].toDouble(),
                                    isFoundational = sParts[4] == "1",
                                    colorHex = if (sParts.size >= 6) sParts[5] else "#4CAF50"
                                )
                            )
                        }
                    }
                }
            }
            StudyPlanInput(
                normalDays = normalDays,
                normalHoursPerDay = normalHoursPerDay,
                specialDays = specialDays,
                specialHoursPerDay = specialHoursPerDay,
                practiceReservePercent = practiceReservePercent,
                revisionLastDay = revisionLastDay,
                subjects = subjectsList
            )
        } catch (e: Exception) {
            null
        }
    }
}
