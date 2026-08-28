package com.example.data.jeetracker

import kotlinx.coroutines.flow.Flow

class JeeTrackerRepository(private val dao: JeeTrackerDao) {

    fun progressFlow(): Flow<List<JeeDayProgressEntity>> = dao.getAllProgress()
    fun customTasksFlow(): Flow<List<JeeCustomTaskEntity>> = dao.getAllCustomTasks()
    fun mockRecordsFlow(): Flow<List<JeeMockRecordEntity>> = dao.getAllMockRecords()

    suspend fun toggleTask(
        current: JeeDayProgressEntity?,
        dayNumber: Int,
        taskId: String,
        allTaskIdsForDay: List<String>
    ) {
        val existingIds = current?.completedTaskIds
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toMutableList() ?: mutableListOf()

        if (existingIds.contains(taskId)) {
            existingIds.remove(taskId)
        } else {
            existingIds.add(taskId)
        }

        val isComplete = allTaskIdsForDay.isNotEmpty() && allTaskIdsForDay.all { existingIds.contains(it) }

        dao.upsertProgress(
            (current ?: JeeDayProgressEntity(dayNumber = dayNumber)).copy(
                completedTaskIds = existingIds.joinToString(","),
                isDayCompleted = isComplete
            )
        )
    }

    suspend fun toggleAllDay(current: JeeDayProgressEntity?, dayNumber: Int, allTaskIds: List<String>, markDone: Boolean) {
        dao.upsertProgress(
            (current ?: JeeDayProgressEntity(dayNumber = dayNumber)).copy(
                completedTaskIds = if (markDone) allTaskIds.joinToString(",") else "",
                isDayCompleted = markDone
            )
        )
    }

    suspend fun toggleBookmark(current: JeeDayProgressEntity?, dayNumber: Int) {
        dao.upsertProgress(
            (current ?: JeeDayProgressEntity(dayNumber = dayNumber)).copy(
                isBookmarked = !(current?.isBookmarked ?: false)
            )
        )
    }

    suspend fun updateNotes(current: JeeDayProgressEntity?, dayNumber: Int, notes: String) {
        dao.upsertProgress((current ?: JeeDayProgressEntity(dayNumber = dayNumber)).copy(notes = notes))
    }

    suspend fun addStudyMinutes(current: JeeDayProgressEntity?, dayNumber: Int, minutes: Int) {
        dao.upsertProgress(
            (current ?: JeeDayProgressEntity(dayNumber = dayNumber)).copy(
                studyMinutesLogged = (current?.studyMinutesLogged ?: 0) + minutes
            )
        )
    }

    suspend fun addCustomTask(dayNumber: Int, text: String) {
        dao.upsertCustomTask(JeeCustomTaskEntity(dayNumber = dayNumber, text = text))
    }

    suspend fun toggleCustomTask(task: JeeCustomTaskEntity) {
        dao.upsertCustomTask(task.copy(completed = !task.completed))
    }

    suspend fun deleteCustomTask(id: Long) {
        dao.deleteCustomTask(id)
    }

    suspend fun saveMockRecord(record: JeeMockRecordEntity) {
        dao.upsertMockRecord(record)
    }

    suspend fun deleteMockRecord(dayNumber: Int) {
        dao.deleteMockRecord(dayNumber)
    }

    suspend fun resetAll() {
        dao.resetProgress()
        dao.resetCustomTasks()
        dao.resetMockRecords()
    }
}
