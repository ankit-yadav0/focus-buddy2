package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.jeetracker.JeeCustomTaskEntity
import com.example.data.jeetracker.JeeDayPlan
import com.example.data.jeetracker.JeeMockRecordEntity
import com.example.data.jeetracker.JeeTask
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoLabel
import com.example.ui.theme.MonoNumeralMedium
import com.example.ui.theme.SpaceGroteskFamily
import com.example.viewmodel.JeeStatusFilter
import com.example.viewmodel.JeeTrackerViewModel

private fun subjectColor(subject: String, scheme: ColorScheme): Color = when (subject) {
    "Physics" -> Color(0xFF5CC8FF)
    "Chemistry" -> Color(0xFFFF8A65)
    "Mathematics" -> Color(0xFFB388FF)
    "Mock" -> scheme.error
    "Revision" -> scheme.secondary
    "Buffer" -> scheme.onSurfaceVariant
    else -> scheme.onSurfaceVariant
}

private val ALL_SUBJECTS = listOf("Physics", "Chemistry", "Mathematics", "Mock", "Revision", "Buffer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeeTrackerScreen(
    viewModel: JeeTrackerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var mockDialogDay by remember { mutableStateOf<JeeDayPlan?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "120-Day Master Tracker",
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset progress")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            JeeStatsHeader(
                overallPercent = state.overallPercent,
                completedDays = state.completedDaysCount,
                streak = state.currentStreak,
                completedTasks = state.completedTasksCount,
                totalTasks = state.totalTasksCount
            )

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = state.filters.searchQuery,
                    onValueChange = { q -> viewModel.updateFilters { it.copy(searchQuery = q) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("jee_tracker_search_field"),
                    placeholder = { Text("Search day, topic, or lecture...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.filters.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateFilters { it.copy(searchQuery = "") } }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }

            JeeMonthTabs(
                selectedMonth = state.filters.month,
                onSelectMonth = { m -> viewModel.updateFilters { it.copy(month = m) } }
            )

            JeeStatusFilterRow(
                selected = state.filters.status,
                onSelect = { s -> viewModel.updateFilters { it.copy(status = s) } }
            )

            val filteredDays = state.filteredDays

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${filteredDays.size} of 120 days",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Tap to check off - auto-saved",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filteredDays.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No days match your filters",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.resetFilters() }) {
                            Text("Reset filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDays, key = { it.dayNumber }) { day ->
                        JeeDayCard(
                            day = day,
                            progress = state.progressByDay[day.dayNumber],
                            customTasks = state.customTasksByDay[day.dayNumber].orEmpty(),
                            mockRecord = state.mockRecordsByDay[day.dayNumber],
                            isDayComplete = state.isDayComplete(day),
                            onToggleTask = { taskId -> viewModel.toggleTask(day.dayNumber, taskId) },
                            onToggleAllDay = { done -> viewModel.toggleAllDay(day.dayNumber, done) },
                            onToggleBookmark = { viewModel.toggleBookmark(day.dayNumber) },
                            onUpdateNotes = { notes -> viewModel.updateNotes(day.dayNumber, notes) },
                            onAddCustomTask = { text -> viewModel.addCustomTask(day.dayNumber, text) },
                            onToggleCustomTask = { task -> viewModel.toggleCustomTask(task) },
                            onDeleteCustomTask = { id -> viewModel.deleteCustomTask(id) },
                            onOpenMockDialog = { mockDialogDay = day }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    mockDialogDay?.let { day ->
        JeeMockTestDialog(
            day = day,
            existing = state.mockRecordsByDay[day.dayNumber],
            onDismiss = { mockDialogDay = null },
            onSave = { record -> viewModel.saveMockRecord(record); mockDialogDay = null },
            onDelete = { viewModel.deleteMockRecord(day.dayNumber); mockDialogDay = null }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset all tracker progress?") },
            text = { Text("This clears every checked task, note, and mock score in the 120-day tracker. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetProgress()
                    showResetConfirm = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun JeeStatsHeader(
    overallPercent: Int,
    completedDays: Int,
    streak: Int,
    completedTasks: Int,
    totalTasks: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        JeeStatChip(
            modifier = Modifier.weight(1f),
            label = "OVERALL",
            value = "$overallPercent%",
            accent = MaterialTheme.colorScheme.primary
        )
        JeeStatChip(
            modifier = Modifier.weight(1f),
            label = "DAYS DONE",
            value = "$completedDays/120",
            accent = MaterialTheme.colorScheme.onSurface
        )
        JeeStatChip(
            modifier = Modifier.weight(1f),
            label = "STREAK",
            value = "$streak",
            accent = MaterialTheme.colorScheme.secondary
        )
    }
    LinearProgressIndicator(
        progress = { if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun JeeStatChip(modifier: Modifier = Modifier, label: String, value: String, accent: Color) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MonoNumeralMedium, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

@Composable
private fun JeeMonthTabs(selectedMonth: Int?, onSelectMonth: (Int?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            JeeFilterChip(label = "All", selected = selectedMonth == null, onClick = { onSelectMonth(null) })
        }
        items((1..4).toList()) { m ->
            JeeFilterChip(label = "Month $m", selected = selectedMonth == m, onClick = { onSelectMonth(m) })
        }
    }
}

@Composable
private fun JeeStatusFilterRow(selected: JeeStatusFilter, onSelect: (JeeStatusFilter) -> Unit) {
    val options = listOf(
        JeeStatusFilter.ALL to "All",
        JeeStatusFilter.PENDING to "Pending",
        JeeStatusFilter.COMPLETED to "Completed",
        JeeStatusFilter.MOCK_ONLY to "Mocks",
        JeeStatusFilter.BOOKMARKED to "Bookmarked"
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { (status, label) ->
            JeeFilterChip(label = label, selected = selected == status, onClick = { onSelect(status) })
        }
    }
}

@Composable
private fun JeeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JeeDayCard(
    day: JeeDayPlan,
    progress: com.example.data.jeetracker.JeeDayProgressEntity?,
    customTasks: List<JeeCustomTaskEntity>,
    mockRecord: JeeMockRecordEntity?,
    isDayComplete: Boolean,
    onToggleTask: (String) -> Unit,
    onToggleAllDay: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onUpdateNotes: (String) -> Unit,
    onAddCustomTask: (String) -> Unit,
    onToggleCustomTask: (JeeCustomTaskEntity) -> Unit,
    onDeleteCustomTask: (Long) -> Unit,
    onOpenMockDialog: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val completedIds = progress?.completedTaskIds?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    val doneCount = day.tasks.count { it.id in completedIds }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isDayComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
            .clickable { expanded = !expanded }
            .testTag("jee_day_card_${day.dayNumber}")
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDayComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${day.dayNumber}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDayComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${day.dayOfWeek} - Week ${day.week}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    if (day.isMockDay) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("MOCK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    day.theme,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "$doneCount/${day.tasks.size}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDayComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (progress?.isBookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (progress?.isBookmarked == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))

                day.tasks.forEach { task ->
                    JeeTaskRow(
                        task = task,
                        isChecked = task.id in completedIds,
                        onToggle = { onToggleTask(task.id) }
                    )
                }

                if (customTasks.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    customTasks.forEach { ct ->
                        JeeCustomTaskRow(ct, onToggle = { onToggleCustomTask(ct) }, onDelete = { onDeleteCustomTask(ct.id) })
                    }
                }

                var newTaskText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        modifier = Modifier.weight(1f).testTag("jee_add_custom_task_${day.dayNumber}"),
                        placeholder = { Text("Add your own task...", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    IconButton(onClick = {
                        if (newTaskText.isNotBlank()) {
                            onAddCustomTask(newTaskText)
                            newTaskText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add task", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (day.isMockDay) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenMockDialog,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (mockRecord != null) "Edit Mock Score (${mockRecord.physicsScore + mockRecord.chemistryScore + mockRecord.mathsScore}/300)"
                            else "Log Mock Test Score",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                var notesText by remember(day.dayNumber) { mutableStateOf(progress?.notes ?: "") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth().testTag("jee_notes_${day.dayNumber}"),
                    placeholder = { Text("Notes for this day...", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    minLines = 1,
                    maxLines = 3,
                    trailingIcon = {
                        if (notesText != (progress?.notes ?: "")) {
                            IconButton(onClick = { onUpdateNotes(notesText) }) {
                                Icon(Icons.Default.Check, contentDescription = "Save note", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onToggleAllDay(doneCount != day.tasks.size) }) {
                        Text(if (doneCount == day.tasks.size) "Uncheck all" else "Mark day done", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JeeTaskRow(task: JeeTask, isChecked: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0.6f,
        animationSpec = tween(250),
        label = "jeeTaskCheckScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.size(20.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
            if (isChecked) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(subjectColor(task.subject, MaterialTheme.colorScheme))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    task.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                )
                if (task.isKeyMilestone) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Star, contentDescription = "Milestone", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(12.dp))
                }
            }
            Text(
                "${task.subject} - ${task.targetDetails}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
            if (!task.lectures.isNullOrBlank() || !task.totalDuration.isNullOrBlank()) {
                Text(
                    listOfNotNull(task.lectures, task.totalDuration).joinToString(" - "),
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun JeeCustomTaskRow(task: JeeCustomTaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            task.text,
            modifier = Modifier.weight(1f),
            fontSize = 12.5.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JeeMockTestDialog(
    day: JeeDayPlan,
    existing: JeeMockRecordEntity?,
    onDismiss: () -> Unit,
    onSave: (JeeMockRecordEntity) -> Unit,
    onDelete: () -> Unit
) {
    var physics by remember { mutableStateOf(existing?.physicsScore?.toString() ?: "") }
    var chemistry by remember { mutableStateOf(existing?.chemistryScore?.toString() ?: "") }
    var maths by remember { mutableStateOf(existing?.mathsScore?.toString() ?: "") }
    var mistakes by remember { mutableStateOf(existing?.mistakesCount?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.analysisNotes ?: "") }

    val p = physics.toIntOrNull() ?: 0
    val c = chemistry.toIntOrNull() ?: 0
    val m = maths.toIntOrNull() ?: 0
    val total = p + c + m

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.mockDetails?.title ?: "Mock Test - Day ${day.dayNumber}", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                day.mockDetails?.let {
                    Text(it.timing, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = physics, onValueChange = { physics = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Physics") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                    OutlinedTextField(
                        value = chemistry, onValueChange = { chemistry = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Chem") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                    OutlinedTextField(
                        value = maths, onValueChange = { maths = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Maths") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                Text(
                    "Total: $total / 300",
                    style = MonoNumeralMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = mistakes, onValueChange = { mistakes = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Mistakes count") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Weak topics / analysis notes") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    JeeMockRecordEntity(
                        dayNumber = day.dayNumber,
                        testName = day.mockDetails?.title ?: "Mock Test - Day ${day.dayNumber}",
                        dateMillis = existing?.dateMillis ?: System.currentTimeMillis(),
                        physicsScore = p,
                        chemistryScore = c,
                        mathsScore = m,
                        accuracyPercentage = if (total > 0) (total * 100 / 300) else 0,
                        mistakesCount = mistakes.toIntOrNull() ?: 0,
                        analysisNotes = notes
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
