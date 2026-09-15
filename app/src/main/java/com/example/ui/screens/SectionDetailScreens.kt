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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.FocusViewModel

/**
 * Dedicated screen for Long-Term Blocks + Website Blocks. Reachable via the
 * "BLOCKS" bottom-nav tab.
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
                title = { Text("Blocks", fontWeight = FontWeight.Bold) },
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
