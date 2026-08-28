package com.example.data.jeetracker

object JeeTrackerData {
    val ALL_120_DAYS: List<JeeDayPlan> =
        JeeTrackerMonth1.days() + JeeTrackerMonth2.days() + JeeTrackerMonth3.days() + JeeTrackerMonth4.days()

    val TOTAL_TASKS_COUNT: Int = ALL_120_DAYS.sumOf { it.tasks.size }
    val TOTAL_MOCKS_COUNT: Int = ALL_120_DAYS.count { it.isMockDay }

    val ALL_TASKS_MAP: Map<String, Pair<JeeTask, JeeDayPlan>> = buildMap {
        ALL_120_DAYS.forEach { day -> day.tasks.forEach { task -> put(task.id, task to day) } }
    }
}
