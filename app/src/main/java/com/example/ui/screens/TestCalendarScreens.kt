package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonDisciplineFooter
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.NeonHorizontalBrush
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonOutlinedPillButton
import com.example.ui.components.NeonSquircleIconBadge
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.components.NeonTargetLightningLogo
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.FocusViewModel

data class NeonTestScheduleItem(
    val title: String,
    val dateTimeLabel: String,
    val daysLeft: Int
)

/**
 * Test Calendar Screen matching Panel 8 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Top bar with "+ Add Test" neon-bordered pill button
 *   • "Upcoming" / "All" segmented neon pill selector
 *   • Test cards (JEE Mock Test 1, Chemistry Test, Physics Test, Full Syllabus Mock)
 *     with neon cyan calendar icon and #FF00E6 "X days left" pill badge
 *   • "Import Tests (Pipe Delimited)" card at bottom with "Import" button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestCalendarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableStateOf("Upcoming") }
    var pipeInput by remember { mutableStateOf("") }
    var showAddTestDialog by remember { mutableStateOf(false) }

    val defaultTests = remember {
        listOf(
            NeonTestScheduleItem("JEE Mock Test 1", "24 Sep 2025  10:00 AM", 2),
            NeonTestScheduleItem("Chemistry Test", "28 Sep 2025  9:00 AM", 6),
            NeonTestScheduleItem("Physics Test", "02 Oct 2025  9:00 AM", 10),
            NeonTestScheduleItem("Full Syllabus Mock", "10 Oct 2025  9:00 AM", 18)
        )
    }

    var testItems by remember {
        val savedRaw = prefs.getString("neon_test_calendar_items", null)
        val parsed = if (!savedRaw.isNullOrBlank()) {
            savedRaw.lines().mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size >= 2) {
                    NeonTestScheduleItem(
                        title = parts[0],
                        dateTimeLabel = parts[1],
                        daysLeft = parts.getOrNull(2)?.toIntOrNull() ?: 7
                    )
                } else null
            }
        } else emptyList()
        mutableStateOf(if (parsed.isNotEmpty()) parsed else defaultTests)
    }

    fun persistTests(updated: List<NeonTestScheduleItem>) {
        testItems = updated
        val serialized = updated.joinToString("\n") { "${it.title}|${it.dateTimeLabel}|${it.daysLeft}" }
        prefs.edit().putString("neon_test_calendar_items", serialized).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Test Calendar",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
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
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NeonSurface)
                            .border(1.4.dp, NeonCardBorderBrush, RoundedCornerShape(50))
                            .clickable { showAddTestDialog = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Add Test",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Segmented Pill Toggle: Upcoming | All (Exact Panel 8)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(NeonSurface)
                        .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    listOf("Upcoming", "All").forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) NeonBlack else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    brush = if (isSelected) NeonHorizontalBrush else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { selectedTab = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }

                // Test List Cards (Exact Panel 8 design)
                testItems.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                NeonSquircleIconBadge(
                                    icon = Icons.Default.CalendarToday,
                                    tint = NeonCyan,
                                    size = 40.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.dateTimeLabel,
                                        color = TextSecondary,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(NeonMagenta.copy(alpha = 0.14f))
                                    .border(1.dp, NeonMagenta.copy(alpha = 0.75f), RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${item.daysLeft} days left",
                                    color = NeonMagenta,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Import Tests (Pipe Delimited) Section (Exact Panel 8)
                Text(
                    text = "Import Tests (Pipe Delimited)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pipeInput,
                            onValueChange = { pipeInput = it },
                            placeholder = {
                                Text(
                                    text = "Paste your test data here... (e.g. Physics Mock | 15 Nov 2026 9:00 AM | 5)",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            NeonOutlinedPillButton(
                                text = "Import",
                                onClick = {
                                    val newEntries = pipeInput.lines().mapNotNull { line ->
                                        val parts = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (parts.isNotEmpty()) {
                                            NeonTestScheduleItem(
                                                title = parts[0],
                                                dateTimeLabel = parts.getOrNull(1) ?: "Upcoming  9:00 AM",
                                                daysLeft = parts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 7
                                            )
                                        } else null
                                    }
                                    if (newEntries.isNotEmpty()) {
                                        persistTests(testItems + newEntries)
                                        pipeInput = ""
                                    }
                                },
                                height = 40.dp,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showAddTestDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newDateLabel by remember { mutableStateOf("30 Oct 2026  9:00 AM") }
        var newDaysLeft by remember { mutableStateOf("14") }

        AlertDialog(
            onDismissRequest = { showAddTestDialog = false },
            containerColor = NeonSurface,
            title = { Text("Add Scheduled Test", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Test Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newDateLabel,
                        onValueChange = { newDateLabel = it },
                        label = { Text("Date & Time") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newDaysLeft,
                        onValueChange = { newDaysLeft = it },
                        label = { Text("Days Remaining") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            persistTests(
                                testItems + NeonTestScheduleItem(
                                    title = newTitle.trim(),
                                    dateTimeLabel = newDateLabel.trim(),
                                    daysLeft = newDaysLeft.toIntOrNull() ?: 7
                                )
                            )
                        }
                        showAddTestDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBlack)
                ) {
                    Text("Save Test", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTestDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * App Usage / Daily Quota Screen matching Panel 5 of `Neon Focus Buddy UI Theme Board.png`:
 *   • "Today", "7 Days", "30 Days" pill selector
 *   • App usage rows (YouTube, Instagram, Chrome, WhatsApp, Spotify, Other) with NeonGradientProgressBar
 *   • Bottom "Daily Quota" card with thick neon gradient progress bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageQuotaScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf("Today") }
    val longTermBlocks by viewModel.allLongTermBlocks.collectAsStateWithLifecycle()

    data class UsageRow(val name: String, val timeText: String, val percent: Int, val color: Color)

    val usageRows = remember(selectedRange) {
        when (selectedRange) {
            "7 Days" -> listOf(
                UsageRow("YouTube", "14h 20m", 36, Color(0xFFFF1744)),
                UsageRow("Instagram", "8h 10m", 20, NeonMagenta),
                UsageRow("Chrome", "6h 40m", 15, NeonCyan),
                UsageRow("WhatsApp", "4h 50m", 12, Color(0xFF00E676)),
                UsageRow("Spotify", "3h 15m", 9, Color(0xFF1DB954)),
                UsageRow("Other", "2h 10m", 8, NeonViolet)
            )
            "30 Days" -> listOf(
                UsageRow("YouTube", "58h 10m", 35, Color(0xFFFF1744)),
                UsageRow("Instagram", "31h 45m", 19, NeonMagenta),
                UsageRow("Chrome", "24h 12m", 14, NeonCyan),
                UsageRow("WhatsApp", "19h 30m", 12, Color(0xFF00E676)),
                UsageRow("Spotify", "14h 05m", 10, Color(0xFF1DB954)),
                UsageRow("Other", "11h 20m", 10, NeonViolet)
            )
            else -> listOf(
                UsageRow("YouTube", "2h 14m", 34, Color(0xFFFF1744)),
                UsageRow("Instagram", "1h 12m", 18, NeonMagenta),
                UsageRow("Chrome", "56m", 12, NeonCyan),
                UsageRow("WhatsApp", "42m", 11, Color(0xFF00E676)),
                UsageRow("Spotify", "28m", 7, Color(0xFF1DB954)),
                UsageRow("Other", "22m", 6, NeonViolet)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Usage",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Today | 7 Days | 30 Days Segmented Pill Bar (Exact Panel 5)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(NeonSurface)
                        .border(1.dp, Color(0xFF2E3248), RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    listOf("Today", "7 Days", "30 Days").forEach { tab ->
                        val isSelected = selectedRange == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) NeonBlack else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { selectedRange = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Usage Rows (Exact Panel 5)
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
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        usageRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(row.color.copy(alpha = 0.22f))
                                        .border(1.dp, row.color.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = row.name.first().toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = row.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                text = row.timeText,
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${row.percent}%",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    NeonGradientProgressBar(
                                        progress = (row.percent / 40f).coerceIn(0.08f, 1f),
                                        height = 6.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Daily Quota Card (Exact Panel 5)
                val activeQuotaBlock = longTermBlocks.firstOrNull { it.dailyLimitSeconds != null }
                val quotaTitle = activeQuotaBlock?.targetLabel ?: "YouTube"
                val quotaProgress = if (activeQuotaBlock?.dailyLimitSeconds != null && activeQuotaBlock.dailyLimitSeconds > 0) {
                    (activeQuotaBlock.usedSecondsToday.toFloat() / activeQuotaBlock.dailyLimitSeconds.toFloat()).coerceIn(0f, 1f)
                } else {
                    0.74f
                }
                val quotaLabel = if (activeQuotaBlock?.dailyLimitSeconds != null) {
                    val usedM = activeQuotaBlock.usedSecondsToday / 60
                    val limM = activeQuotaBlock.dailyLimitSeconds / 60
                    "${usedM}m / ${limM}m"
                } else {
                    "2h 14m / 3h"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.4.dp, NeonCardBorderBrush, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(NeonBlack)
                                        .border(1.5.dp, NeonMagenta, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFFFF1744),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Daily Quota",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = quotaTitle,
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Text(
                                text = quotaLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        NeonGradientProgressBar(
                            progress = quotaProgress,
                            height = 11.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * All Apps / Launcher Drawer Screen matching Panel 3 of `Neon Focus Buddy UI Theme Board.png`:
 *   • "All Apps" header
 *   • "Search apps..." neon search bar
 *   • Category filter pills: All (active gradient), Study, Social, Tools, Games
 *   • 4-column app grid + "Pinned Study Apps" (Physics, Chemistry, Mathematics, Biology) at bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAppsDrawerScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedApps by viewModel.appListState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Study", "Social", "Tools", "Games")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "All Apps",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Color.White
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search apps... bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = NeonSurface,
                        unfocusedContainerColor = NeonSurface,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0xFF2E3248)
                    )
                )

                // Category Pills: All, Study, Social, Tools, Games (Exact Panel 3)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val active = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) NeonHorizontalBrush
                                    else Brush.horizontalGradient(listOf(NeonSurface, NeonSurface))
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) NeonCyan else Color(0xFF2E3248),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                color = Color.White,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // App Grid (Combines installed apps or curated default tiles matching Panel 3)
                val filteredInstalled = remember(installedApps, searchQuery, selectedCategory) {
                    installedApps.filter { app ->
                        (searchQuery.isBlank() || app.appName.contains(searchQuery, ignoreCase = true))
                    }.take(16)
                }

                if (filteredInstalled.isNotEmpty()) {
                    val rows = filteredInstalled.chunked(4)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        rows.forEach { rowApps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowApps.forEach { app ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                if (launch != null) {
                                                    try { context.startActivity(launch) } catch (_: Exception) {}
                                                }
                                            }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(15.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(NeonCyan.copy(alpha = 0.25f), NeonMagenta.copy(alpha = 0.25f))
                                                    )
                                                )
                                                .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(15.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.appName.firstOrNull()?.uppercase() ?: "A",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp
                                            )
                                        }
                                        Text(
                                            text = app.appName,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                repeat(4 - rowApps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // Fallback 4x3 showcase grid matching Panel 3 when no apps match or on emulator
                    val showcaseApps = listOf(
                        Triple("Chrome", NeonCyan, Icons.Default.Public),
                        Triple("YouTube", Color(0xFFFF1744), Icons.Default.PlayArrow),
                        Triple("WhatsApp", Color(0xFF00E676), Icons.Default.Chat),
                        Triple("Spotify", Color(0xFF1DB954), Icons.Default.MusicNote),
                        Triple("Instagram", NeonMagenta, Icons.Default.CameraAlt),
                        Triple("Telegram", NeonCyan, Icons.Default.Send),
                        Triple("Discord", NeonViolet, Icons.Default.Forum),
                        Triple("Calculator", NeonCyan, Icons.Default.Calculate)
                    )
                    showcaseApps.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { (name, tint, icon) ->
                                Column(
                                    modifier = Modifier.weight(1f).padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NeonSquircleIconBadge(icon = icon, tint = tint, size = 54.dp)
                                    Text(name, color = Color.White, fontSize = 11.5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Pinned Study Apps (Exact Panel 3 bottom section)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinned Study Apps",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonMagenta,
                        modifier = Modifier.size(18.dp)
                    )
                }

                NeonStudyAppsQuickRow(onNavigateToRoute = onNavigateToRoute)

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * Reusable "Study Apps" 4-item row (Physics, Chemistry, Mathematics, Biology)
 * shown on both the Home Screen (Panel 2) and All Apps Drawer (Panel 3).
 */
