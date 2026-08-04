package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.FocusViewModel

/**
 * Dedicated screen for Long-Term Blocks + Daily Dashboard.
 * Previously these were embedded inline in HomeScreen; they now live behind
 * their own bottom-nav tab ("BLOCKS") so Home doesn't need a giant scroll
 * and this content only shows up when that tab is tapped.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
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
                title = { Text("Blocks & Daily Progress", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

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

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(80.dp))
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
 * Dedicated screen for Focuss Buddy Insights (analytics), previously an
 * inline card at the bottom of HomeScreen. Reachable only via the "STATS"
 * bottom-nav tab.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
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
                title = { Text("Focuss Buddy Insights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

            AnalyticsCard(
                analytics = analytics,
                dailyAnalytics = dailyAnalytics,
                weeklyTrends = weeklyTrends,
                advancedAnalytics = advancedAnalytics,
                allSessions = allSessions
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Dedicated screen for My Forest Gallery, previously an inline card in
 * HomeScreen. Reachable only via the "FOREST" bottom-nav tab.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
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
                title = { Text("My Forest Gallery", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

            MyForestGalleryCard(maturedSessions = maturedSessions)

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
