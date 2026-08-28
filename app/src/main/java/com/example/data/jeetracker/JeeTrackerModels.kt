package com.example.data.jeetracker

data class JeeTask(
    val id: String,
    val title: String,
    val subject: String,
    val grade: String,
    val phase: String,
    val targetDetails: String,
    val totalDuration: String? = null,
    val lectures: String? = null,
    val isKeyMilestone: Boolean = false
)

data class JeeMockDetails(
    val title: String,
    val timing: String,
    val description: String
)

data class JeeDayPlan(
    val dayNumber: Int,
    val month: Int,
    val week: Int,
    val dayOfWeek: String,
    val theme: String,
    val isMockDay: Boolean,
    val isBufferDay: Boolean,
    val tasks: List<JeeTask>,
    val mockDetails: JeeMockDetails? = null
)
