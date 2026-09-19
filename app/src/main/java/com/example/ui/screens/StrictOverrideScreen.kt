package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.FocusViewModel

private const val STRICT_OVERRIDE_COOLDOWN_MS = 45L * 60L * 1000L // 45 minutes

private fun isStrictOverrideBlackoutWindow(hour: Int): Boolean {
    // Fully disabled 10:00 PM - 6:00 AM, regardless of stage.
    return hour >= 22 || hour < 6
}

private fun generateDisciplineParagraph(): String {
    val sentences = listOf(
        "True strength lies in the quiet persistence of our daily choices to remain focused.",
        "We build our future through the deliberate practice of resisting instant gratification.",
        "Maintaining absolute focus on our primary objectives is the key to deep mastery.",
        "Every moment spent in distraction is a moment stolen from our potential.",
        "Self-discipline is not restriction, but the ultimate expression of personal freedom.",
        "By channeling our attention inward, we cultivate a powerful state of mental clarity.",
        "We must learn to embrace the discomfort of difficult tasks to unlock genuine growth.",
        "An organized mind is capable of extraordinary achievements when shielded from chaos.",
        "Consistency is the foundation upon which all great and lasting endeavors are built.",
        "I am fully committed to my goals, guarding my focus against passing temptations."
    )
    val resultWords = mutableListOf<String>()
    val rand = kotlin.random.Random(System.currentTimeMillis())
    while (resultWords.size < 300) {
        resultWords.addAll(sentences[rand.nextInt(sentences.size)].split(" "))
    }
    return resultWords.take(300).joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictOverrideScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val requestedAt by viewModel.strictBypassRequestedAt.collectAsStateWithLifecycle()

    val targetText = remember { generateDisciplineParagraph() }
    var typedText by remember { mutableStateOf("") }
    val targetWords = remember(targetText) { targetText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() } }
    val typedWords = remember(typedText) { typedText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() } }
    val matchedWordsCount = remember(targetWords, typedWords) {
        var count = 0
        for (i in 0 until minOf(targetWords.size, typedWords.size)) {
            if (targetWords[i] == typedWords[i]) count++
        }
        count
    }
    val isPerfectMatch = remember(typedText, targetText) { typedText.trim() == targetText.trim() }

    val disabledClipboardManager = remember {
        object : androidx.compose.ui.platform.ClipboardManager {
            override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) {}
            override fun getText(): androidx.compose.ui.text.AnnotatedString? = null
        }
    }
    val disabledTextToolbar = remember {
        object : androidx.compose.ui.platform.TextToolbar {
            override val status: androidx.compose.ui.platform.TextToolbarStatus = androidx.compose.ui.platform.TextToolbarStatus.Hidden
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {}
            override fun hide() {}
        }
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val currentHour = remember(now) {
        java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
    }
    val isBlackout = remember(currentHour) { isStrictOverrideBlackoutWindow(currentHour) }

    val elapsedMs = if (requestedAt > 0L) (now - requestedAt).coerceAtLeast(0L) else 0L
    val remainingMs = (STRICT_OVERRIDE_COOLDOWN_MS - elapsedMs).coerceAtLeast(0L)
    val cooldownElapsed = requestedAt > 0L && remainingMs <= 0L
    val canCompleteOverride = cooldownElapsed && !isBlackout

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D12),
        topBar = {
            TopAppBar(
                title = { Text("Extreme Override", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D12))
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
            when {
                isBlackout -> {
                    InfoCard(
                        icon = Icons.Default.Warning,
                        title = "UNAVAILABLE RIGHT NOW",
                        body = "Extreme Override is switched off between 10:00 PM and 6:00 AM, no matter the stage. Come back after 6 AM."
                    )
                }

                requestedAt <= 0L -> {
                    InfoCard(
                        icon = Icons.Default.Warning,
                        title = "STRICT DISCIPLINE CHALLENGE",
                        body = "Perfectly type the 300-word paragraph below to start the 45-minute cooldown. Copy-pasting, clipboard features, and selection utilities are completely disabled on the input field."
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f), contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("REQUIRED TEXT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text("300 Words", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Text(targetText, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TYPED WORDS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Text("${typedWords.size} / 300", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (typedWords.size == 300) MaterialTheme.colorScheme.primary else Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PERFECTLY MATCHED", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Text("$matchedWordsCount", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (matchedWordsCount == 300) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalClipboardManager provides disabledClipboardManager,
                        androidx.compose.ui.platform.LocalTextToolbar provides disabledTextToolbar
                    ) {
                        OutlinedTextField(
                            value = typedText,
                            onValueChange = { typedText = it },
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            placeholder = { Text("Begin typing the paragraph perfectly here...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isPerfectMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White)
                        )
                    }
                    Button(
                        onClick = { if (isPerfectMatch) viewModel.requestStrictModeOverride() },
                        enabled = isPerfectMatch,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(if (isPerfectMatch) "START 45-MIN COOLDOWN" else "TYPE ENTIRE PARAGRAPH PERFECTLY", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                remainingMs > 0L -> {
                    InfoCard(
                        icon = Icons.Default.Warning,
                        title = "45-MINUTE COOLDOWN",
                        body = "Paragraph verified. Strict Mode can be switched off once this cooldown finishes. Also unavailable 10 PM - 6 AM."
                    )
                    val remainingSeconds = remainingMs / 1000L
                    val timerLabel = String.format("%02d:%02d", remainingSeconds / 60L, remainingSeconds % 60L)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("TIME REMAINING", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Text(timerLabel, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            LinearProgressIndicator(
                                progress = { (elapsedMs.toFloat() / STRICT_OVERRIDE_COOLDOWN_MS.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.cancelStrictModeOverrideRequest() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Request", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }

                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("READY TO OVERRIDE", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Text("00:00", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.deactivateStrictModeViaOverride()
                            android.widget.Toast.makeText(context, "Strict Mode Prematurely Deactivated.", android.widget.Toast.LENGTH_LONG).show()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("COMPLETE OVERRIDE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    TextButton(onClick = { viewModel.cancelStrictModeOverrideRequest() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Request", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }
            Text(body, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
