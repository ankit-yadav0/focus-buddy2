package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.NeonHorizontalBrush
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.FocusViewModel

/**
 * Dedicated "MORE / BLOCKS" tab screen: houses Strict Mode Content Controls, Morning 5–9 AM Daily Strict Lock,
 * 7s Mindful Launch Delay, Keyword & Topic Blocker, Long-Term Blocks, and Daily Dashboard in the
 * Neon Black Theme.
 */
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
        !context.getSharedPreferences("focuss_buddy_settings", android.content.Context.MODE_PRIVATE)
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

                // 1. Strict Mode Content Controls (Shorts, Reels, Spotlight, YouTube Study Mode, WhatsApp Chat-Only)
                ContentLevelBlockingCard(viewModel = viewModel)

                // 2. Morning "First Task" Lock (Daily 5:00 AM - 9:00 AM Automatic Strict Mode)
                MorningFirstTaskLockCard(viewModel = viewModel)

                // 3. Mindful Launch Delay (7s Breathing Gate)
                MindfulLaunchDelayCard(viewModel = viewModel)

                // 4. Keyword & Topic Blocker (Browser + YouTube)
                KeywordTopicBlockerCard(viewModel = viewModel)

                // 5. Long-Term App & Website Blocks
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

                // 6. Daily Dashboard
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
 * Dedicated "Focus & Analytics" ("STATS") tab screen matching Panel 7 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Weekly / Monthly segmented neon pill bar
 *   • Vertical Neon Bar Chart (Mon..Sun with #FF00E6 -> #7B61FF -> #00E5FF vertical gradient bars)
 *   • Streak Heatmap (Mon..Fri grid of glowing #00E5FF, #7B61FF, #FF00E6, and #1A1A24 cells)
 *   • Subject Progress (Physics 68%, Chemistry 52%, Mathematics 63%, Biology 34% with NeonGradientProgressBar)
 *   • Plus the full detailed AnalyticsCard below!
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
        !context.getSharedPreferences("focuss_buddy_settings", android.content.Context.MODE_PRIVATE)
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

                // 1. Weekly | Monthly Segmented Pill Bar (Exact Panel 7)
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

                // 2. Vertical Neon Gradient Bar Chart (Mon..Sun, 0h..8h — Exact Panel 7)
                NeonFocusBarChartCard(weeklyTrends = weeklyTrends)

                // 3. Streak Heatmap (Mon..Fri grid of #00E5FF, #7B61FF, #FF00E6, #1A1A24 cells — Exact Panel 7)
                NeonStreakHeatmapCard()

                // 4. Subject Progress (Physics 68%, Chemistry 52%, Mathematics 63%, Biology 34% — Exact Panel 7)
                NeonSubjectProgressCard()

                // 5. Full Detailed Analytics Dashboard Card (preserves all existing tabs & testTags)
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
    weeklyTrends: List<com.example.viewmodel.TrendPoint>
) {
    val defaultHeights = listOf(0.88f, 0.45f, 0.78f, 0.72f, 0.62f, 0.48f, 0.85f)
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val fractions = remember(weeklyTrends) {
        val hasRealData = weeklyTrends.any { it.actualFocusMinutes > 0f }
        if (hasRealData && weeklyTrends.size == 7) {
            val maxM = (weeklyTrends.maxOfOrNull { it.actualFocusMinutes } ?: 60f).coerceAtLeast(60f)
            weeklyTrends.map { (it.actualFocusMinutes / maxM).coerceIn(0.12f, 1f) }
        } else {
            defaultHeights
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Y-axis labels: 8h, 6h, 4h, 2h, 0h
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf("8h", "6h", "4h", "2h", "0h").forEach { label ->
                        Text(label, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // 7 Vertical Neon Gradient Bars
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayLabels.forEachIndexed { idx, day ->
                        val frac = fractions.getOrElse(idx) { 0.6f }
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .width(16.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(frac)
                                ) {
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(NeonMagenta, NeonViolet, NeonCyan)
                                        ),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = day,
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
private fun NeonStreakHeatmapCard() {
    val rows = listOf(
        "Mon" to listOf(NeonCyan, NeonSurface, NeonMagenta, NeonCyan, NeonViolet, NeonMagenta, NeonSurface, NeonMagenta),
        "Tue" to listOf(NeonViolet, NeonCyan, NeonCyan, NeonViolet, NeonMagenta, NeonSurface, NeonCyan, NeonViolet),
        "Wed" to listOf(NeonMagenta, NeonMagenta, NeonSurface, NeonCyan, NeonCyan, NeonMagenta, NeonMagenta, NeonSurface),
        "Thu" to listOf(NeonCyan, NeonViolet, NeonMagenta, NeonSurface, NeonViolet, NeonCyan, NeonMagenta, NeonMagenta),
        "Fri" to listOf(NeonMagenta, NeonCyan, NeonViolet, NeonMagenta, NeonCyan, NeonViolet, NeonSurface, NeonCyan)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Streak Heatmap",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
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
                rows.forEach { (dayLabel, colors) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dayLabel,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.width(34.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colors.forEach { cellColor ->
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (cellColor == NeonSurface) Color(0xFF252538)
                                            else cellColor
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeonSubjectProgressCard() {
    data class SubjectStat(val name: String, val percent: Int, val icon: ImageVector, val tint: Color)

    val subjects = listOf(
        SubjectStat("Physics", 68, Icons.Default.Science, NeonCyan),
        SubjectStat("Chemistry", 52, Icons.Default.Biotech, NeonMagenta),
        SubjectStat("Mathematics", 63, Icons.Default.Functions, NeonViolet),
        SubjectStat("Biology", 34, Icons.Default.Spa, Color(0xFF00E676))
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Subject Progress",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
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
                subjects.forEach { subj ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(subj.tint.copy(alpha = 0.18f))
                                .border(1.dp, subj.tint.copy(alpha = 0.65f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = subj.icon,
                                contentDescription = subj.name,
                                tint = subj.tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = subj.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${subj.percent}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                            NeonGradientProgressBar(
                                progress = subj.percent / 100f,
                                height = 6.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated "FOREST" tab screen in the Neon Black Theme.
 */
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
        !context.getSharedPreferences("focuss_buddy_settings", android.content.Context.MODE_PRIVATE)
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
