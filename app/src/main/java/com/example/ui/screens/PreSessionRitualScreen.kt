package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreSessionRitualScreen(
    viewModel: FocusViewModel,
    onRitualComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var stage by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Global "Skip Ritual" text button in the top-right corner
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .testTag("skip_ritual_global")
        ) {
            Text(
                text = "Skip Ritual",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Main content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (stage) {
                0 -> {
                    // Stage 0: Stretch Prompt
                    var timeLeft by remember { mutableStateOf(10) }

                    LaunchedEffect(Unit) {
                        while (timeLeft > 0) {
                            delay(1000L)
                            timeLeft--
                        }
                        stage = 1
                    }

                    Text(
                        text = "Stand Up & Stretch",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Stand up. Stretch your arms up for 10 seconds. Roll your shoulders back.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Animated countdown display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { timeLeft / 10f },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "$timeLeft",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    TextButton(
                        onClick = { stage = 1 },
                        modifier = Modifier.testTag("skip_stage_0")
                    ) {
                        Text(
                            text = "Skip Stretch →",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                1 -> {
                    // Stage 1: Box Breathing
                    var cycleCount by remember { mutableStateOf(0) }
                    var phase by remember { mutableStateOf(0) } // 0: Breathe in, 1: Hold, 2: Breathe out, 3: Hold
                    var secondsInPhase by remember { mutableStateOf(4) }

                    LaunchedEffect(Unit) {
                        while (cycleCount < 3) {
                            for (p in 0..3) {
                                phase = p
                                for (sec in 4 downTo 1) {
                                    secondsInPhase = sec
                                    delay(1000L)
                                }
                            }
                            cycleCount++
                        }
                        stage = 2
                    }

                    val targetScale = when (phase) {
                        0 -> 1.0f
                        1 -> 1.0f
                        2 -> 0.3f
                        3 -> 0.3f
                        else -> 0.3f
                    }

                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = tween(durationMillis = 4000, easing = LinearEasing),
                        label = "BreathingCircle"
                    )

                    val phaseLabel = when (phase) {
                        0 -> "Breathe in..."
                        1 -> "Hold..."
                        2 -> "Breathe out..."
                        3 -> "Hold..."
                        else -> ""
                    }

                    Text(
                        text = "Box Breathing",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Cycle ${cycleCount + 1} of 3 • Phase: $secondsInPhase s",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(240.dp)
                    ) {
                        // Background glowing halo
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFF1E88E5).copy(alpha = 0.1f),
                                radius = size.minDimension / 2
                            )
                        }

                        // Animating breathing circle
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFF1E88E5).copy(alpha = 0.4f),
                                radius = (size.minDimension / 2) * scale
                            )
                        }

                        // Pulse indicator in center
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = phaseLabel,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    TextButton(
                        onClick = { stage = 2 },
                        modifier = Modifier.testTag("skip_stage_1")
                    ) {
                        Text(
                            text = "Skip Breathing →",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                2 -> {
                    // Stage 2: Personal "Why" Reminder
                    var personalWhy by remember { mutableStateOf<String?>(null) }
                    var isLoadingWhy by remember { mutableStateOf(true) }
                    var isEditingWhy by remember { mutableStateOf(false) }
                    var whyTextState by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        personalWhy = viewModel.getSetting("personal_why_note")
                        whyTextState = personalWhy.orEmpty()
                        isLoadingWhy = false
                    }

                    if (isLoadingWhy) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        if (personalWhy.isNullOrBlank() || isEditingWhy) {
                            Text(
                                text = "Your Motivation",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = whyTextState,
                                onValueChange = { whyTextState = it },
                                placeholder = {
                                    Text(
                                        text = "What's your reason for studying? You'll see this before every session.",
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(horizontal = 16.dp)
                                    .testTag("why_input_field"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.saveSetting("personal_why_note", whyTextState.trim())
                                        personalWhy = whyTextState.trim()
                                        isEditingWhy = false
                                        stage = 3
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(48.dp)
                                    .testTag("save_why_button")
                            ) {
                                Text("Save & Continue", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Why I'm doing this:",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "\"${personalWhy}\"",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFCC80), // Large, warm color
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(36.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { isEditingWhy = true },
                                    modifier = Modifier.testTag("edit_why_button")
                                ) {
                                    Text(
                                        text = "Edit Reason",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 15.sp
                                    )
                                }

                                Button(
                                    onClick = { stage = 3 },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("proceed_from_why_button")
                                ) {
                                    Text("Proceed", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Stage 3: 2-Minute Starter Task
                    var timeLeft3 by remember { mutableStateOf(120) }

                    LaunchedEffect(Unit) {
                        while (timeLeft3 > 0) {
                            delay(1000L)
                            timeLeft3--
                        }
                        onRitualComplete()
                    }

                    Text(
                        text = "The 2-Minute Rule",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Just open your book/notes to today's first topic. That's all - the timer starts after this.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // 120-second countdown display
                    val minutesLeft = timeLeft3 / 60
                    val secondsLeft = timeLeft3 % 60
                    val timerDisplay = String.format("%02d:%02d", minutesLeft, secondsLeft)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { timeLeft3 / 120f },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = timerDisplay,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = onRitualComplete,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(48.dp)
                            .testTag("skip_stage_3")
                    ) {
                        Text("Start Timer Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
