package com.example.ui.screens

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.Alert
import com.example.ui.theme.Amber
import com.example.ui.theme.Hairline
import com.example.ui.theme.Panel
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.Phosphor
import com.example.ui.theme.PhosphorDim
import com.example.ui.theme.TextFaint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.Void
import com.example.viewmodel.FocusViewModel
import com.example.viewmodel.LauncherAppInfo
import java.text.SimpleDateFormat
import java.util.Locale

/** Launches [packageName]'s default launcher activity, if it still resolves to one. */
private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    }
}

/**
 * The study-first home screen shown when Focuss Buddy is set as the device's Home app
 * (Launcher Mode). Surfaces today's progress instead of a normal app grid, and only
 * exposes the apps the user has explicitly pinned as "study apps" - everything else
 * lives one tap away behind [AppDrawerScreen] rather than being on the home screen.
 */
@Composable
fun LauncherHomeScreen(
    viewModel: FocusViewModel,
    onOpenAppDrawer: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenShortcut: (String) -> Unit = {}
) {
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingLauncherApps.collectAsStateWithLifecycle()
    val studyAppPackages by viewModel.studyAppPackages.collectAsStateWithLifecycle()
    val lockedAppPackages by viewModel.lockedAppPackages.collectAsStateWithLifecycle()
    val appQuotaInfo by viewModel.appQuotaInfo.collectAsStateWithLifecycle()
    val recentAppPackages by viewModel.recentAppPackages.collectAsStateWithLifecycle()
    val advancedAnalytics by viewModel.advancedAnalytics.collectAsStateWithLifecycle()
    val weeklyTrends by viewModel.weeklyTrends.collectAsStateWithLifecycle()
    val allTests by viewModel.allTests.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val todayTopApps by viewModel.todayTopApps.collectAsStateWithLifecycle()
    val currentAccentTheme by viewModel.accentTheme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var dndRefreshTrigger by remember { mutableStateOf(0) }
    val isDndOn = remember(dndRefreshTrigger) {
        notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }
    var showThemePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (launcherApps.isEmpty()) viewModel.loadLauncherApps()
        viewModel.loadTodayTopApps()
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val studyApps = remember(launcherApps, studyAppPackages) {
        launcherApps.filter { it.packageName in studyAppPackages }
    }
    val nextTest = remember(allTests, now) {
        allTests.firstOrNull { it.dateMillis >= now }
    }
    val isSessionActive = remember(activeSession, now) {
        activeSession?.let { it.isActive && now < it.endTime } ?: false
    }

    // Swipe-up-to-open-drawer: accumulate the upward drag across the gesture and fire
    // onOpenAppDrawer once it crosses a threshold, mirroring a normal launcher's
    // swipe-up-from-home gesture as an alternative to the "All Apps" button.
    var dragAccumulator by remember { mutableStateOf(0f) }
    val swipeUpModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                dragAccumulator += dragAmount
                if (dragAccumulator < -140f) {
                    onOpenAppDrawer()
                    dragAccumulator = 0f
                }
            }
        )
    }

    // Long-press anywhere on the home screen background (like a real launcher) opens
    // the accent-theme picker - a quick way to restyle without going to Dashboard.
    val longPressModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { showThemePicker = true })
    }

    if (showThemePicker) {
        AccentThemePickerDialog(
            currentTheme = currentAccentTheme,
            onSelect = { viewModel.setAccentTheme(it) },
            onDismiss = { showThemePicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .then(swipeUpModifier)
            .then(longPressModifier)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            if (isSessionActive) {
                val session = activeSession!!
                val minutesLeft = ((session.endTime - now) / 60_000L).coerceAtLeast(0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PhosphorDim)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = Phosphor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (session.isStrict) "Strict session active" else "Focus session active",
                        color = Phosphor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "${minutesLeft}m left", color = Phosphor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Clock + Quick DND toggle
            val timeText = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
            val dateText = remember(now) { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = timeText, color = TextPrimary, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text(text = dateText, color = TextSecondary, fontSize = 14.sp)
                }
                IconButton(
                    onClick = {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(
                                if (isDndOn) NotificationManager.INTERRUPTION_FILTER_ALL
                                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            )
                            dndRefreshTrigger++
                        } else {
                            try {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            } catch (e: Exception) {
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDndOn) PhosphorDim else Panel)
                ) {
                    Icon(
                        imageVector = if (isDndOn) Icons.Default.DoNotDisturbOn else Icons.Default.NotificationsActive,
                        contentDescription = "Toggle Do Not Disturb",
                        tint = if (isDndOn) Phosphor else TextFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Streak + today's focus time
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LauncherStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = Amber,
                    value = "${advancedAnalytics.currentStreak}",
                    label = "day streak"
                )
                val todayMinutes = advancedAnalytics.todayFocusTimeSeconds / 60
                LauncherStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    iconTint = Phosphor,
                    value = "${todayMinutes}m",
                    label = "focused today"
                )
            }

            if (nextTest != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val daysLeft = remember(nextTest, now) {
                    ((nextTest.dateMillis - now) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${nextTest.testType} ${nextTest.testNumber} (${nextTest.level})",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (daysLeft == 0L) "Today" else "${daysLeft}d left",
                        color = Alert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // JEE Mains / Advanced countdown
            Text(text = "JEE 2027", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val mainsDaysLeft = remember(now) { ((JEE_MAINS_2027_MILLIS - now) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0) }
                JeeCountdownCard(modifier = Modifier.weight(1f), label = "JEE Mains", value = "${mainsDaysLeft}d")
                JeeCountdownCard(modifier = Modifier.weight(1f), label = "JEE Advanced", value = "TBD")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // This week's focus time
            Text(text = "THIS WEEK", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            WeeklyBarChart(trends = weeklyTrends)

            Spacer(modifier = Modifier.height(24.dp))

            // Last 30 days streak heatmap
            Text(text = "LAST 4 WEEKS", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            StreakHeatmap(days = advancedAnalytics.calendarDays)

            Spacer(modifier = Modifier.height(24.dp))

            // Quick shortcuts
            Text(text = "QUICK ACCESS", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickShortcutTile(modifier = Modifier.weight(1f), icon = Icons.Default.Quiz, label = "PYQ Quiz", onClick = { onOpenShortcut("pyq_practice") })
                QuickShortcutTile(modifier = Modifier.weight(1f), icon = Icons.Default.BarChart, label = "Insights", onClick = { onOpenShortcut("insights") })
                QuickShortcutTile(modifier = Modifier.weight(1f), icon = Icons.Default.Schedule, label = "Schedules", onClick = { onOpenShortcut("schedule_manager") })
                QuickShortcutTile(modifier = Modifier.weight(1f), icon = Icons.Default.Park, label = "Forest", onClick = { onOpenShortcut("forest_gallery") })
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Today's top apps (real system-wide usage, not just quota-tracked ones)
            Text(text = "TODAY'S TOP APPS", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (!viewModel.isUsageStatsPermissionGranted()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel)
                        .clickable {
                            try {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } catch (e: Exception) {
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Grant usage access to see today's top apps here",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (todayTopApps.isEmpty()) {
                Text(text = "No usage yet today", color = TextFaint, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todayTopApps.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Panel)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (entry.icon != null) {
                                Image(bitmap = entry.icon.asImageBitmap(), contentDescription = entry.appName, modifier = Modifier.size(28.dp))
                            } else {
                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(PanelElevated))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = entry.appName, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            val minutes = entry.timeMillis / 60_000L
                            Text(text = "${minutes}m", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val recentAppsResolved = remember(recentAppPackages, launcherApps, studyAppPackages) {
                recentAppPackages
                    .mapNotNull { pkg -> launcherApps.find { it.packageName == pkg } }
                    .filter { it.packageName !in studyAppPackages }
            }
            if (recentAppsResolved.isNotEmpty()) {
                Text(text = "RECENT", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    recentAppsResolved.forEach { app ->
                        Box(modifier = Modifier.weight(1f)) {
                            LauncherAppIcon(
                                app = app,
                                onClick = {
                                    viewModel.recordRecentApp(app.packageName)
                                    launchApp(context, app.packageName)
                                },
                                onLongClick = { viewModel.toggleStudyApp(app.packageName) }
                            )
                            if (app.packageName in lockedAppPackages) {
                                LockBadge(modifier = Modifier.align(Alignment.TopStart))
                            }
                        }
                    }
                    repeat(4 - recentAppsResolved.size.coerceAtMost(4)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "STUDY APPS", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Long-press an app in All Apps to pin it here",
                    color = TextFaint,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingApps && studyApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Phosphor, modifier = Modifier.size(24.dp))
                }
            } else if (studyApps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "No study apps pinned yet", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Open All Apps below and long-press one to pin it here",
                        color = TextFaint,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(studyApps, key = { it.packageName }) { app ->
                        val quota = appQuotaInfo[app.packageName]
                        val quotaFraction = quota?.let { (used, limit) ->
                            if (limit <= 0L) null else (1f - (used.toFloat() / limit.toFloat())).coerceIn(0f, 1f)
                        }
                        Box {
                            LauncherAppIcon(
                                app = app,
                                onClick = {
                                    viewModel.recordRecentApp(app.packageName)
                                    launchApp(context, app.packageName)
                                },
                                onLongClick = { viewModel.toggleStudyApp(app.packageName) },
                                quotaFraction = quotaFraction
                            )
                            if (app.packageName in lockedAppPackages) {
                                LockBadge(modifier = Modifier.align(Alignment.TopStart))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onOpenDashboard,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
            ) {
                Text("Dashboard")
            }
            Button(
                onClick = onOpenAppDrawer,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PhosphorDim, contentColor = Phosphor)
            ) {
                Icon(imageVector = Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("All Apps")
            }
        }
    }
}

/** Fixed at 2027-01-20 00:00:00 local time. */
private val JEE_MAINS_2027_MILLIS: Long = java.util.Calendar.getInstance().apply {
    set(2027, java.util.Calendar.JANUARY, 20, 0, 0, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun JeeCountdownCard(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PanelElevated)
            .padding(14.dp)
    ) {
        Text(text = value, color = Alert, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextFaint, fontSize = 11.sp)
    }
}

@Composable
private fun QuickShortcutTile(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Phosphor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, color = TextSecondary, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Alert),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked",
            tint = Void,
            modifier = Modifier.size(10.dp)
        )
    }
}

/** Simple 7-bar chart of this week's focus minutes, reusing the same [com.example.viewmodel.TrendPoint]
 * data the Insights screen's weekly trend is built from. */
@Composable
private fun WeeklyBarChart(trends: List<com.example.viewmodel.TrendPoint>) {
    if (trends.isEmpty()) {
        Text(text = "No sessions yet this week", color = TextFaint, fontSize = 12.sp)
        return
    }
    val maxMinutes = (trends.maxOfOrNull { it.actualFocusMinutes } ?: 0f).coerceAtLeast(1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        trends.forEach { point ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val fraction = (point.actualFocusMinutes / maxMinutes).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Phosphor)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = point.dateLabel.take(1), color = TextFaint, fontSize = 9.sp)
            }
        }
    }
}

/** Last 4 weeks as a GitHub-style contribution heatmap, reusing the same
 * [com.example.viewmodel.CalendarDay] data behind the Insights screen's calendar view. */
@Composable
private fun StreakHeatmap(days: List<com.example.viewmodel.CalendarDay>) {
    if (days.isEmpty()) {
        Text(text = "No history yet", color = TextFaint, fontSize = 12.sp)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(80.dp)
    ) {
        items(days, key = { it.dateString }) { day ->
            val color = when {
                day.isProductive -> Phosphor
                day.isMissed -> Alert.copy(alpha = 0.4f)
                else -> PanelElevated
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
                    .then(
                        if (day.isToday) Modifier.border(1.dp, TextPrimary, RoundedCornerShape(3.dp)) else Modifier
                    )
            )
        }
    }
}

/** Bottom-sheet-style dialog for picking Focuss Buddy's accent theme, opened by a
 * long-press on the Launcher Mode home screen background. */
@Composable
private fun AccentThemePickerDialog(
    currentTheme: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Panel)
                .padding(20.dp)
        ) {
            Text(text = "Accent Theme", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            AccentTheme.values().forEach { theme ->
                val isSelected = theme.displayName == currentTheme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelect(theme.displayName)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(theme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = theme.displayName,
                        color = if (isSelected) theme.primary else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Current", tint = theme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    label: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextFaint, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherAppIcon(
    app: LauncherAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    quotaFraction: Float? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(58.dp),
            contentAlignment = Alignment.Center
        ) {
            if (quotaFraction != null) {
                // Remaining-quota ring, drawn like a battery/countdown ring - full circle
                // when the full daily quota is still left, shrinking as it's used up.
                val ringColor = if (quotaFraction <= 0.15f) Alert else Phosphor
                Canvas(modifier = Modifier.size(58.dp)) {
                    val stroke = 3.dp.toPx()
                    drawArc(
                        color = ringColor.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
                        size = Size(size.width - stroke, size.height - stroke),
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * quotaFraction.coerceIn(0f, 1f),
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
                        size = Size(size.width - stroke, size.height - stroke),
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PanelElevated),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Text(text = app.appName.take(1), color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.appName,
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Full alphabetical list of every launchable app on the device, with search and
 * long-press-to-pin. This is the only place all apps are reachable from in Launcher
 * Mode - anything not pinned as a study app lives here rather than on the home screen.
 */
@Composable
fun AppDrawerScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit
) {
    val launcherApps by viewModel.launcherApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingLauncherApps.collectAsStateWithLifecycle()
    val studyAppPackages by viewModel.studyAppPackages.collectAsStateWithLifecycle()
    val lockedAppPackages by viewModel.lockedAppPackages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (launcherApps.isEmpty()) viewModel.loadLauncherApps()
    }

    val filtered = remember(launcherApps, query) {
        if (query.isBlank()) launcherApps
        else launcherApps.filter { it.appName.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Void)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(text = "All Apps", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search apps", color = TextFaint) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextFaint) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextFaint)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Phosphor,
                unfocusedBorderColor = Hairline,
                cursorColor = Phosphor
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Text(
            text = "Tap to open · Long-press to pin/unpin as a study app",
            color = TextFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (isLoadingApps && filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Phosphor)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val isPinned = app.packageName in studyAppPackages
                    val isLocked = app.packageName in lockedAppPackages
                    Box {
                        LauncherAppIcon(
                            app = app,
                            onClick = {
                                viewModel.recordRecentApp(app.packageName)
                                launchApp(context, app.packageName)
                            },
                            onLongClick = { viewModel.toggleStudyApp(app.packageName) }
                        )
                        if (isPinned) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Phosphor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Void,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        if (isLocked) {
                            LockBadge(modifier = Modifier.align(Alignment.TopStart))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Explainer + entry point for Launcher Mode, reached from the home dashboard's drawer.
 * Lets the user preview the study-only home screen without committing, and jumps to
 * the system's "Default apps > Home app" picker to actually set Focuss Buddy as Home.
 */
@Composable
fun LauncherModeSettingsScreen(
    onBack: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Void).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(text = "Launcher Mode", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Turn your phone's home screen into a study-only surface: streak, focus time, and next test up front, with only your pinned study apps one tap away. Everything else lives behind All Apps instead of your home screen.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PhosphorDim, contentColor = Phosphor)
        ) {
            Text("Preview It", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Text("Set as Default Home App")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This opens Android's own Default apps screen - you can switch back to your regular launcher there any time.",
            color = TextFaint,
            fontSize = 11.sp
        )
    }
}
