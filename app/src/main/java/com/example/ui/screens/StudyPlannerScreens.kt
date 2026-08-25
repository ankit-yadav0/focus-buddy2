package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.planner.StudyPlanCalculator
import com.example.planner.StudyPlanInput
import com.example.planner.StudyPlanResult
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private enum class PlanLineType { HEADER, TASK, PLAIN }
private data class PlanLine(val index: Int, val type: PlanLineType, val text: String)

/** Splits the generated plan text into individually renderable lines. Lines starting
with "- " (the task lines StudyPlanCalculator emits, e.g. "- Chemistry: 4.0 hours")
become interactive checkboxes; "Day N" header lines are bold and non-interactive;
everything else (title, warnings, blank lines, "(Free / buffer day)") is plain text. */
private fun parsePlanLines(planText: String): List<PlanLine> {
    return planText.lines().mapIndexed { index, rawLine ->
        val trimmed = rawLine.trim()
        // Strip any leading emoji/symbol characters (e.g. "📅 ") before checking
        // for the "DAY" header prefix, since the calculator's output format uses
        // an emoji prefix and uppercase "DAY" rather than plain "Day ".
        val alphaStart = trimmed.dropWhile { !it.isLetterOrDigit() }
        val type = when {
            trimmed.startsWith("- ") -> PlanLineType.TASK
            alphaStart.startsWith("DAY ", ignoreCase = true) -> PlanLineType.HEADER
            else -> PlanLineType.PLAIN
        }
        PlanLine(index, type, rawLine)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanTaskRow(
    line: PlanLine,
    isChecked: Boolean,
    onCheck: () -> Unit,
    onLongPressUncheck: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0.6f,
        animationSpec = tween(durationMillis = 300),
        label = "checkScale"
    )
    val textColor by animateColorAsState(
        targetValue = if (isChecked) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.9f),
        animationSpec = tween(durationMillis = 300),
        label = "checkTextColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (!isChecked) onCheck() },
                onLongClick = { if (isChecked) onLongPressUncheck() }
            )
            .padding(vertical = 4.dp)
            .testTag("plan_task_row_${line.index}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed - long-press to undo",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = line.text.trim().removePrefix("- "),
            color = textColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp,
            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanDashboardScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
    onNavigateToPyq: () -> Unit = {}
) {
    var savedPlanText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var checkedLines by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    var currentStreakCount by remember { mutableStateOf(0) }
    var streakFreezeTokensUsed by remember { mutableStateOf(0) }
    var streakFreezeMonth by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        savedPlanText = viewModel.getSetting("saved_study_plan")
        val savedChecked = viewModel.getSetting("study_plan_checked_lines")
        checkedLines = savedChecked
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        currentStreakCount = viewModel.getSetting("current_streak_count")?.toIntOrNull() ?: 0
        streakFreezeTokensUsed = viewModel.getSetting("streak_freeze_tokens_used")?.toIntOrNull() ?: 0
        streakFreezeMonth = viewModel.getSetting("streak_freeze_month") ?: ""
        isLoading = false
    }

    fun persistCheckedLines(updated: Set<Int>) {
        checkedLines = updated
        coroutineScope.launch {
            viewModel.saveSetting("study_plan_checked_lines", updated.joinToString(","))
            
            val createdDateStr = viewModel.getSetting("study_plan_created_date")
            val plan = savedPlanText
            if (!createdDateStr.isNullOrBlank() && !plan.isNullOrBlank()) {
                try {
                    val createdDate = java.time.LocalDate.parse(createdDateStr)
                    val today = java.time.LocalDate.now()
                    val dayNumber = java.time.temporal.ChronoUnit.DAYS.between(createdDate, today).toInt() + 1
                    
                    val planLines = parsePlanLines(plan)
                    val tasksForDay = mutableListOf<PlanLine>()
                    var currentDayNum: Int? = null
                    for (line in planLines) {
                        if (line.type == PlanLineType.HEADER) {
                            val trimmed = line.text.trim()
                            val alphaStart = trimmed.dropWhile { !it.isLetterOrDigit() }
                            if (alphaStart.startsWith("DAY ", ignoreCase = true)) {
                                val parts = alphaStart.split("\\s+".toRegex(), limit = 3)
                                currentDayNum = parts.getOrNull(1)?.toIntOrNull()
                            }
                        } else if (line.type == PlanLineType.TASK) {
                            if (currentDayNum == dayNumber) {
                                tasksForDay.add(line)
                            }
                        }
                    }
                    
                    if (tasksForDay.isNotEmpty() && tasksForDay.all { updated.contains(it.index) }) {
                        val todayStr = today.toString()
                        val lastStreakDateStr = viewModel.getSetting("last_streak_date")
                        
                        if (lastStreakDateStr != todayStr) {
                            var nextStreak = 0
                            var nextTokensUsed = viewModel.getSetting("streak_freeze_tokens_used")?.toIntOrNull() ?: 0
                            val currentMonthStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").format(today)
                            var nextMonth = viewModel.getSetting("streak_freeze_month") ?: currentMonthStr
                            
                            val currentStreak = viewModel.getSetting("current_streak_count")?.toIntOrNull() ?: 0
                            
                            if (lastStreakDateStr.isNullOrBlank()) {
                                nextStreak = 1
                            } else {
                                val lastDate = java.time.LocalDate.parse(lastStreakDateStr)
                                val yesterday = today.minusDays(1)
                                if (lastDate == yesterday) {
                                    nextStreak = currentStreak + 1
                                } else {
                                    if (nextMonth != currentMonthStr) {
                                        nextTokensUsed = 0
                                        nextMonth = currentMonthStr
                                    }
                                    if (nextTokensUsed < 2) {
                                        nextTokensUsed += 1
                                        nextStreak = currentStreak + 1
                                    } else {
                                        nextStreak = 1
                                    }
                                }
                            }
                            
                            viewModel.saveSetting("current_streak_count", nextStreak.toString())
                            viewModel.saveSetting("last_streak_date", todayStr)
                            viewModel.saveSetting("streak_freeze_tokens_used", nextTokensUsed.toString())
                            viewModel.saveSetting("streak_freeze_month", nextMonth)
                            
                            currentStreakCount = nextStreak
                            streakFreezeTokensUsed = nextTokensUsed
                            streakFreezeMonth = nextMonth
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Dashboard", color = Color.White) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToPyq,
                        modifier = Modifier.testTag("pyq_practice_button")
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = "PYQ Practice", tint = Color.White)
                    }
                    if (!savedPlanText.isNullOrBlank()) {
                        IconButton(
                            onClick = onEditPlan,
                            modifier = Modifier.testTag("edit_plan_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Plan", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("dashboard_loader"),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                val plan = savedPlanText
                if (plan.isNullOrBlank()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "No Plan Icon",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Study Plan Found",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create a custom-tailored study plan outlining daily sub-tasks. You can check items off as you progress, complete offline in flight mode, and stay aligned with your ultimate study schedule.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onEditPlan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("generate_plan_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Icon")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate My Study Plan", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Active study plan display
                    val plan = savedPlanText ?: ""
                    val subjectProgressList = remember(plan, checkedLines) {
                        if (plan.isBlank()) {
                            emptyList()
                        } else {
                            val lines = parsePlanLines(plan).filter { it.type == PlanLineType.TASK }
                            val subjectsMap = mutableMapOf<String, Pair<Double, Double>>() // subjectName -> Pair(checkedHours, totalHours)

                            for (line in lines) {
                                val cleanLine = line.text.trim().removePrefix("- ").trim()
                                val colonIndex = cleanLine.indexOf(':')
                                if (colonIndex != -1) {
                                    val subjectName = cleanLine.substring(0, colonIndex).trim()
                                    val hoursPart = cleanLine.substring(colonIndex + 1).trim()
                                    val hoursIndex = hoursPart.indexOf(" hours")
                                    val hoursStr = if (hoursIndex != -1) hoursPart.substring(0, hoursIndex).trim() else hoursPart
                                    val hoursValue = hoursStr.toDoubleOrNull() ?: 0.0

                                    val isChecked = checkedLines.contains(line.index)
                                    val currentPair = subjectsMap.getOrDefault(subjectName, Pair(0.0, 0.0))
                                    val newCheckedHours = currentPair.first + (if (isChecked) hoursValue else 0.0)
                                    val newTotalHours = currentPair.second + hoursValue

                                    subjectsMap[subjectName] = Pair(newCheckedHours, newTotalHours)
                                }
                            }

                            subjectsMap.map { (subjectName, hoursPair) ->
                                Triple(subjectName, hoursPair.first, hoursPair.second)
                            }.filter { it.third > 0.0 }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .testTag("study_streak_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "🔥",
                                    fontSize = 32.sp
                                )
                                Column {
                                    Text(
                                        text = "$currentStreakCount day streak",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val currentMonth = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").format(java.time.LocalDate.now())
                                    val tokensUsedForCurrentMonth = if (streakFreezeMonth == currentMonth) streakFreezeTokensUsed else 0
                                    val Y = (2 - tokensUsedForCurrentMonth).coerceIn(0, 2)
                                    Text(
                                        text = "($Y freezes left this month)",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (subjectProgressList.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .testTag("subject_progress_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Overall Progress by Subject",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    
                                    subjectProgressList.forEach { (subjectName, checkedHours, totalHours) ->
                                        val progress = (checkedHours / totalHours).toFloat().coerceIn(0f, 1f)
                                        val percent = (progress * 100).toInt()
                                        
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = subjectName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                                Text(
                                                    text = "$percent%",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .testTag("subject_progress_${subjectName.lowercase()}"),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = Color.White.copy(alpha = 0.1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_plan_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Tracking Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Plan Progress Tracker",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap any task below to check it off. Long-press a completed task to uncheck it.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(16.dp))

                                val planLines = remember(plan) { parsePlanLines(plan) }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    planLines.forEach { line ->
                                        when (line.type) {
                                            PlanLineType.HEADER -> {
                                                if (line.text.isNotBlank()) {
                                                    Text(
                                                        text = line.text.trim(),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                                    )
                                                }
                                            }
                                            PlanLineType.TASK -> {
                                                PlanTaskRow(
                                                    line = line,
                                                    isChecked = checkedLines.contains(line.index),
                                                    onCheck = { persistCheckedLines(checkedLines + line.index) },
                                                    onLongPressUncheck = { persistCheckedLines(checkedLines - line.index) }
                                                )
                                            }
                                            PlanLineType.PLAIN -> {
                                                if (line.text.isNotBlank()) {
                                                    Text(
                                                        text = line.text,
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 13.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        lineHeight = 19.sp
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private data class SubjectState(
    val name: String = "",
    val lectures: String = "",
    val hrsPerLec: String = "",
    val speed: String = "1.0",
    val isFoundational: Boolean = false,
    val colorHex: String = "#4CAF50"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlannerInputScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var normalDays by remember { mutableStateOf("5") }
    var normalHoursPerDay by remember { mutableStateOf("4.0") }
    var specialDays by remember { mutableStateOf("2") }
    var specialHoursPerDay by remember { mutableStateOf("6.0") }
    var practiceReservePercent by remember { mutableStateOf("15") }
    var revisionLastDay by remember { mutableStateOf(true) }

    var subjectsList by remember { mutableStateOf<List<SubjectState>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val savedInput = viewModel.getSetting("saved_study_planner_input")
        if (!savedInput.isNullOrBlank()) {
            val parsed = StudyPlanCalculator.deserializeInput(savedInput)
            if (parsed != null) {
                normalDays = parsed.normalDays.toString()
                normalHoursPerDay = parsed.normalHoursPerDay.toString()
                specialDays = parsed.specialDays.toString()
                specialHoursPerDay = parsed.specialHoursPerDay.toString()
                practiceReservePercent = parsed.practiceReservePercent.toInt().toString()
                revisionLastDay = parsed.revisionLastDay
                subjectsList = parsed.subjects.map {
                    SubjectState(
                        name = it.name,
                        lectures = it.lectures.toString(),
                        hrsPerLec = it.hrsPerLec.toString(),
                        speed = it.speed.toString(),
                        isFoundational = it.isFoundational,
                        colorHex = it.colorHex
                    )
                }
            }
        } else {
            // Default with one empty subject to guide the user
            subjectsList = listOf(SubjectState())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Study Planner", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("planner_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Plan Your Study Journey",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set your study metrics and subjects. We will calculate lecture playback, subtract your revision reserve, and generate a day-by-day study calendar.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // 1. GLOBAL STUDY METRICS HEADER CARDS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.04f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Global Study Metrics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Row 1: Normal Days & Normal Hours/Day
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = normalDays,
                            onValueChange = { normalDays = it },
                            label = { Text("Normal Days") },
                            placeholder = { Text("e.g. 5") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("normal_days_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        OutlinedTextField(
                            value = normalHoursPerDay,
                            onValueChange = { normalHoursPerDay = it },
                            label = { Text("Normal Hours/Day") },
                            placeholder = { Text("e.g. 4.0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("normal_hours_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // Row 2: Special/Mega Days & Special Hours/Day
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = specialDays,
                            onValueChange = { specialDays = it },
                            label = { Text("Special/Mega Days") },
                            placeholder = { Text("e.g. 2") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("special_days_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        OutlinedTextField(
                            value = specialHoursPerDay,
                            onValueChange = { specialHoursPerDay = it },
                            label = { Text("Special Hours/Day") },
                            placeholder = { Text("e.g. 6.0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("special_hours_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // Row 3: Practice Reserve % & Checkbox for "Revision Last Day"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = practiceReservePercent,
                            onValueChange = { practiceReservePercent = it },
                            label = { Text("Practice Reserve %") },
                            placeholder = { Text("e.g. 15") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.1f).testTag("practice_reserve_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .weight(0.9f)
                                .clickable { revisionLastDay = !revisionLastDay }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Checkbox(
                                checked = revisionLastDay,
                                onCheckedChange = { revisionLastDay = it },
                                modifier = Modifier.testTag("revision_last_day_checkbox")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Revision\nLast Day",
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // 2. DYNAMIC SUBJECTS LIST CONTAINER
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subjects & Lectures",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Button(
                        onClick = {
                            subjectsList = subjectsList + SubjectState()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("add_subject_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Subject Icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Subject", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (subjectsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.02f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No subjects added. Tap '+ Add Subject' to start.",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    subjectsList.forEachIndexed { index, subject ->
                        val cardBorderColor = remember(subject.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(subject.colorHex)).copy(alpha = 0.5f)
                            } catch (e: Exception) {
                                Color.White.copy(alpha = 0.08f)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("subject_card_$index"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.03f)
                            ),
                            border = BorderStroke(1.5.dp, cardBorderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Element A: Full Width Row for Subject Name, Color Picker & Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = subject.name,
                                        onValueChange = { newValue ->
                                            subjectsList = subjectsList.mapIndexed { idx, item ->
                                                if (idx == index) item.copy(name = newValue) else item
                                            }
                                        },
                                        label = { Text("Subject Name") },
                                        placeholder = { Text("e.g. Physics") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("subject_name_$index"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )

                                    var showColorPicker by remember { mutableStateOf(false) }
                                    val currentColor = remember(subject.colorHex) {
                                        try { Color(android.graphics.Color.parseColor(subject.colorHex)) } catch (e: Exception) { Color(0xFF4CAF50) }
                                    }

                                    Box(modifier = Modifier.wrapContentSize()) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(currentColor)
                                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                                .clickable { showColorPicker = true }
                                                .testTag("color_picker_$index")
                                        )

                                        DropdownMenu(
                                            expanded = showColorPicker,
                                            onDismissRequest = { showColorPicker = false },
                                            modifier = Modifier.background(Color(0xFF1E1E1E))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf(
                                                    "#4CAF50", // Green
                                                    "#2196F3", // Blue
                                                    "#E91E63", // Pink/Magenta
                                                    "#FF9800", // Orange
                                                    "#9C27B0", // Purple
                                                    "#00BCD4", // Cyan
                                                    "#FFC107"  // Amber/Gold
                                                ).forEach { hex ->
                                                    val col = Color(android.graphics.Color.parseColor(hex))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(col)
                                                            .clickable {
                                                                subjectsList = subjectsList.mapIndexed { idx, item ->
                                                                    if (idx == index) item.copy(colorHex = hex) else item
                                                                }
                                                                showColorPicker = false
                                                            }
                                                            .testTag("color_select_${hex.replace("#", "")}_$index")
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            subjectsList = subjectsList.filterIndexed { idx, _ -> idx != index }
                                        },
                                        modifier = Modifier.testTag("delete_subject_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Subject",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Element B (Single Compact Row for Sub-Metrics)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = subject.lectures,
                                        onValueChange = { newValue ->
                                            subjectsList = subjectsList.mapIndexed { idx, item ->
                                                if (idx == index) item.copy(lectures = newValue) else item
                                            }
                                        },
                                        label = { Text("Lectures", fontSize = 11.sp) },
                                        placeholder = { Text("e.g. 16") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("subject_lectures_$index"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )

                                    OutlinedTextField(
                                        value = subject.hrsPerLec,
                                        onValueChange = { newValue ->
                                            subjectsList = subjectsList.mapIndexed { idx, item ->
                                                if (idx == index) item.copy(hrsPerLec = newValue) else item
                                            }
                                        },
                                        label = { Text("Hrs/Lec", fontSize = 11.sp) },
                                        placeholder = { Text("e.g. 2.0") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f).testTag("subject_hrs_$index"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )

                                    OutlinedTextField(
                                        value = subject.speed,
                                        onValueChange = { newValue ->
                                            subjectsList = subjectsList.mapIndexed { idx, item ->
                                                if (idx == index) item.copy(speed = newValue) else item
                                            }
                                        },
                                        label = { Text("Speed", fontSize = 11.sp) },
                                        placeholder = { Text("e.g. 1.25") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        enabled = !subject.isFoundational,
                                        modifier = Modifier.weight(1f).testTag("subject_speed_$index"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            disabledBorderColor = Color.White.copy(alpha = 0.05f),
                                            disabledLabelColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }

                                // Foundational checkbox options row beneath it
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            subjectsList = subjectsList.mapIndexed { idx, item ->
                                                if (idx == index) item.copy(isFoundational = !item.isFoundational) else item
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = subject.isFoundational,
                                        onCheckedChange = { isChecked ->
                                            subjectsList = subjectsList.mapIndexed { idx, item ->
                                                if (idx == index) item.copy(isFoundational = isChecked) else item
                                            }
                                        },
                                        modifier = Modifier.testTag("subject_foundational_$index")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Foundational Subject (No playback speed scaling)",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. GENERATE PLAN BUTTON
            val isInputValid = remember(subjectsList, normalDays, normalHoursPerDay, specialDays, specialHoursPerDay, practiceReservePercent) {
                val nDays = normalDays.toIntOrNull() ?: 0
                val nHours = normalHoursPerDay.toDoubleOrNull() ?: 0.0
                val sDays = specialDays.toIntOrNull() ?: 0
                val sHours = specialHoursPerDay.toDoubleOrNull() ?: 0.0
                val reserve = practiceReservePercent.toDoubleOrNull() ?: -1.0
                
                nDays >= 0 && nHours >= 0.0 && sDays >= 0 && sHours >= 0.0 && reserve in 0.0..100.0 &&
                (nDays + sDays) > 0 &&
                subjectsList.isNotEmpty() && subjectsList.all {
                    it.name.isNotBlank() && (it.lectures.toIntOrNull() ?: 0) > 0 && (it.hrsPerLec.toDoubleOrNull() ?: 0.0) > 0.0
                }
            }

            Button(
                onClick = {
                    if (isInputValid && !isSaving) {
                        isSaving = true
                        val nDays = normalDays.toInt()
                        val nHours = normalHoursPerDay.toDouble()
                        val sDays = specialDays.toInt()
                        val sHours = specialHoursPerDay.toDouble()
                        val reserve = practiceReservePercent.toDouble()

                        val inputToSave = com.example.planner.StudyPlanInput(
                            normalDays = nDays,
                            normalHoursPerDay = nHours,
                            specialDays = sDays,
                            specialHoursPerDay = sHours,
                            practiceReservePercent = reserve,
                            revisionLastDay = revisionLastDay,
                            subjects = subjectsList.map {
                                com.example.planner.SubjectInput(
                                    name = it.name,
                                    lectures = it.lectures.toInt(),
                                    hrsPerLec = it.hrsPerLec.toDouble(),
                                    speed = it.speed.toDoubleOrNull() ?: 1.0,
                                    isFoundational = it.isFoundational,
                                    colorHex = it.colorHex
                                )
                            }
                        )

                        val result = StudyPlanCalculator.generate(inputToSave)

                        coroutineScope.launch {
                            viewModel.saveSetting("saved_study_plan", result.formattedText)
                            viewModel.saveSetting("study_plan_created_date", java.time.LocalDate.now().toString())
                            viewModel.saveSetting("study_plan_checked_lines", "")
                            viewModel.saveSetting("syllabus_100_celebrated", "false")
                            viewModel.saveSetting("saved_study_planner_input", StudyPlanCalculator.serializeInput(inputToSave))
                            isSaving = false
                            onSaved()
                        }
                    }
                },
                enabled = isInputValid && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_activate_plan_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = "Done")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Plan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
