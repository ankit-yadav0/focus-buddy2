package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonDisciplineFooter
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.NeonHorizontalBrush
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonOutlinedPillButton
import com.example.ui.components.NeonPrimaryGradientButton
import com.example.ui.components.NeonSquircleIconBadge
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.components.NeonTargetLightningLogo
import com.example.ui.components.RealAppIconBadge
import com.example.ui.components.RealAppUsageItem
import com.example.ui.components.classifyRealAppCategory
import com.example.ui.components.formatUsageDuration
import com.example.ui.components.queryRealPhoneAppUsage
import com.example.ui.components.queryRecentPhoneApps
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppInfo
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class NeonTestScheduleItem(
    val id: Long,
    val title: String,
    val targetMillis: Long
)

/**
 * Test Calendar Screen (Panel 8) — 100% Real & Dynamic:
 *   • Uses real timestamps (targetMillis) with DatePickerDialog & TimePickerDialog
 *   • Computes real remaining days dynamically from the phone's current clock
 *   • Supports "Upcoming" vs "All" filtering, deleting any test, and pipe-delimited import
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

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    var testItems by remember {
        val savedRaw = prefs.getString("neon_real_test_calendar_v2", null)
        val parsed = if (savedRaw != null) {
            savedRaw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size >= 2) {
                    val id = parts.getOrNull(2)?.toLongOrNull() ?: System.nanoTime()
                    val ms = parts[1].toLongOrNull()
                    if (ms != null) NeonTestScheduleItem(id, parts[0], ms) else null
                } else null
            }
        } else {
            emptyList()
        }
        mutableStateOf(parsed.sortedBy { it.targetMillis })
    }

    fun persistTests(updated: List<NeonTestScheduleItem>) {
        val sorted = updated.sortedBy { it.targetMillis }
        testItems = sorted
        val serialized = sorted.joinToString("\n") { "${it.title}|${it.targetMillis}|${it.id}" }
        prefs.edit().putString("neon_real_test_calendar_v2", serialized).apply()
    }

    val startOfToday = remember(nowMs) {
        Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val displayedTests = remember(testItems, selectedTab, startOfToday) {
        if (selectedTab == "Upcoming") {
            testItems.filter { it.targetMillis >= startOfToday }
        } else {
            testItems
        }
    }

    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.US) }

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

                // Segmented Pill Toggle: Upcoming | All
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
                                .background(if (isSelected) NeonBlack else Color.Transparent)
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
                                text = "$tab (${if (tab == "Upcoming") testItems.count { it.targetMillis >= startOfToday } else testItems.size})",
                                color = if (isSelected) Color.White else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (displayedTests.isEmpty()) {
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
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeonSquircleIconBadge(
                                icon = Icons.Default.CalendarToday,
                                tint = NeonCyan,
                                size = 48.dp
                            )
                            Text(
                                text = "No Tests Scheduled Yet",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Tap '+ Add Test' above or import pipe-delimited tests below to track your upcoming real exams with live day countdowns.",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                            NeonOutlinedPillButton(
                                text = "+ Add Your First Test",
                                onClick = { showAddTestDialog = true },
                                height = 44.dp
                            )
                        }
                    }
                } else {
                    displayedTests.forEach { item ->
                        val diffMs = item.targetMillis - startOfToday
                        val daysLeft = (diffMs / (24L * 3600L * 1000L)).toInt()
                        val badgeLabel = when {
                            daysLeft < 0 -> "Completed"
                            daysLeft == 0 -> "Today!"
                            daysLeft == 1 -> "1 day left"
                            else -> "$daysLeft days left"
                        }

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
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = dateTimeFormatter.format(Date(item.targetMillis)),
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (daysLeft == 0) NeonCyan.copy(alpha = 0.18f)
                                                else NeonMagenta.copy(alpha = 0.14f)
                                            )
                                            .border(
                                                1.dp,
                                                if (daysLeft == 0) NeonCyan else NeonMagenta.copy(alpha = 0.75f),
                                                RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 11.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            color = if (daysLeft == 0) NeonCyan else NeonMagenta,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            persistTests(testItems.filterNot { it.id == item.id })
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete test",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Import Tests (Pipe Delimited) Section
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
                                    text = "Format: Test Name | Days From Now (e.g. JEE Mock Test 1 | 5)",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp
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
                                            val title = parts[0]
                                            val daysFromNow = parts.lastOrNull()?.filter { it.isDigit() }?.toIntOrNull() ?: 7
                                            val cal = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_YEAR, daysFromNow)
                                                set(Calendar.HOUR_OF_DAY, 9)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                            }
                                            NeonTestScheduleItem(
                                                id = System.nanoTime(),
                                                title = title,
                                                targetMillis = cal.timeInMillis
                                            )
                                        } else null
                                    }
                                    if (newEntries.isNotEmpty()) {
                                        persistTests(testItems + newEntries)
                                        pipeInput = ""
                                        Toast.makeText(context, "Imported ${newEntries.size} test(s)", Toast.LENGTH_SHORT).show()
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
        var chosenCal by remember {
            mutableStateOf(
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 3)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
            )
        }
        var dateDisplay by remember {
            mutableStateOf(SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.US).format(chosenCal.time))
        }

        AlertDialog(
            onDismissRequest = { showAddTestDialog = false },
            containerColor = NeonSurface,
            title = { Text("Schedule New Test", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Test Name (e.g. JEE Mock Test 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    chosenCal.set(Calendar.YEAR, y)
                                    chosenCal.set(Calendar.MONTH, m)
                                    chosenCal.set(Calendar.DAY_OF_MONTH, d)
                                    TimePickerDialog(
                                        context,
                                        { _, hr, min ->
                                            chosenCal.set(Calendar.HOUR_OF_DAY, hr)
                                            chosenCal.set(Calendar.MINUTE, min)
                                            dateDisplay = SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.US).format(chosenCal.time)
                                        },
                                        chosenCal.get(Calendar.HOUR_OF_DAY),
                                        chosenCal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                chosenCal.get(Calendar.YEAR),
                                chosenCal.get(Calendar.MONTH),
                                chosenCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(dateDisplay, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            persistTests(
                                testItems + NeonTestScheduleItem(
                                    id = System.nanoTime(),
                                    title = newTitle.trim(),
                                    targetMillis = chosenCal.timeInMillis
                                )
                            )
                            showAddTestDialog = false
                        }
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
 * App Usage / Daily Quota Screen (Panel 5) — 100% Real Phone Data:
 *   • Queries Android's UsageStatsManager for real foreground app usage ("Today", "7 Days", "30 Days")
 *   • Displays each app's real icon from PackageManager, real duration, and real % of screen time
 *   • Prompts for Usage Access permission with 1-tap Settings redirect if not yet granted
 *   • Bottom "Daily Quota" section binds to real LongTermBlock daily quotas + lets the user add new app quotas!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageQuotaScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedRange by remember { mutableStateOf("Today") }
    var hasUsagePermission by remember { mutableStateOf(viewModel.isUsageStatsPermissionGranted()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isLoadingUsage by remember { mutableStateOf(true) }
    var realUsageList by remember { mutableStateOf<List<RealAppUsageItem>>(emptyList()) }

    val longTermBlocks by viewModel.allLongTermBlocks.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var showAddQuotaDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = viewModel.isUsageStatsPermissionGranted()
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedRange, hasUsagePermission, refreshTrigger) {
        if (!hasUsagePermission) {
            realUsageList = emptyList()
            isLoadingUsage = false
        } else {
            isLoadingUsage = true
            val stats = withContext(Dispatchers.IO) {
                queryRealPhoneAppUsage(context, selectedRange)
            }
            realUsageList = stats
            isLoadingUsage = false
        }
    }

    val totalScreenTimeMs = remember(realUsageList) {
        realUsageList.sumOf { it.usageMillis }
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
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Usage", tint = NeonCyan)
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
                // Today | 7 Days | 30 Days Segmented Pill Bar
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

                if (!hasUsagePermission) {
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Grant Usage Access for Real Phone Stats",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Allow Usage Access so Focuss Buddy can read your phone's real app screen-time (YouTube, Instagram, WhatsApp, Chrome, etc.) and enforce daily quotas.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            NeonPrimaryGradientButton(
                                text = "Grant Usage Access",
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 50.dp
                            )
                        }
                    }
                } else {
                    // Summary Banner showing real total screen time
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "$selectedRange Total Screen Time",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = formatUsageDuration(totalScreenTimeMs),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "${realUsageList.size} apps used",
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (isLoadingUsage) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    } else if (realUsageList.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = NeonSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No App Usage Recorded Yet ($selectedRange)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "As you use apps on your phone today, their exact screen time and percentage will appear here.",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val maxAppMs = realUsageList.first().usageMillis.coerceAtLeast(1L)
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
                                realUsageList.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        RealAppIconBadge(
                                            packageName = row.packageName,
                                            appName = row.appName,
                                            size = 42.dp,
                                            cornerRadius = 12.dp
                                        )
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
                                                    text = row.appName,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(
                                                        text = row.formattedTime,
                                                        color = TextSecondary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${row.percentOfTotal}%",
                                                        color = NeonCyan,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            NeonGradientProgressBar(
                                                progress = (row.usageMillis.toFloat() / maxAppMs.toFloat()).coerceIn(0.06f, 1f),
                                                height = 6.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Daily Quota Section — Real LongTermBlock Daily Quotas + "+ Set Daily Quota"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily App Quotas",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    TextButton(onClick = { showAddQuotaDialog = true }) {
                        Text("+ Set App Quota", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }

                val quotaBlocks = remember(longTermBlocks) {
                    longTermBlocks.filter { it.type == "APP" && it.dailyLimitSeconds != null }
                }

                if (quotaBlocks.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.4.dp, NeonCardBorderBrush, RoundedCornerShape(22.dp))
                            .clickable { showAddQuotaDialog = true },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Daily Quota",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "No daily app quota set yet — tap to cap any app's daily time",
                                        color = TextSecondary,
                                        fontSize = 12.5.sp
                                    )
                                }
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = NeonMagenta)
                            }
                            NeonGradientProgressBar(progress = 0f, height = 9.dp)
                        }
                    }
                } else {
                    quotaBlocks.forEach { block ->
                        val limitSecs = (block.dailyLimitSeconds ?: 3600L).coerceAtLeast(1L)
                        // Also check UsageStatsManager for today's usage if higher than tracked seconds
                        val usageStatSecs = (realUsageList.firstOrNull { it.packageName == block.target }?.usageMillis ?: 0L) / 1000L
                        val effectiveUsedSecs = maxOf(block.usedSecondsToday, usageStatSecs)
                        val quotaProgress = (effectiveUsedSecs.toFloat() / limitSecs.toFloat()).coerceIn(0f, 1f)
                        val quotaLabel = "${formatUsageDuration(effectiveUsedSecs * 1000L)} / ${formatUsageDuration(limitSecs * 1000L)}"

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
                                        RealAppIconBadge(
                                            packageName = block.target,
                                            appName = block.targetLabel,
                                            size = 42.dp,
                                            cornerRadius = 12.dp
                                        )
                                        Column {
                                            Text(
                                                text = "Daily Quota",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = block.targetLabel,
                                                color = TextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = quotaLabel,
                                        color = if (quotaProgress >= 1f) NeonMagenta else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                }

                                NeonGradientProgressBar(
                                    progress = quotaProgress,
                                    height = 11.dp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showAddQuotaDialog) {
        var searchApp by remember { mutableStateOf("") }
        var selectedApp by remember { mutableStateOf<AppInfo?>(installedApps.firstOrNull()) }
        var dailyMinutesText by remember { mutableStateOf("60") }
        var durationDaysText by remember { mutableStateOf("30") }

        val filteredApps = remember(installedApps, searchApp) {
            if (searchApp.isBlank()) installedApps
            else installedApps.filter { it.appName.contains(searchApp, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showAddQuotaDialog = false },
            containerColor = NeonSurface,
            title = { Text("Set Real App Daily Quota", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dailyMinutesText,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) dailyMinutesText = it },
                            label = { Text("Daily Mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = durationDaysText,
                            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) durationDaysText = it },
                            label = { Text("Lock Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = searchApp,
                        onValueChange = { searchApp = it },
                        placeholder = { Text("Search installed apps...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChosen = selectedApp?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isChosen) NeonCyan.copy(alpha = 0.16f) else Color.Transparent)
                                    .clickable { selectedApp = app }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RealAppIconBadge(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    size = 34.dp,
                                    cornerRadius = 9.dp
                                )
                                Text(
                                    text = app.appName,
                                    color = Color.White,
                                    fontWeight = if (isChosen) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                RadioButton(
                                    selected = isChosen,
                                    onClick = { selectedApp = app },
                                    colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetApp = selectedApp
                        val mins = dailyMinutesText.toLongOrNull() ?: 60L
                        val days = (durationDaysText.toLongOrNull() ?: 30L).coerceAtLeast(1L)
                        if (targetApp != null && mins > 0L) {
                            val now = System.currentTimeMillis()
                            viewModel.addLongTermBlock(
                                type = "APP",
                                target = targetApp.packageName,
                                targetLabel = targetApp.appName,
                                reason = "Daily quota: ${mins}m/day",
                                startDate = now,
                                endDate = now + days * 24L * 3600L * 1000L,
                                dailyLimitSeconds = mins * 60L
                            )
                            showAddQuotaDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBlack)
                ) {
                    Text("Save Quota", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddQuotaDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * All Apps / Launcher Drawer Screen (Panel 3) — 100% Real Phone Apps:
 *   • Shows ALL installed apps from the user's phone with their real icons
 *   • Category filter pills (All, Study, Social, Tools, Games) filter apps using ApplicationInfo.category & package heuristics
 *   • Customizable "Pinned Study Apps" at the bottom with an "Edit" picker dialog
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
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val prefs = remember { context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE) }
    var pinnedStudyPackages by remember {
        val raw = prefs.getString("pinned_study_packages", "") ?: ""
        mutableStateOf(raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }
    var showEditStudyAppsDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Study", "Social", "Tools", "Games")

    val filteredInstalled = remember(installedApps, searchQuery, selectedCategory, pinnedStudyPackages) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() || app.appName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = if (selectedCategory == "All") {
                true
            } else {
                classifyRealAppCategory(context, app.packageName, app.appName, pinnedStudyPackages) == selectedCategory
            }
            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "All Apps (${filteredInstalled.size})",
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps on your phone...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
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

                // Category Pills: All, Study, Social, Tools, Games
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

                if (isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                } else if (filteredInstalled.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No Apps Found in '$selectedCategory'",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (selectedCategory == "Study") {
                                Text(
                                    text = "Tap 'Edit' next to Pinned Study Apps below to mark any app on your phone as a Study App.",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
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
                                                    try {
                                                        context.startActivity(launch)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Unable to open ${app.appName}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        RealAppIconBadge(
                                            packageName = app.packageName,
                                            appName = app.appName,
                                            size = 54.dp,
                                            cornerRadius = 15.dp
                                        )
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
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Pinned Study Apps from the user's real phone
                RealPinnedStudyAppsSection(
                    installedApps = installedApps,
                    pinnedPackages = pinnedStudyPackages,
                    onEditClick = { showEditStudyAppsDialog = true },
                    onNavigateToRoute = onNavigateToRoute
                )

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showEditStudyAppsDialog) {
        EditPinnedStudyAppsDialog(
            installedApps = installedApps,
            currentPinned = pinnedStudyPackages,
            onDismiss = { showEditStudyAppsDialog = false },
            onSave = { updated ->
                pinnedStudyPackages = updated
                prefs.edit().putString("pinned_study_packages", updated.joinToString(",")).apply()
                showEditStudyAppsDialog = false
            }
        )
    }
}

/**
 * Displays the user's real Pinned Study Apps from their phone (with an "Edit" button to pick apps),
 * plus built-in study tools if fewer than 4 apps are pinned.
 */