@Composable
fun NeonStudyAppsQuickRow(
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    data class StudyTile(val label: String, val icon: ImageVector, val color: Color, val route: String)

    val tiles = listOf(
        StudyTile("Physics", Icons.Default.Science, NeonCyan, "study_planner"),
        StudyTile("Chemistry", Icons.Default.Biotech, NeonMagenta, "pyq_practice"),
        StudyTile("Mathematics", Icons.Default.Functions, NeonCyan, "test_calendar"),
        StudyTile("Biology", Icons.Default.Spa, Color(0xFF00E676), "app_usage")
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.forEach { tile ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToRoute(tile.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonSurface)
                        .border(
                            width = 1.3.dp,
                            brush = Brush.linearGradient(
                                listOf(tile.color.copy(alpha = 0.8f), NeonViolet.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tile.icon,
                        contentDescription = tile.label,
                        tint = tile.color,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = tile.label,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Settings Hub Screen matching Panel 10 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Focus Buddy v1.0.0 top card with Target + Lightning logo
 *   • App Blocking, Daily Quota, Strict Mode, Launcher Mode, Study Planner,
 *     Test Calendar, PYQ Practice, Appearance, About
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonSettingsHubScreen(
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Focus Buddy v1.0.0 Card (Exact Panel 10)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.4.dp, NeonCardBorderBrush, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            NeonTargetLightningLogo(size = 44.dp)
                            Column {
                                Text(
                                    text = "Focus Buddy",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "v1.0.0 • Neon Black Edition",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val items = listOf(
                    Triple("App Blocking", Icons.Default.AppBlocking, "app_selection"),
                    Triple("Daily Quota & Usage", Icons.Default.BarChart, "app_usage"),
                    Triple("Strict Mode", Icons.Default.Lock, "timer"),
                    Triple("Launcher Mode (All Apps)", Icons.Default.Apps, "all_apps"),
                    Triple("Study Planner", Icons.Default.EventNote, "study_planner"),
                    Triple("Test Calendar", Icons.Default.CalendarToday, "test_calendar"),
                    Triple("PYQ Practice", Icons.Default.Quiz, "pyq_practice"),
                    Triple("App Lock (PIN Gate)", Icons.Default.Password, "app_lock"),
                    Triple("Banking Mode (Pause)", Icons.Default.Shield, "banking_pause"),
                    Triple("Blocks & Content Shield", Icons.Default.Security, "blocks_progress")
                )

                items.forEachIndexed { idx, (label, icon, route) ->
                    val tint = if (idx % 2 == 0) NeonMagenta else NeonCyan
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                            .clickable { onNavigateToRoute(route) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                NeonDisciplineFooter()
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
