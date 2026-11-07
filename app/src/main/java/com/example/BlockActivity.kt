package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusSession
import com.example.service.FocusAccessibilityService
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonCircularLockEmblem
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonOutlinedPillButton
import com.example.ui.components.NeonPrimaryGradientButton
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class BlockActivity : ComponentActivity() {

    private var blockedPackageState by mutableStateOf("Unknown App")
    private var fallbackEndTimeState by mutableLongStateOf(0L)
    private var isContentLevelState by mutableStateOf(false)
    private var contentTypeState by mutableStateOf("")
    private var isLongTermState by mutableStateOf(false)
    private var longTermReasonState by mutableStateOf("")
    private var longTermEndDateState by mutableLongStateOf(0L)
    private var longTermTypeState by mutableStateOf("APP")
    private var appLabelState by mutableStateOf("")
    private var isMindfulDelayState by mutableStateOf(false)
    private var mindfulTriggerKey by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        FocusAccessibilityService.instance?.dismissInstantOverlay()
    }

    private fun resolveAppLabel(targetLabel: String, pkg: String): String {
        if (targetLabel.isNotEmpty()) return targetLabel
        val pm = packageManager
        return try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast(".")
        }
    }

    private fun applyIntent(newIntent: Intent) {
        val blockedPkg = newIntent.getStringExtra("BLOCKED_PACKAGE") ?: "Unknown App"
        blockedPackageState = blockedPkg
        fallbackEndTimeState = newIntent.getLongExtra("END_TIME", 0L)
        isContentLevelState = newIntent.getBooleanExtra("IS_CONTENT_LEVEL", false)
        contentTypeState = newIntent.getStringExtra("CONTENT_TYPE") ?: ""
        isLongTermState = newIntent.getBooleanExtra("IS_LONG_TERM", false)
        longTermReasonState = newIntent.getStringExtra("LONG_TERM_REASON") ?: ""
        longTermEndDateState = newIntent.getLongExtra("LONG_TERM_END_DATE", 0L)
        val targetLabel = newIntent.getStringExtra("LONG_TERM_TARGET_LABEL") ?: ""
        longTermTypeState = newIntent.getStringExtra("LONG_TERM_TYPE") ?: "APP"
        isMindfulDelayState = newIntent.getBooleanExtra("IS_MINDFUL_DELAY", false)
        mindfulTriggerKey++
        appLabelState = resolveAppLabel(targetLabel, blockedPkg)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun goHomeAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun launchAllowedAppAndFinish(pkg: String) {
        FocusAccessibilityService.instance?.grantMindfulLaunchPass(pkg)
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            // Fall through to finish() which reveals the underlying app
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) {
            if (isMindfulDelayState) {
                goHomeAndFinish()
            }
        }

        applyIntent(intent)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NeonBlack
                ) {
                    val app = application as FocusApplication
                    val activeSession by app.repository.activeSession.collectAsState(initial = null)

                    if (isMindfulDelayState) {
                        MindfulBreathingGateScreen(
                            appName = appLabelState,
                            triggerKey = mindfulTriggerKey,
                            onProceedWithReason = { _ ->
                                launchAllowedAppAndFinish(blockedPackageState)
                            },
                            onCancelToHome = {
                                goHomeAndFinish()
                            }
                        )
                    } else if (isContentLevelState) {
                        ContentBlockedScreen(
                            contentType = contentTypeState,
                            activeSession = activeSession,
                            fallbackEndTime = fallbackEndTimeState,
                            onGoBack = {
                                val service = FocusAccessibilityService.instance
                                if (service != null) {
                                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                                } else {
                                    goHomeAndFinish()
                                    return@ContentBlockedScreen
                                }
                                finish()
                            }
                        )
                    } else {
                        BlockScreenContent(
                            appName = appLabelState,
                            activeSession = activeSession,
                            fallbackEndTime = fallbackEndTimeState,
                            isLongTerm = isLongTermState,
                            longTermReason = longTermReasonState,
                            longTermEndDate = longTermEndDateState,
                            longTermType = longTermTypeState,
                            onReturnHome = {
                                goHomeAndFinish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MindfulBreathingGateScreen(
    appName: String,
    triggerKey: Int,
    onProceedWithReason: (String) -> Unit,
    onCancelToHome: () -> Unit
) {
    var secondsLeft by remember(triggerKey) { mutableIntStateOf(7) }

    LaunchedEffect(triggerKey) {
        secondsLeft = 7
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            secondsLeft--
        }
    }

    val isReady = secondsLeft <= 0

    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NeonSurface)
                    .border(1.dp, NeonCardBorderBrush, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = "Mindful Pause",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "MINDFUL LAUNCH GATE · 7s PAUSE",
                        color = NeonCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(Alignment.CenterVertically)
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(NeonSurface)
                        .border(2.5.dp, NeonCardBorderBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isReady) {
                        Text(
                            text = "${secondsLeft}s",
                            color = NeonCyan,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = "Ready",
                            tint = NeonMagenta,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Text(
                    text = "Rukiye! Kya $appName abhi zaroori hai?",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (!isReady) {
                        "Take a deep breath for $secondsLeft second${if (secondsLeft != 1) "s" else ""}. Break muscle-memory scrolling before deciding."
                    } else {
                        "If you still need to open $appName, tap your intention below (grants a 10-minute mindful pass)."
                    },
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                if (isReady) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SELECT REASON TO CONTINUE (10 MIN PASS)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onProceedWithReason("Study / Work") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.2.dp, NeonCyan.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                            ) {
                                Text("📚 Study / Work", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { onProceedWithReason("Urgent Message") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.2.dp, NeonMagenta.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonMagenta)
                            ) {
                                Text("⚡ Urgent", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NeonPrimaryGradientButton(
                    text = "Close & Stay Focused",
                    onClick = onCancelToHome,
                    modifier = Modifier.fillMaxWidth()
                )
                NeonBetterFocusTagline()
            }
        }
    }
}

/**
 * Blocked Screen matching Panel 4 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Deep #08080F background with diagonal neon magenta & cyan laser beams
 *   • Large glowing NeonCircularLockEmblem at top-center
 *   • "Blocked" bold white headline + "You can't access this app right now. It's part of your focus plan."
 *   • Dark #1A1A24 app card with neon gradient squircle icon, app title, limit/status reason, and timer
 *   • Glowing neon gradient-bordered pill button "Go Back"
 *   • "Better Focus. Brighter Future." tagline at the bottom
 */
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
    val nowInitial = remember(actualEndTime) { TrustedClock.now() }
    val computedDurationMin = remember(actualEndTime, nowInitial) {
        if (actualEndTime > nowInitial) {
            ((actualEndTime - nowInitial + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } else 0
    }
    val startTime = activeSession?.startTime ?: nowInitial
    val durationMinutes = activeSession?.durationMinutes ?: computedDurationMin

    var timeRemaining by remember(actualEndTime) { mutableStateOf(max(0L, actualEndTime - TrustedClock.now())) }

    LaunchedEffect(actualEndTime) {
        if (actualEndTime > 0L) {
            while (timeRemaining > 0) {
                kotlinx.coroutines.delay(1000L)
                timeRemaining = max(0L, actualEndTime - TrustedClock.now())
            }
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
        if (days > 0) parts.add("${days}d")
        if (hours > 0 || days > 0) parts.add("${hours}h")
        if (minutes > 0 || hours > 0 || days > 0) parts.add("${minutes}m")
        parts.add("${seconds}s")
        parts.joinToString(" ")
    }

    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val startTimeString = remember(startTime) { timeFormatter.format(Date(startTime)) }
    val endTimeString = remember(actualEndTime) { timeFormatter.format(Date(actualEndTime)) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val blockEndDateString = remember(actualEndTime) { dateFormatter.format(Date(actualEndTime)) }

    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .navigationBarsPadding()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Centerpiece matching Panel 4 of the Neon Focus Buddy UI Theme Board
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NeonCircularLockEmblem(size = 152.dp)

                Text(
                    text = if (isLongTerm && longTermType == "WEBSITE") "Website Blocked" else "Blocked",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "You can't access this app right now.\nIt's part of your focus plan.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Main Blocked App Info Card (Exact Panel 4 layout: App Icon + Name + Limit/Status + Timer)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = NeonSurface,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Neon gradient squircle app icon badge (Instagram / App style)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                NeonMagenta,
                                                NeonViolet,
                                                Color(0xFFFF7A00)
                                            )
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(15.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = appName.firstOrNull()?.uppercase() ?: "A",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = appName.ifBlank { "Blocked App" },
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = when {
                                        longTermReason.isNotEmpty() -> longTermReason
                                        isLongTerm -> "Long-term block active"
                                        activeSession?.isStrict == true -> "Strict Mode • Daily focus plan"
                                        else -> "Daily limit reached"
                                    },
                                    color = TextSecondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = NeonMagenta,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (timeRemaining > 0L) "$timeString remaining" else "0h 0m / ${durationMinutes}m",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // Compact session / block metadata row
                        if (isLongTerm) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Unlocks On", color = TextSecondary, fontSize = 12.sp)
                                Text(blockEndDateString, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Session: $startTimeString – $endTimeString",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${durationMinutes}m plan",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Glowing Neon-Bordered "Go Back" Pill Button matching Panel 4
                NeonOutlinedPillButton(
                    text = "Go Back",
                    onClick = onReturnHome,
                    modifier = Modifier.fillMaxWidth(),
                    height = 56.dp,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            NeonBetterFocusTagline()
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
    var timeRemaining by remember(endTime) { mutableStateOf(max(0L, endTime - TrustedClock.now())) }

    LaunchedEffect(endTime) {
        while (timeRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            timeRemaining = max(0L, endTime - TrustedClock.now())
        }
    }

    val totalSeconds = timeRemaining / 1000
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val timeString = remember(hours, minutes, seconds) {
        if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
    }

    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NeonCircularLockEmblem(size = 148.dp)

                Text(
                    text = "Blocked",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "You can't access $contentType right now.\nIt's part of your focus plan.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = NeonSurface,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(22.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(listOf(NeonMagenta, NeonViolet, NeonCyan))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = contentType.ifBlank { "Short-Form Content" },
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Content shield active in Strict Mode",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            if (timeRemaining > 0L) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = NeonMagenta,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$timeString left",
                                        color = NeonCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                NeonOutlinedPillButton(
                    text = "Go Back",
                    onClick = onGoBack,
                    modifier = Modifier.fillMaxWidth(),
                    height = 56.dp,
                    fontSize = 17.sp
                )
            }

            NeonBetterFocusTagline()
        }
    }
}

@Composable
private fun NeonBetterFocusTagline() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = NeonCyan, fontWeight = FontWeight.SemiBold)) {
                append("Better Focus. ")
            }
            withStyle(SpanStyle(color = NeonMagenta, fontWeight = FontWeight.SemiBold)) {
                append("Brighter Future.")
            }
        },
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
