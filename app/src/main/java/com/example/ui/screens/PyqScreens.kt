package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PyqQuestion
import com.example.data.PyqQuizAnswer
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.launch

private val CorrectGreen = Color(0xFF22C55E)

private enum class PyqStage { SETUP, QUIZ, RESULTS }

@Composable
fun PyqPracticeScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit
) {
    var stage by remember { mutableStateOf(PyqStage.SETUP) }
    var quizQuestions by remember { mutableStateOf<List<PyqQuestion>>(emptyList()) }
    var attemptId by remember { mutableStateOf(0) }
    var finalAnswers by remember { mutableStateOf<List<PyqQuizAnswer>>(emptyList()) }
    var finalTotalTimeSeconds by remember { mutableStateOf(0L) }

    when (stage) {
        PyqStage.SETUP -> PyqSetupContent(
            viewModel = viewModel,
            onBack = onBack,
            onStart = { questions, id ->
                quizQuestions = questions
                attemptId = id
                stage = PyqStage.QUIZ
            }
        )
        PyqStage.QUIZ -> PyqQuizContent(
            questions = quizQuestions,
            onFinish = { answers, totalTimeSeconds ->
                finalAnswers = answers
                finalTotalTimeSeconds = totalTimeSeconds
                stage = PyqStage.RESULTS
            }
        )
        PyqStage.RESULTS -> PyqResultsContent(
            viewModel = viewModel,
            attemptId = attemptId,
            answers = finalAnswers,
            totalTimeSeconds = finalTotalTimeSeconds,
            onPracticeAgain = { stage = PyqStage.SETUP },
            onExit = onBack
        )
    }
}

// =====================================================================================
// STAGE 1: SETUP
// =====================================================================================

