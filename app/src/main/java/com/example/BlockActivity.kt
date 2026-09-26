package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import com.example.service.FocusAccessibilityService
import com.example.data.FocusSession
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class BlockActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        FocusAccessibilityService.instance?.dismissInstantOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val blockedPackage = intent.getStringExtra("BLOCKED_PACKAGE") ?: "Unknown App"
        val fallbackEndTime = intent.getLongExtra("END_TIME", 0L)

        // Content-level block details
        val isContentLevel = intent.getBooleanExtra("IS_CONTENT_LEVEL", false)
        val contentType = intent.getStringExtra("CONTENT_TYPE") ?: ""

        // Long-term block details
        val isLongTerm = intent.getBooleanExtra("IS_LONG_TERM", false)
        val longTermReason = intent.getStringExtra("LONG_TERM_REASON") ?: ""
        val longTermEndDate = intent.getLongExtra("LONG_TERM_END_DATE", 0L)
        val longTermTargetLabel = intent.getStringExtra("LONG_TERM_TARGET_LABEL") ?: ""
        val longTermType = intent.getStringExtra("LONG_TERM_TYPE") ?: "APP"

        // Find clean name of package if possible
        val pm = packageManager
        val appLabel = if (longTermTargetLabel.isNotEmpty()) {
            longTermTargetLabel
        } else {
            try {
                val appInfo = pm.getApplicationInfo(blockedPackage, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                blockedPackage.substringAfterLast(".")
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) { // Force Dark Theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as FocusApplication
                    val activeSession by app.repository.activeSession.collectAsState(initial = null)

                    if (isContentLevel) {
                        ContentBlockedScreen(
                            contentType = contentType,
                            activeSession = activeSession,
                            fallbackEndTime = fallbackEndTime,
                            onGoBack = {
                                val service = FocusAccessibilityService.instance
                                if (service != null) {
                                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                                } else {
                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(homeIntent)
                                }
                                finish()
                            }
                        )
                    } else {
                        BlockScreenContent(
                            appName = appLabel,
                            activeSession = activeSession,
                            fallbackEndTime = fallbackEndTime,
                            isLongTerm = isLongTerm,
                            longTermReason = longTermReason,
                            longTermEndDate = longTermEndDate,
                            longTermType = longTermType,
                            onReturnHome = {
                                // Launch home screen
                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(homeIntent)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlockScreenContent(
    appName: String,
    activeSession: FocusSession?,
    fallbackEndTime: Long,
    isLongTerm: Boolean,
    longTermReason: String,
    longTermEndDate: Long,
    longTermType: String,
    onReturnHome: () -> Unit
) {
    val endTime = activeSession?.endTime ?: fallbackEndTime
    val actualEndTime = if (isLongTerm) longTermEndDate else endTime
    val startTime = activeSession?.startTime ?: (actualEndTime - 15 * 60 * 1000L) // fallback to 15 min session
    val durationMinutes = activeSession?.durationMinutes ?: 15

    var timeRemaining by remember(actualEndTime) { mutableStateOf(max(0L, actualEndTime - System.currentTimeMillis())) }

    // Tick the countdown every second for active block/session
    LaunchedEffect(actualEndTime) {
        while (timeRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            timeRemaining = max(0L, actualEndTime - System.currentTimeMillis())
        }
    }

    val totalSeconds = timeRemaining / 1000
    val days = totalSeconds / (24 * 3600)
    val remainingSecs = totalSeconds % (24 * 3600)
    val hours = remainingSecs / 3600
    val minutes = (remainingSecs % 3600) / 60
    val seconds = remainingSecs % 60

    val timeString = remember(days, hours, minutes, seconds) {
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days Day${if (days != 1L) "s" else ""}")
        if (hours > 0 || days > 0) parts.add("$hours Hour${if (hours != 1L) "s" else ""}")
        if (minutes > 0 || hours > 0 || days > 0) parts.add("$minutes Minute${if (minutes != 1L) "s" else ""}")
        parts.add("$seconds Second${if (seconds != 1L) "s" else ""}")
        parts.joinToString(", ")
    }

    val timeFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val startTimeString = remember(startTime) { timeFormatter.format(Date(startTime)) }
    val endTimeString = remember(actualEndTime) { timeFormatter.format(Date(actualEndTime)) }

    val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
    val blockEndDateString = remember(actualEndTime) { dateFormatter.format(Date(actualEndTime)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20), // Deep rich space theme background
                        Color(0xFF15102A),
                        Color(0xFF0A0714)
                    )
                )
            )
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status banner
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isLongTerm) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    } else {
                        Color(0xFFE65100).copy(alpha = 0.2f)
                    }
                )
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isLongTerm) Icons.Default.Shield else Icons.Default.Lock,
                    contentDescription = "Shield Lock Icon",
                    tint = if (isLongTerm) MaterialTheme.colorScheme.error else Color(0xFFFFB74D),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isLongTerm) "LONG-TERM BLOCK ACTIVE" else "FOCUS SESSION ACTIVE",
                    color = if (isLongTerm) MaterialTheme.colorScheme.error else Color(0xFFFFB74D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = if (isLongTerm) Icons.Default.Shield else Icons.Default.HourglassEmpty,
                contentDescription = "Shield Icon",
                tint = if (isLongTerm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = if (isLongTerm) appName else "Take a Breath",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isLongTerm) {
                    if (longTermType == "WEBSITE") {
                        "This website is currently blocked."
                    } else {
                        "This app is currently blocked."
                    }
                } else {
                    "$appName is blocked while Focuss Buddy is shielding you from distractions."
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Section for custom Reason (displayed for BOTH long-term and short-term blocks if reason is not empty)
            if (longTermReason.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "WHY IT IS BLOCKED",
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = longTermReason,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            } else {
                // If there's no custom reason, but it's a strict mode active focus session
                val isStrict = activeSession?.isStrict ?: false
                if (isStrict) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "STRICT MODE ACTIVE",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This app is shielded. You cannot open it or turn off the block until the timer ends.",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time Remaining Countdown Display Card (Generically used for both)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x15FFFFFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REMAINING TIME",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val containerWidth = maxWidth
                        val calculatedFontSize = (containerWidth.value / (timeString.length * 0.62f)).coerceIn(10f, 32f).sp
                        val calculatedLineHeight = (calculatedFontSize.value * 1.3).sp
                        Text(
                            text = timeString,
                            color = if (isLongTerm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontSize = calculatedFontSize,
                            lineHeight = calculatedLineHeight,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Unified Details Panel
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x0AFFFFFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLongTerm) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Block End Date",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            Text(
                                text = blockEndDateString,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Duration",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$durationMinutes min",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Session Start",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            Text(
                                text = startTimeString,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Session End",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            Text(
                                text = endTimeString,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Bottom Button
        Button(
            onClick = onReturnHome,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Return to Home Screen",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ContentBlockedScreen(
    contentType: String,
    activeSession: FocusSession?,
    fallbackEndTime: Long,
    onGoBack: () -> Unit
) {
    val endTime = activeSession?.endTime ?: fallbackEndTime
    var timeRemaining by remember(endTime) { mutableStateOf(max(0L, endTime - System.currentTimeMillis())) }

    // Tick the countdown every second for active focus session
    LaunchedEffect(endTime) {
        while (timeRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            timeRemaining = max(0L, endTime - System.currentTimeMillis())
        }
    }

    val totalSeconds = timeRemaining / 1000
    val days = totalSeconds / (24 * 3600)
    val remainingSecs = totalSeconds % (24 * 3600)
    val hours = remainingSecs / 3600
    val minutes = (remainingSecs % 3600) / 60
    val seconds = remainingSecs % 60

    val timeString = remember(days, hours, minutes, seconds) {
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days Day${if (days != 1L) "s" else ""}")
        if (hours > 0 || days > 0) parts.add("$hours Hour${if (hours != 1L) "s" else ""}")
        if (minutes > 0 || hours > 0 || days > 0) parts.add("$minutes Minute${if (minutes != 1L) "s" else ""}")
        parts.add("$seconds Second${if (seconds != 1L) "s" else ""}")
        parts.joinToString(", ")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20),
                        Color(0xFF15102A),
                        Color(0xFF0A0714)
                    )
                )
            )
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status banner
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Icon",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "STRICT MODE CONTENT BLOCK",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Block Icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "Content Restricted",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "$contentType access is restricted during your active focus session.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Dynamic Info / Reason Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WHY IT IS BLOCKED",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You enabled the restriction for $contentType to block short-form video distractions in Strict Mode.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time Remaining Countdown Display Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x15FFFFFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REMAINING TIME",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val containerWidth = maxWidth
                        val calculatedFontSize = (containerWidth.value / (timeString.length * 0.62f)).coerceIn(10f, 32f).sp
                        val calculatedLineHeight = (calculatedFontSize.value * 1.3).sp
                        Text(
                            text = timeString,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = calculatedFontSize,
                            lineHeight = calculatedLineHeight,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Bottom Button
        Button(
            onClick = onGoBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Go Back",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
