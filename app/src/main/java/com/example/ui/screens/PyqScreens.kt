package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonOutlinedPillButton
import com.example.ui.components.NeonPrimaryGradientButton
import com.example.ui.components.NeonSquircleIconBadge
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Study Planner Screen matching Panel 6 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Plan Calculator card (Total Lectures, Hours per Lecture, Playback Speed)
 *   • Live Required Study Hours readout (e.g., 40 * 2 / 1.5 = 53.33 hours)
 *   • Plan Duration card (Start Date, End Date with neon magenta calendar icons,
 *     Foundational Subject neon toggle, and "Calculate Plan" primary gradient button)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlannerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var totalLecturesText by remember { mutableStateOf("40") }
    var hoursPerLectureText by remember { mutableStateOf("2") }
    var playbackSpeed by remember { mutableStateOf(1.5f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var foundationalSubject by remember { mutableStateOf(true) }
    var showCalculatedSummary by remember { mutableStateOf(false) }

    val nowMs = remember { System.currentTimeMillis() }
    var startDateMs by remember { mutableLongStateOf(nowMs) }
    var endDateMs by remember { mutableLongStateOf(nowMs + 28L * 24 * 3600 * 1000L) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val startDateStr = remember(startDateMs) { dateFormatter.format(Date(startDateMs)) }
    val endDateStr = remember(endDateMs) { dateFormatter.format(Date(endDateMs)) }

    val lectures = totalLecturesText.toFloatOrNull() ?: 40f
    val hoursPerLec = hoursPerLectureText.toFloatOrNull() ?: 2f
    val rawStudyHours = remember(lectures, hoursPerLec, playbackSpeed, foundationalSubject) {
        val base = (lectures * hoursPerLec) / playbackSpeed.coerceAtLeast(0.5f)
        if (foundationalSubject) base else base * 0.9f
    }
    val totalDays = remember(startDateMs, endDateMs) {
        max(1L, ((endDateMs - startDateMs) / (24L * 3600 * 1000L)).coerceAtLeast(1L))
    }
    val dailyHoursTarget = remember(rawStudyHours, totalDays) {
        rawStudyHours / totalDays.toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Study Planner",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Top Header Card: Plan Calculator
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCardBorderBrush, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NeonSquircleIconBadge(
                                icon = Icons.Default.Calculate,
                                tint = NeonCyan,
                                size = 38.dp
                            )
                            Text(
                                text = "Plan Calculator",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }

                // Inputs Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Total Lectures", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = totalLecturesText,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) totalLecturesText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeonSurface,
                            unfocusedContainerColor = NeonSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )

                    Text("Hours per Lecture", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = hoursPerLectureText,
                        onValueChange = { if (it.length <= 4) hoursPerLectureText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeonSurface,
                            unfocusedContainerColor = NeonSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )

                    Text("Playback Speed", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeonSurface)
                                .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(14.dp))
                                .clickable { speedMenuExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false },
                            modifier = Modifier.background(NeonSurface)
                        ) {
                            listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x", color = Color.White) },
                                    onClick = {
                                        playbackSpeed = speed
                                        speedMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Required Study Hours Readout (Exact Panel 6 style: "53.33 hours")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Required Study Hours",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", rawStudyHours),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "hours",
                            color = TextSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Plan Duration Card (Exact Panel 6 layout)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Plan Duration",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        // Start Date Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeonBlack.copy(alpha = 0.65f))
                                .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(14.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = startDateMs }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val chosen = Calendar.getInstance().apply { set(y, m, d) }
                                            startDateMs = chosen.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Start Date", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(startDateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Start Date",
                                tint = NeonMagenta,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // End Date Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeonBlack.copy(alpha = 0.65f))
                                .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(14.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = endDateMs }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val chosen = Calendar.getInstance().apply { set(y, m, d) }
                                            endDateMs = chosen.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("End Date", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(endDateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "End Date",
                                tint = NeonMagenta,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Foundational Subject Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Foundational Subject",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = foundationalSubject,
                                onCheckedChange = { foundationalSubject = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonMagenta,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = NeonBlack
                                )
                            )
                        }

                        // Calculate Plan Neon Gradient Button
                        NeonPrimaryGradientButton(
                            text = "Calculate Plan",
                            onClick = { showCalculatedSummary = true },
                            modifier = Modifier.fillMaxWidth(),
                            height = 54.dp
                        )

                        if (showCalculatedSummary) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonBlack.copy(alpha = 0.75f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Daily Focus Target: ${String.format(Locale.US, "%.2f", dailyHoursTarget)} hrs / day",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Spanning $totalDays days • ${String.format(Locale.US, "%.1f", lectures / totalDays.toFloat())} lectures/day at ${playbackSpeed}x speed",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * PYQ Practice Screen matching Panel 9 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Stepper: 1 Setup (glowing #FF00E6 circle) — 2 Quiz — 3 Results
 *   • Select Subject dropdown ("Physics", "Chemistry", "Mathematics", "Biology")
 *   • Select Topic dropdown ("Modern Physics", "Electrostatics", "Optics", etc.)
 *   • Number of Questions neon slider (5..50, default 20)
 *   • "Start Quiz" primary neon gradient button
 *   • "Practice today. Score better tomorrow." tagline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PyqPracticeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(1) } // 1 = Setup, 2 = Quiz, 3 = Results
    var selectedSubject by remember { mutableStateOf("Physics") }
    var subjectExpanded by remember { mutableStateOf(false) }

    val topicsBySubject = remember {
        mapOf(
            "Physics" to listOf("Modern Physics", "Electrostatics", "Current Electricity", "Ray & Wave Optics", "Thermodynamics"),
            "Chemistry" to listOf("Chemical Bonding", "Thermodynamics", "Electrochemistry", "Organic Reaction Mechanisms", "Coordination Compounds"),
            "Mathematics" to listOf("Definite Integration", "Differential Equations", "Matrices & Determinants", "3D Geometry & Vectors", "Probability"),
            "Biology" to listOf("Genetics & Evolution", "Cell Structure & Function", "Human Physiology", "Plant Physiology", "Biotechnology")
        )
    }

    var selectedTopic by remember(selectedSubject) {
        mutableStateOf(topicsBySubject[selectedSubject]?.firstOrNull() ?: "Modern Physics")
    }
    var topicExpanded by remember { mutableStateOf(false) }
    var questionCount by remember { mutableFloatStateOf(20f) }

    // Interactive sample quiz state for Step 2 & Step 3
    var currentQuestionIdx by remember { mutableIntStateOf(0) }
    var selectedOptionIdx by remember { mutableIntStateOf(-1) }
    var correctCount by remember { mutableIntStateOf(0) }

    val sampleQuestions = remember(selectedSubject, selectedTopic) {
        listOf(
            Triple(
                "[$selectedSubject • $selectedTopic] If the de Broglie wavelength of a particle is halved, its kinetic energy becomes:",
                listOf("2 times", "4 times", "Half", "Remains unchanged"),
                1
            ),
            Triple(
                "[$selectedSubject • $selectedTopic] In a photoelectric experiment, stopping potential depends primarily on:",
                listOf("Intensity of incident light", "Frequency of incident light & work function", "Area of the metal plate", "Exposure duration"),
                1
            ),
            Triple(
                "[$selectedSubject • $selectedTopic] The half-life of a radioactive nuclide is T. What fraction decays in 3T?",
                listOf("1/8", "7/8", "3/4", "1/3"),
                1
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PYQ Practice",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) currentStep = 1 else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // 1 Setup — 2 Quiz — 3 Results Stepper (Exact Panel 9)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PyqStepCircle(number = 1, label = "Setup", active = currentStep == 1, done = currentStep > 1)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.5.dp)
                                .padding(horizontal = 8.dp)
                                .background(if (currentStep > 1) NeonCyan else Color(0xFF2E3248))
                        )
                        PyqStepCircle(number = 2, label = "Quiz", active = currentStep == 2, done = currentStep > 2)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.5.dp)
                                .padding(horizontal = 8.dp)
                                .background(if (currentStep > 2) NeonCyan else Color(0xFF2E3248))
                        )
                        PyqStepCircle(number = 3, label = "Results", active = currentStep == 3, done = false)
                    }

                    when (currentStep) {
                        1 -> {
                            // Select Subject
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select Subject", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(NeonSurface)
                                            .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                                            .clickable { subjectExpanded = true }
                                            .padding(horizontal = 18.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedSubject, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                                    }
                                    DropdownMenu(
                                        expanded = subjectExpanded,
                                        onDismissRequest = { subjectExpanded = false },
                                        modifier = Modifier.background(NeonSurface)
                                    ) {
                                        topicsBySubject.keys.forEach { subj ->
                                            DropdownMenuItem(
                                                text = { Text(subj, color = Color.White) },
                                                onClick = {
                                                    selectedSubject = subj
                                                    subjectExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Select Topic
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select Topic", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(NeonSurface)
                                            .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                                            .clickable { topicExpanded = true }
                                            .padding(horizontal = 18.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedTopic, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                                    }
                                    DropdownMenu(
                                        expanded = topicExpanded,
                                        onDismissRequest = { topicExpanded = false },
                                        modifier = Modifier.background(NeonSurface)
                                    ) {
                                        (topicsBySubject[selectedSubject] ?: emptyList()).forEach { topic ->
                                            DropdownMenuItem(
                                                text = { Text(topic, color = Color.White) },
                                                onClick = {
                                                    selectedTopic = topic
                                                    topicExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Number of Questions Slider (Exact Panel 9)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Number of Questions", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Slider(
                                        value = questionCount,
                                        onValueChange = { questionCount = it },
                                        valueRange = 5f..50f,
                                        steps = 8,
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonMagenta,
                                            activeTrackColor = NeonMagenta,
                                            inactiveTrackColor = NeonSurface
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${questionCount.toInt()}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            NeonPrimaryGradientButton(
                                text = "Start Quiz",
                                onClick = {
                                    currentQuestionIdx = 0
                                    selectedOptionIdx = -1
                                    correctCount = 0
                                    currentStep = 2
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 56.dp,
                                fontSize = 17.sp
                            )
                        }

                        2 -> {
                            val (qText, options, answerIdx) = sampleQuestions[currentQuestionIdx % sampleQuestions.size]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, NeonCardBorderBrush, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Question ${currentQuestionIdx + 1} of ${sampleQuestions.size}",
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "+4 / -1 JEE Pattern",
                                            color = NeonMagenta,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = qText,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 22.sp
                                    )
                                    options.forEachIndexed { idx, opt ->
                                        val isSelected = selectedOptionIdx == idx
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSelected) NeonCyan.copy(alpha = 0.16f) else NeonBlack.copy(alpha = 0.6f))
                                                .border(
                                                    width = 1.2.dp,
                                                    color = if (isSelected) NeonCyan else Color(0xFF2E3248),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .clickable { selectedOptionIdx = idx }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "${('A' + idx)}.",
                                                color = if (isSelected) NeonCyan else TextSecondary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = opt,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }

                                    NeonPrimaryGradientButton(
                                        text = if (currentQuestionIdx + 1 < sampleQuestions.size) "Next Question" else "Finish & View Results",
                                        enabled = selectedOptionIdx >= 0,
                                        onClick = {
                                            if (selectedOptionIdx == answerIdx) correctCount++
                                            if (currentQuestionIdx + 1 < sampleQuestions.size) {
                                                currentQuestionIdx++
                                                selectedOptionIdx = -1
                                            } else {
                                                currentStep = 3
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        3 -> {
                            val accuracy = ((correctCount.toFloat() / sampleQuestions.size.toFloat()) * 100f).toInt()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, NeonCardBorderBrush, RoundedCornerShape(22.dp)),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = NeonSurface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Text(
                                        text = "$selectedSubject • $selectedTopic",
                                        color = NeonMagenta,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Score: $correctCount / ${sampleQuestions.size} ($accuracy%)",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp
                                    )
                                    NeonGradientProgressBar(
                                        progress = accuracy / 100f,
                                        modifier = Modifier.fillMaxWidth(),
                                        height = 10.dp
                                    )
                                    NeonOutlinedPillButton(
                                        text = "Practice Another Topic",
                                        onClick = { currentStep = 1 },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom motivational tagline (Exact Panel 9)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = NeonCyan, fontWeight = FontWeight.Medium)) {
                            append("Practice today. ")
                        }
                        withStyle(SpanStyle(color = NeonMagenta, fontWeight = FontWeight.Medium)) {
                            append("Score better tomorrow.")
                        }
                    },
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun PyqStepCircle(
    number: Int,
    label: String,
    active: Boolean,
    done: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active -> Brush.linearGradient(listOf(NeonMagenta, NeonViolet))
                        done -> Brush.linearGradient(listOf(NeonCyan, NeonViolet))
                        else -> Brush.linearGradient(listOf(NeonSurface, NeonSurface))
                    }
                )
                .border(
                    width = 1.5.dp,
                    color = when {
                        active -> NeonMagenta
                        done -> NeonCyan
                        else -> Color(0xFF2E3248)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
        Text(
            text = label,
            color = if (active) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}