@Composable
private fun PyqSetupContent(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onStart: (List<PyqQuestion>, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var totalAvailable by remember { mutableStateOf(0) }

    var selectedSubject by remember { mutableStateOf<String?>(null) }   // null = all subjects
    var selectedDifficulty by remember { mutableStateOf<String?>(null) } // null = mixed
    var matchingCount by remember { mutableStateOf(0) }
    var questionCount by remember { mutableStateOf(10) }
    var isStarting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        totalAvailable = viewModel.getPyqQuestionCount()
        isLoading = false
    }

    LaunchedEffect(selectedSubject, selectedDifficulty, totalAvailable) {
        if (totalAvailable > 0) {
            matchingCount = viewModel.getMatchingPyqCount(selectedSubject, selectedDifficulty)
            if (questionCount > matchingCount) {
                questionCount = matchingCount.coerceAtLeast(1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("PYQ Practice", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                totalAvailable == 0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = "No Questions",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No PYQs Added Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Upload previous-year question papers and they'll show up here for practice.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "$totalAvailable questions in bank",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SUBJECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PyqChip("All", selectedSubject == null) { selectedSubject = null }
                                PyqChip("Physics", selectedSubject == "Physics") { selectedSubject = "Physics" }
                                PyqChip("Chemistry", selectedSubject == "Chemistry") { selectedSubject = "Chemistry" }
                                PyqChip("Maths", selectedSubject == "Mathematics") { selectedSubject = "Mathematics" }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("DIFFICULTY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PyqChip("Mixed", selectedDifficulty == null) { selectedDifficulty = null }
                                PyqChip("Easy", selectedDifficulty == "EASY") { selectedDifficulty = "EASY" }
                                PyqChip("Medium", selectedDifficulty == "MEDIUM") { selectedDifficulty = "MEDIUM" }
                                PyqChip("Tough", selectedDifficulty == "TOUGH") { selectedDifficulty = "TOUGH" }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "NUMBER OF QUESTIONS ($matchingCount available)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            if (matchingCount == 0) {
                                Text(
                                    text = "No questions match this filter combination.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    IconButton(
                                        onClick = { questionCount = (questionCount - 5).coerceAtLeast(1) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) { Text("-5", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                                    Text(
                                        text = "$questionCount",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(64.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    IconButton(
                                        onClick = { questionCount = (questionCount + 5).coerceAtMost(matchingCount) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) { Text("+5", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f).heightIn(min = 8.dp))

                        Button(
                            onClick = {
                                if (isStarting || matchingCount == 0) return@Button
                                isStarting = true
                                scope.launch {
                                    val questions = viewModel.getRandomPyqQuestions(
                                        selectedSubject,
                                        selectedDifficulty,
                                        questionCount.coerceAtMost(matchingCount)
                                    )
                                    val id = viewModel.startPyqQuizAttempt(
                                        selectedSubject ?: "ALL",
                                        selectedDifficulty ?: "ALL",
                                        questionCount
                                    )
                                    isStarting = false
                                    if (questions.isNotEmpty()) {
                                        onStart(questions, id)
                                    }
                                }
                            },
                            enabled = matchingCount > 0 && !isStarting,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isStarting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("START QUIZ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PyqChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f)
        )
    }
}

// =====================================================================================
// STAGE 2: QUIZ
// =====================================================================================

@Composable
private fun PyqQuizContent(
    questions: List<PyqQuestion>,
    onFinish: (List<PyqQuizAnswer>, Long) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var numericInput by remember { mutableStateOf("") }
    val collectedAnswers = remember { mutableStateListOf<PyqQuizAnswer>() }
    var questionStartMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val quizStartMs = remember { System.currentTimeMillis() }

    val question = questions[currentIndex]

    LaunchedEffect(currentIndex) {
        selectedOption = null
        isAnswered = false
        numericInput = ""
        questionStartMs = System.currentTimeMillis()
    }

    fun recordAndAdvance(chosen: String) {
        val timeTaken = (System.currentTimeMillis() - questionStartMs) / 1000L
        val correct = chosen.isNotBlank() &&
            chosen.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
        collectedAnswers.add(
            PyqQuizAnswer(
                attemptId = 0, // the real attemptId is applied by completePyqQuizAttempt() in the results stage
                questionId = question.id,
                subject = question.subject,
                chapter = question.chapter,
                difficulty = question.difficulty,
                selectedAnswer = chosen,
                correctAnswer = question.correctAnswer,
                isCorrect = correct,
                timeTakenSeconds = timeTaken
            )
        )
        if (currentIndex < questions.lastIndex) {
            currentIndex += 1
        } else {
            val totalTime = (System.currentTimeMillis() - quizStartMs) / 1000L
            onFinish(collectedAnswers.toList(), totalTime)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF121212),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Question ${currentIndex + 1} / ${questions.size}", color = Color.White, fontSize = 15.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                )
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / questions.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PyqTag(question.subject)
                if (question.chapter.isNotBlank()) PyqTag(question.chapter)
                PyqTag(question.difficulty)
                PyqTag("${question.year}")
            }

            Text(
                text = question.questionText,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color.White
            )

            if (question.isNumerical) {
                OutlinedTextField(
                    value = numericInput,
                    onValueChange = { if (!isAnswered) numericInput = it },
                    enabled = !isAnswered,
                    label = { Text("Your answer") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                if (isAnswered) {
                    val correct = numericInput.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
                    Text(
                        text = if (correct) "Correct! Answer: ${question.correctAnswer}" else "Wrong. Correct answer: ${question.correctAnswer}",
                        color = if (correct) CorrectGreen else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                val options = listOf("A" to question.optionA, "B" to question.optionB, "C" to question.optionC, "D" to question.optionD)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { (key, text) ->
                        if (text.isNotBlank()) {
                            PyqOptionCard(
                                label = key,
                                text = text,
                                isAnswered = isAnswered,
                                isSelected = selectedOption == key,
                                isCorrectOption = question.correctAnswer.trim().equals(key, ignoreCase = true),
                                onClick = {
                                    if (!isAnswered) {
                                        selectedOption = key
                                        isAnswered = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isAnswered) {
                    OutlinedButton(
                        onClick = { recordAndAdvance("") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SKIP", color = Color.White.copy(alpha = 0.7f))
                    }
                }
                if (question.isNumerical && !isAnswered) {
                    Button(
                        onClick = {
                            if (numericInput.isNotBlank()) {
                                isAnswered = true
                            }
                        },
                        enabled = numericInput.isNotBlank(),
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("SUBMIT", fontWeight = FontWeight.Bold)
                    }
                }
                if (isAnswered) {
                    Button(
                        onClick = {
                            recordAndAdvance(if (question.isNumerical) numericInput else (selectedOption ?: ""))
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            if (currentIndex == questions.lastIndex) "FINISH" else "NEXT",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PyqTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun PyqOptionCard(
    label: String,
    text: String,
    isAnswered: Boolean,
    isSelected: Boolean,
    isCorrectOption: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        !isAnswered -> Color.White.copy(alpha = 0.15f)
        isCorrectOption -> CorrectGreen
        isSelected && !isCorrectOption -> MaterialTheme.colorScheme.error
        else -> Color.White.copy(alpha = 0.1f)
    }
    val bgColor = when {
        !isAnswered -> Color.White.copy(alpha = 0.04f)
        isCorrectOption -> CorrectGreen.copy(alpha = 0.15f)
        isSelected && !isCorrectOption -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.02f)
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
    val rowModifier = if (!isAnswered) baseModifier.clickable(onClick = onClick) else baseModifier

    Row(
        modifier = rowModifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(borderColor.copy(alpha = if (isAnswered) 1f else 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(text, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (isAnswered && isCorrectOption) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = CorrectGreen, modifier = Modifier.size(20.dp))
        }
    }
}

// =====================================================================================
// STAGE 3: RESULTS DASHBOARD
// =====================================================================================

@Composable
private fun PyqResultsContent(
    viewModel: FocusViewModel,
    attemptId: Int,
    answers: List<PyqQuizAnswer>,
    totalTimeSeconds: Long,
    onPracticeAgain: () -> Unit,
    onExit: () -> Unit
) {
    LaunchedEffect(attemptId) {
        val answersWithAttemptId = answers.map { it.copy(attemptId = attemptId) }
        viewModel.completePyqQuizAttempt(attemptId, answersWithAttemptId, totalTimeSeconds)
    }

    val total = answers.size
    val correct = answers.count { it.isCorrect }
    val skipped = answers.count { it.selectedAnswer.isBlank() }
    val wrong = total - correct - skipped
    val percentage = if (total > 0) (correct.toFloat() / total.toFloat()) * 100f else 0f
    val avgTimePerQuestion = if (total > 0) totalTimeSeconds / total else 0L

    val bySubject = answers.groupBy { it.subject }
    val byChapter = answers.filter { it.chapter.isNotBlank() }.groupBy { it.chapter }
    val byDifficulty = answers.groupBy { it.difficulty }

    val weakChapters = byChapter.entries
        .map { (chapter, list) -> Triple(chapter, list.count { it.isCorrect }, list.size) }
        .filter { it.third >= 1 }
        .sortedBy { it.second.toFloat() / it.third.toFloat() }
        .take(5)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("Results", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("SCORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                    Text(
                        text = "${"%.1f".format(percentage)}%",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (percentage >= 50f) CorrectGreen else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "$correct correct / $wrong wrong / $skipped skipped  (of $total)",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatBox(modifier = Modifier.weight(1f), label = "TOTAL TIME", value = formatDuration(totalTimeSeconds))
                StatBox(modifier = Modifier.weight(1f), label = "AVG / QUESTION", value = formatDuration(avgTimePerQuestion))
            }

            Text(
                text = "Percentile isn't shown - there's no peer group to compare against in an offline personal app, so accuracy % above is the real signal.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f),
                lineHeight = 15.sp
            )

            ResultSection(title = "SUBJECT-WISE") {
                bySubject.forEach { (subject, list) ->
                    AccuracyRow(label = subject, correct = list.count { it.isCorrect }, total = list.size)
                }
            }

            ResultSection(title = "DIFFICULTY-WISE") {
                listOf("EASY", "MEDIUM", "TOUGH").forEach { diff ->
                    val list = byDifficulty[diff] ?: emptyList()
                    if (list.isNotEmpty()) {
                        AccuracyRow(label = diff, correct = list.count { it.isCorrect }, total = list.size)
                    }
                }
            }

            if (weakChapters.isNotEmpty()) {
                ResultSection(title = "WEAK TOPICS (lowest accuracy)") {
                    weakChapters.forEach { (chapter, chapterCorrect, chapterTotal) ->
                        AccuracyRow(label = chapter, correct = chapterCorrect, total = chapterTotal, highlightIfWeak = true)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("EXIT", color = Color.White)
                }
                Button(
                    onClick = onPracticeAgain,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("PRACTICE AGAIN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatBox(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun ResultSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun AccuracyRow(label: String, correct: Int, total: Int, highlightIfWeak: Boolean = false) {
    val pct = if (total > 0) (correct.toFloat() / total.toFloat()) else 0f
    val color = when {
        highlightIfWeak && pct < 0.5f -> MaterialTheme.colorScheme.error
        pct >= 0.7f -> CorrectGreen
        else -> Color(0xFFFFA630)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text("$correct/$total (${(pct * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
