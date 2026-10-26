package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.FocusViewModel

@Composable
private fun rememberLofiBgGradient(): Brush {
    val context = LocalContext.current
    val hasWallpaper = remember {
        !context.getSharedPreferences("focuss_buddy_settings", android.content.Context.MODE_PRIVATE)
            .getString("custom_wallpaper_uri", null).isNullOrEmpty()
    }
    val alpha = if (hasWallpaper) 0.45f else 1.0f
    return remember(alpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF171219).copy(alpha = alpha),
                Color(0xFF221824).copy(alpha = alpha),
                Color(0xFF120E14).copy(alpha = alpha)
            )
        )
    }
}

/**
 * Dedicated "BLOCKS" tab screen: houses Strict Mode Content Controls, Morning 5–9 AM Daily Strict Lock,
 * 7s Mindful Launch Delay, Keyword & Topic Blocker, Long-Term Blocks, and Daily Dashboard in the
 * Cozy Lofi Night-Study ("Night Owl") theme.
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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.actionBlockedMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(scheduleJustAdded) {
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
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFFFFF8E7),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "🦉", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFFB74D)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rememberLofiBgGradient())
                .padding(innerPadding)
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
 * Dedicated "STATS" tab screen in the Cozy Lofi Night-Study ("Night Owl") theme.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Focuss Buddy Insights",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFFFFF8E7),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "🦉", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFFB74D)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rememberLofiBgGradient())
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

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

/**
 * Dedicated "FOREST" tab screen in the Cozy Lofi Night-Study ("Night Owl") theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestGalleryScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maturedSessions by viewModel.maturedSessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "My Forest Gallery",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFFFFF8E7),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "🦉", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFFFB74D)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rememberLofiBgGradient())
                .padding(innerPadding)
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
