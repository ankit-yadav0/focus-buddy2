package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.jeetracker.JeeCustomTaskEntity
import com.example.data.jeetracker.JeeDayPlan
import com.example.data.jeetracker.JeeDayProgressEntity
import com.example.data.jeetracker.JeeMockRecordEntity
import com.example.data.jeetracker.JeeTrackerData
import com.example.data.jeetracker.JeeTrackerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JeeFilterState(
    val month: Int? = 1, // null = all months
    val week: Int? = null,
    val subject: String? = null,
    val status: JeeStatusFilter = JeeStatusFilter.ALL,
    val searchQuery: String = ""
)

enum class JeeStatusFilter { ALL, PENDING, COMPLETED, MOCK_ONLY, BOOKMARKED }

data class JeeTrackerUiState(
    val allDays: List<JeeDayPlan> = JeeTrackerData.ALL_120_DAYS,
    val progressByDay: Map<Int, JeeDayProgressEntity> = emptyMap(),
    val customTasksByDay: Map<Int, List<JeeCustomTaskEntity>> = emptyMap(),
    val mockRecordsByDay: Map<Int, JeeMockRecordEntity> = emptyMap(),
    val filters: JeeFilterState = JeeFilterState(),
    val isLoading: Boolean = true
) {
    val totalTasksCount: Int get() = JeeTrackerData.TOTAL_TASKS_COUNT

    val completedTasksCount: Int
        get() = allDays.sumOf { day ->
            val done = progressByDay[day.dayNumber]?.completedTaskIds
                ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            day.tasks.count { it.id in done }
        }

    val completedDaysCount: Int
        get() = allDays.count { day ->
            val done = progressByDay[day.dayNumber]?.completedTaskIds
                ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            day.tasks.isNotEmpty() && day.tasks.all { it.id in done }
        }

    val overallPercent: Int
        get() = if (totalTasksCount > 0) (completedTasksCount * 100 / totalTasksCount) else 0

    val currentStreak: Int
        get() {
            var streak = 0
            for (dayNum in 1..120) {
                val day = allDays.find { it.dayNumber == dayNum } ?: break
                val done = progressByDay[dayNum]?.completedTaskIds
                    ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                val isDone = day.tasks.isNotEmpty() && day.tasks.all { it.id in done }
                if (isDone) streak++ else break
            }
            return streak
        }

    fun isDayComplete(day: JeeDayPlan): Boolean {
        val done = progressByDay[day.dayNumber]?.completedTaskIds
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        return day.tasks.isNotEmpty() && day.tasks.all { it.id in done }
    }

    val filteredDays: List<JeeDayPlan>
        get() = allDays.filter { day ->
            if (filters.month != null && day.month != filters.month) return@filter false
            if (filters.week != null && day.week != filters.week) return@filter false
            if (filters.subject != null && day.tasks.none { it.subject == filters.subject }) return@filter false

            val prog = progressByDay[day.dayNumber]
            val isComplete = isDayComplete(day)
            when (filters.status) {
                JeeStatusFilter.PENDING -> if (isComplete) return@filter false
                JeeStatusFilter.COMPLETED -> if (!isComplete) return@filter false
                JeeStatusFilter.MOCK_ONLY -> if (!day.isMockDay) return@filter false
                JeeStatusFilter.BOOKMARKED -> if (prog?.isBookmarked != true) return@filter false
                JeeStatusFilter.ALL -> {}
            }

            val q = filters.searchQuery.trim().lowercase()
            if (q.isNotEmpty()) {
                val matchesDay = day.dayNumber.toString() == q || "day $q".contains(q)
                val matchesTheme = day.theme.lowercase().contains(q)
                val matchesTasks = day.tasks.any {
                    it.title.lowercase().contains(q) ||
                        it.subject.lowercase().contains(q) ||
                        it.targetDetails.lowercase().contains(q) ||
                        (it.lectures?.lowercase()?.contains(q) == true)
                }
                if (!matchesDay && !matchesTheme && !matchesTasks) return@filter false
            }
            true
        }
}

class JeeTrackerViewModel(private val repository: JeeTrackerRepository) : ViewModel() {

    private val filtersFlow = MutableStateFlow(JeeFilterState())

    val uiState: StateFlow<JeeTrackerUiState> = combine(
        repository.progressFlow(),
        repository.customTasksFlow(),
        repository.mockRecordsFlow(),
        filtersFlow
    ) { progress, customTasks, mockRecords, filters ->
        JeeTrackerUiState(
            progressByDay = progress.associateBy { it.dayNumber },
            customTasksByDay = customTasks.groupBy { it.dayNumber },
            mockRecordsByDay = mockRecords.associateBy { it.dayNumber },
            filters = filters,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JeeTrackerUiState())

    fun updateFilters(transform: (JeeFilterState) -> JeeFilterState) {
        filtersFlow.value = transform(filtersFlow.value)
    }

    fun resetFilters() {
        filtersFlow.value = JeeFilterState(month = null)
    }

    fun toggleTask(dayNumber: Int, taskId: String) {
        val day = JeeTrackerData.ALL_120_DAYS.find { it.dayNumber == dayNumber } ?: return
        val current = uiState.value.progressByDay[dayNumber]
        viewModelScope.launch {
            repository.toggleTask(current, dayNumber, taskId, day.tasks.map { it.id })
        }
    }

    fun toggleAllDay(dayNumber: Int, markDone: Boolean) {
        val day = JeeTrackerData.ALL_120_DAYS.find { it.dayNumber == dayNumber } ?: return
        val current = uiState.value.progressByDay[dayNumber]
        viewModelScope.launch {
            repository.toggleAllDay(current, dayNumber, day.tasks.map { it.id }, markDone)
        }
    }

    fun toggleBookmark(dayNumber: Int) {
        val current = uiState.value.progressByDay[dayNumber]
        viewModelScope.launch { repository.toggleBookmark(current, dayNumber) }
    }

    fun updateNotes(dayNumber: Int, notes: String) {
        val current = uiState.value.progressByDay[dayNumber]
        viewModelScope.launch { repository.updateNotes(current, dayNumber, notes) }
    }

    fun addStudyMinutes(dayNumber: Int, minutes: Int) {
        val current = uiState.value.progressByDay[dayNumber]
        viewModelScope.launch { repository.addStudyMinutes(current, dayNumber, minutes) }
    }

    fun addCustomTask(dayNumber: Int, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repository.addCustomTask(dayNumber, text.trim()) }
    }

    fun toggleCustomTask(task: JeeCustomTaskEntity) {
        viewModelScope.launch { repository.toggleCustomTask(task) }
    }

    fun deleteCustomTask(id: Long) {
        viewModelScope.launch { repository.deleteCustomTask(id) }
    }

    fun saveMockRecord(record: JeeMockRecordEntity) {
        viewModelScope.launch { repository.saveMockRecord(record) }
    }

    fun deleteMockRecord(dayNumber: Int) {
        viewModelScope.launch { repository.deleteMockRecord(dayNumber) }
    }

    fun resetProgress() {
        viewModelScope.launch { repository.resetAll() }
    }
}

class JeeTrackerViewModelFactory(
    private val repository: JeeTrackerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JeeTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JeeTrackerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