@Composable
fun RealPinnedStudyAppsSection(
    installedApps: List<AppInfo>,
    pinnedPackages: Set<String>,
    onEditClick: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedStudyApps = remember(installedApps, pinnedPackages) {
        if (pinnedPackages.isNotEmpty()) {
            installedApps.filter { it.packageName in pinnedPackages }.take(8)
        } else {
            // Auto-detect study/utility apps installed on the user's phone
            val autoStudy = installedApps.filter {
                classifyRealAppCategory(context, it.packageName, it.appName, emptySet()) == "Study"
            }
            if (autoStudy.isNotEmpty()) autoStudy.take(4) else installedApps.take(4)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Study Apps",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onEditClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Study Apps",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Edit",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        }

        if (resolvedStudyApps.isNotEmpty()) {
            resolvedStudyApps.chunked(4).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                },
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
                                        brush = NeonCardBorderBrush,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                RealAppIconBadge(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    size = 44.dp,
                                    cornerRadius = 12.dp
                                )
                            }
                            Text(
                                text = app.appName,
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
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


    }
}

/**
 * Displays the user's 4 most recently used real phone apps from UsageStatsManager + a 5th "More / All Apps" tile,
 * matching the bottom "Recent Apps" row on Panel 2 of `Neon Focus Buddy UI Theme Board.png`.
 */
@Composable
fun RealRecentAppsRow(
    installedApps: List<AppInfo>,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentApps by produceState<List<AppInfo>>(initialValue = installedApps.take(4), key1 = installedApps) {
        value = withContext(Dispatchers.IO) {
            queryRecentPhoneApps(context, installedApps, maxCount = 4)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Recent Apps",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.5.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            recentApps.take(4).forEach { app ->
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeonSurface)
                        .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(14.dp))
                        .clickable {
                            val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (launch != null) {
                                try { context.startActivity(launch) } catch (_: Exception) {}
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    RealAppIconBadge(
                        packageName = app.packageName,
                        appName = app.appName,
                        size = 40.dp,
                        cornerRadius = 11.dp
                    )
                }
            }

        }
    }
}

