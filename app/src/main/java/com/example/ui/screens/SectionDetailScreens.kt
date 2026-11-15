package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FocusSession
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.RealAppIconBadge
import com.example.ui.components.RealAppUsageItem
import com.example.ui.components.queryRealPhoneAppUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.components.NeonHorizontalBrush
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalendarDay
import com.example.viewmodel.FocusViewModel
import com.example.viewmodel.TrendPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksProgressScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onNavigateToBlockDetails: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val longTermBlocks by viewModel.allLongTermBlocks.collectAsStateWithLifecycle()
    val websiteBlocks by viewModel.allWebsiteBlocks.collectAsStateWithLifecycle()

    var showAddLongTermBlockDialog by remember { mutableStateOf(false) }
    var scheduleJustAdded by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var isAutoTriggered by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val hasWallpaper = remember {
        !context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .getString("custom_wallpaper_uri", null).isNullOrEmpty()
    }

    LaunchedEffect(Unit) {
        viewModel.actionBlockedMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(scheduleJustAdded) {
        if (scheduleJustAdded) {
            scheduleJustAdded = false
            val shown = viewModel.isBatteryOptimizationPromptShown()
            if (!shown) {
                isAutoTriggered = true
                showBatteryOptimizationDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Blocks & Content Shield",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "⚡", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            bgAlpha = if (hasWallpaper) 0.48f else 1.0f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                ContentLevelBlockingCard(viewModel = viewModel)
                MorningFirstTaskLockCard(viewModel = viewModel)
                MindfulLaunchDelayCard(viewModel = viewModel)
                KeywordTopicBlockerCard(viewModel = viewModel)

                LongTermBlockSection(
                    appBlocks = longTermBlocks,
                    websiteBlocks = websiteBlocks,
                    onAddBlockClick = { showAddLongTermBlockDialog = true },
                    onRemoveBlockClick = { id -> viewModel.removeLongTermBlock(id) },
                    onRemoveWebsiteBlockClick = { id -> viewModel.removeWebsiteBlock(id) },
                    onBlockClick = onNavigateToBlockDetails,
                    onBatteryGuidanceClick = {
                        isAutoTriggered = false
                        showBatteryOptimizationDialog = true
                    }
                )

                DailyDashboardCard(viewModel = viewModel)

                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    if (showAddLongTermBlockDialog) {
        AddLongTermBlockDialog(
            installedApps = viewModel.installedApps.collectAsStateWithLifecycle().value,
            onDismiss = { showAddLongTermBlockDialog = false },
            onConfirm = { type, target, label, reason, start, end, dailyLimitSeconds ->
                if (type == "APP") {
                    viewModel.addLongTermBlock(type, target, label, reason, start, end, dailyLimitSeconds)
                } else {
                    viewModel.addWebsiteBlock(target, reason, start, end)
                }
                showAddLongTermBlockDialog = false
                scheduleJustAdded = true
            }
        )
    }

    if (showBatteryOptimizationDialog) {
        BatteryOptimizationDialog(
            onDismiss = {
                if (isAutoTriggered) {
                    viewModel.setBatteryOptimizationPromptShown()
                }
                showBatteryOptimizationDialog = false
            }
        )
    }
}

/**
 * Dedicated "Focus & Analytics" ("STATS") tab screen — 100% Real Session Data:
 *   • Weekly / Monthly toggle switches between 7-day daily bars and 4-week monthly bars from Room DB
 *   • Streak Heatmap renders the user's real 28-day session history from advancedAnalytics.calendarDays
 *   • Subject Progress tracks real persisted subject accuracy/minutes & lets the user tap any subject to update
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val dailyAnalytics by viewModel.dailyAnalytics.collectAsStateWithLifecycle()
    val weeklyTrends by viewModel.weeklyTrends.collectAsStateWithLifecycle()
    val advancedAnalytics by viewModel.advancedAnalytics.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val hasWallpaper = remember {
        !context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .getString("custom_wallpaper_uri", null).isNullOrEmpty()
    }

    var selectedPeriod by remember { mutableStateOf("Weekly") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Focus & Analytics",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White,
                        maxLines = 1
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            bgAlpha = if (hasWallpaper) 0.48f else 1.0f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // 1. Weekly | Monthly Segmented Pill Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(NeonSurface)
                        .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    listOf("Weekly", "Monthly").forEach { period ->
                        val active = selectedPeriod == period
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) NeonBlack else Color.Transparent)
                                .border(
                                    width = if (active) 1.5.dp else 0.dp,
                                    brush = if (active) NeonHorizontalBrush else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { selectedPeriod = period }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                color = if (active) Color.White else TextSecondary,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }

                // 2. Real Vertical Neon Gradient Bar Chart (Weekly 7 days or Monthly 4 weeks)
                NeonFocusBarChartCard(
                    selectedPeriod = selectedPeriod,
                    weeklyTrends = weeklyTrends,
                    allSessions = allSessions,
                    weeklyHours = advancedAnalytics.weeklyFocusHours,
                    monthlyHours = advancedAnalytics.monthlyFocusHours
                )

                // 3. Real 28-Day Streak Heatmap bound to advancedAnalytics.calendarDays & allSessions
                NeonStreakHeatmapCard(
                    calendarDays = advancedAnalytics.calendarDays,
                    allSessions = allSessions
                )

                // 4. Real & Interactive Subject Progress Card
                NeonSubjectProgressCard(context = context)

                // 5. Full Detailed Analytics Dashboard Card
                AnalyticsCard(
                    analytics = analytics,
                    dailyAnalytics = dailyAnalytics,
                    weeklyTrends = weeklyTrends,
                    advancedAnalytics = advancedAnalytics,
                    allSessions = allSessions
                )

                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun NeonFocusBarChartCard(
    selectedPeriod: String,
    weeklyTrends: List<TrendPoint>,
    allSessions: List<FocusSession>,
    weeklyHours: Float,
    monthlyHours: Float
) {
    val chartBars: List<Pair<String, Float>> = remember(selectedPeriod, weeklyTrends, allSessions) {
        if (selectedPeriod == "Weekly") {
            if (weeklyTrends.isNotEmpty()) {
                weeklyTrends.map { it.dateLabel to (it.actualFocusMinutes / 60f) }
            } else {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").map { it to 0f }
            }
        } else {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 3600L * 1000L
            (3 downTo 0).map { weekOffset ->
                val wEnd = now - weekOffset * 7L * dayMs
                val wStart = wEnd - 7L * dayMs
                val secs = allSessions
                    .filter { it.startTime in wStart..wEnd && !it.isActive && it.sessionStatus != "Active" }
                    .sumOf { it.actualDurationSeconds }
                "W${4 - weekOffset}" to (secs / 3600f)
            }
        }
    }

    val maxHoursAxis = remember(chartBars, selectedPeriod) {
        val peak = chartBars.maxOfOrNull { it.second } ?: 0f
        if (selectedPeriod == "Weekly") maxOf(8f, peak * 1.15f) else maxOf(20f, peak * 1.15f)
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedPeriod == "Weekly") "Past 7 Days Focus Hours" else "Past 4 Weeks Focus Hours",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = String.format(Locale.US, "Total: %.1fh", if (selectedPeriod == "Weekly") weeklyHours else monthlyHours),
                    color = NeonCyan,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val topInt = maxHoursAxis.roundToInt()
                    listOf(
                        "${topInt}h",
                        "${(topInt * 3) / 4}h",
                        "${topInt / 2}h",
                        "${topInt / 4}h",
                        "0h"
                    ).forEach { label ->
                        Text(label, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    chartBars.forEach { (label, hrs) ->
                        val frac = if (hrs <= 0f) 0.04f else (hrs / maxHoursAxis).coerceIn(0.08f, 1f)
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = if (hrs >= 0.1f) String.format(Locale.US, "%.1fh", hrs) else "0m",
                                color = if (hrs > 0f) NeonCyan else TextSecondary.copy(alpha = 0.5f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .width(if (selectedPeriod == "Weekly") 16.dp else 28.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(frac)
                                ) {
                                    drawRoundRect(
                                        brush = if (hrs > 0f) {
                                            Brush.verticalGradient(
                                                colors = listOf(NeonMagenta, NeonViolet, NeonCyan)
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF28283C), Color(0xFF28283C))
                                            )
                                        },
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = label,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeonStreakHeatmapCard(
    calendarDays: List<CalendarDay>,
    allSessions: List<FocusSession>
) {
    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val minutesByDate = remember(allSessions) {
        val map = mutableMapOf<String, Long>()
        for (s in allSessions) {
            if (!s.isActive && (s.sessionStatus == "Completed" || s.sessionStatus == "Expired")) {
                val key = sdfDate.format(Date(s.startTime))
                map[key] = (map[key] ?: 0L) + (s.actualDurationSeconds / 60L)
            }
        }
        map
    }

    val weeks = remember(calendarDays) {
        calendarDays.chunked(7)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Streak Heatmap (Past 28 Days)",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF252538)))
                Text("0m", color = TextSecondary, fontSize = 10.sp)
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(NeonViolet))
                Text("<30m", color = TextSecondary, fontSize = 10.sp)
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(NeonCyan))
                Text("30m+", color = TextSecondary, fontSize = 10.sp)
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(NeonMagenta))
                Text("90m+", color = TextSecondary, fontSize = 10.sp)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NeonSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (weeks.isEmpty()) {
                    Text(
                        text = "Complete your first Focus Session to light up your Streak Heatmap!",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                } else {
                    weeks.forEachIndexed { wIdx, weekDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "W${wIdx + 1}",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.width(34.dp)
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                weekDays.forEach { day ->
                                    val mins = minutesByDate[day.dateString] ?: 0L
                                    val cellColor = when {
                                        mins >= 90L -> NeonMagenta
                                        mins >= 30L -> NeonCyan
                                        mins > 0L || day.isProductive -> NeonViolet
                                        else -> Color(0xFF252538)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(cellColor)
                                            .border(
                                                width = if (day.isToday) 1.5.dp else 0.dp,
                                                color = if (day.isToday) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(7.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${day.dayOfMonth}",
                                            color = if (mins > 0L || day.isProductive) Color.White else TextSecondary.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeonSubjectProgressCard(context: Context) {
    val realUsage by produceState<List<RealAppUsageItem>>(initialValue = emptyList(), key1 = context) {
        value = withContext(Dispatchers.IO) {
            queryRealPhoneAppUsage(context, "Today")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Real Phone App Usage",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Text(
                text = if (realUsage.isNotEmpty()) "${realUsage.size} apps active" else "Live UsageStats",
                color = NeonCyan,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NeonSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (realUsage.isEmpty()) {
                    Text(
                        text = "Grant Usage Access permission to see your real phone screen time for YouTube, Instagram, WhatsApp, Chrome, and all installed apps.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                } else {
                    val maxMs = realUsage.first().usageMillis.coerceAtLeast(1L)
                    realUsage.take(6).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RealAppIconBadge(
                                packageName = item.packageName,
                                appName = item.appName,
                                size = 38.dp,
                                cornerRadius = 10.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.appName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${item.formattedTime} (${item.percentOfTotal}%)",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                                NeonGradientProgressBar(
                                    progress = (item.usageMillis.toFloat() / maxMs.toFloat()).coerceIn(0.06f, 1f),
                                    height = 6.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestGalleryScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maturedSessions by viewModel.maturedSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasWallpaper = remember {
        !context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .getString("custom_wallpaper_uri", null).isNullOrEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "My Forest Gallery",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "⚡", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            bgAlpha = if (hasWallpaper) 0.48f else 1.0f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                MyForestGalleryCard(maturedSessions = maturedSessions)

                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}