@Composable
fun EditPinnedStudyAppsDialog(
    installedApps: List<AppInfo>,
    currentPinned: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedSet by remember(currentPinned) { mutableStateOf(currentPinned) }
    val filtered = remember(installedApps, search) {
        if (search.isBlank()) installedApps
        else installedApps.filter { it.appName.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        title = {
            Column {
                Text("Pin Your Real Study Apps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${selectedSet.size} apps pinned", color = NeonCyan, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search your installed apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selectedSet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSet = if (checked) selectedSet - app.packageName else selectedSet + app.packageName
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RealAppIconBadge(
                                packageName = app.packageName,
                                appName = app.appName,
                                size = 36.dp,
                                cornerRadius = 10.dp
                            )
                            Text(
                                text = app.appName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedSet = if (checked) selectedSet - app.packageName else selectedSet + app.packageName
                                },
                                colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedSet) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBlack)
            ) {
                Text("Save Study Apps", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Settings Hub Screen (Panel 10) — All 11 items wired to real screens & dialogs!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonSettingsHubScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showStrictWizard by remember { mutableStateOf(false) }
    val currentTheme by viewModel.accentTheme.collectAsStateWithLifecycle()

    if (showStrictWizard) {
        StrictModeSetupWizardScreen(
            viewModel = viewModel,
            onBack = { showStrictWizard = false },
            onActivated = {
                showStrictWizard = false
                onNavigateToRoute("timer")
            }
        )
        return
    }

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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.4.dp, NeonCardBorderBrush, RoundedCornerShape(20.dp))
                        .clickable { showAboutDialog = true },
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

                data class SettingEntry(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val action: () -> Unit)

                val entries = listOf(
                    SettingEntry("App Blocking", Icons.Default.AppBlocking) { onNavigateToRoute("app_selection") },
                    SettingEntry("Daily Quota & Real App Usage", Icons.Default.BarChart) { onNavigateToRoute("app_usage") },
                    SettingEntry("Strict Mode Lock-in", Icons.Default.Lock) { showStrictWizard = true },
                    SettingEntry("Recurring Strict Schedules", Icons.Default.Schedule) { onNavigateToRoute("schedule_manager") },
                    SettingEntry("App Lock (6-Digit PIN Gate)", Icons.Default.Password) { onNavigateToRoute("app_lock") },
                    SettingEntry("Focus Forest Gallery", Icons.Default.Park) { onNavigateToRoute("forest_gallery") },
                    SettingEntry("Appearance & Wallpaper", Icons.Default.Palette) { showAppearanceDialog = true },
                    SettingEntry("About Focus Buddy", Icons.Default.Info) { showAboutDialog = true }
                )

                entries.forEachIndexed { idx, entry ->
                    val tint = if (idx % 2 == 0) NeonMagenta else NeonCyan
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                            .clickable { entry.action() },
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
                                    imageVector = entry.icon,
                                    contentDescription = entry.label,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = entry.label,
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

    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            containerColor = NeonSurface,
            title = { Text("Appearance & Wallpaper", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Neon Accent Palette:", color = TextSecondary, fontSize = 13.sp)
                    AccentTheme.values().forEach { theme ->
                        val selected = theme.displayName == currentTheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) NeonCyan.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { viewModel.setAccentTheme(theme.displayName) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary)
                                )
                                Text(theme.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonCyan)
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showAppearanceDialog = false
                                onPickWallpaper()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Set Wallpaper", color = NeonCyan, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                showAppearanceDialog = false
                                onClearWallpaper()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Wallpaper", color = NeonMagenta, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Done", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = NeonSurface,
            title = { Text("About Focus Buddy", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Focus Buddy • Neon Black Edition", color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text(
                        "100% local, offline-first distraction blocker & study companion. All app usage stats, focus sessions, PIN hashes, and test schedules stay strictly on your phone.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
