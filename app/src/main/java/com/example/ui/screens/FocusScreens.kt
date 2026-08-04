package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.example.data.StrictSchedule
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Analytics
import com.example.data.FocusSession
import com.example.data.LongTermBlock
import com.example.viewmodel.AppInfo
import com.example.viewmodel.FocusViewModel
import com.example.ui.theme.AccentTheme
import com.example.viewmodel.DailyAnalytics
import com.example.viewmodel.TrendPoint
import com.example.viewmodel.AdvancedAnalytics
import com.example.viewmodel.CalendarDay
import com.example.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FocusViewModel,
    onNavigateToTimer: () -> Unit,
    onNavigateToAppSelection: () -> Unit,
    onNavigateToBlockDetails: (Int, String) -> Unit,
    onNavigateToStudyChat: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    wallpaperOpacity: Float = 0.5f,
    onWallpaperOpacityChange: (Float) -> Unit = {},
    onNavigateToEmergencyUnlock: () -> Unit = {},
    onNavigateToUninstall: () -> Unit = {},
    onNavigateToStudyPlanner: () -> Unit = {},
    onNavigateToBlocksProgress: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToForestGallery: () -> Unit = {},

    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val longTermBlocks by viewModel.allLongTermBlocks.collectAsStateWithLifecycle()
    val websiteBlocks by viewModel.allWebsiteBlocks.collectAsStateWithLifecycle()

    val isStrictModeActive by viewModel.isStrictModeActive.collectAsStateWithLifecycle()
    val showReflectionPrompt by viewModel.showReflectionPrompt.collectAsStateWithLifecycle()

    if (showReflectionPrompt) {
        SessionReflectionPrompt(
            onSave = { text ->
                viewModel.saveReflectionNote(text)
            },
            onSkip = {
                viewModel.dismissReflectionPrompt()
            }
        )
    }

    var isAccessibilityEnabled by remember { mutableStateOf(viewModel.isAccessibilityServiceEnabled()) }
    var isUsageEnabled by remember { mutableStateOf(viewModel.isUsageStatsPermissionGranted()) }

    var isResumedTrigger by remember { mutableStateOf(0) }
    var studyPlanCompletionPercentage by remember { mutableStateOf<Float?>(null) }
    var isCelebratedAlready by remember { mutableStateOf(false) }
    var celebrateTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(isResumedTrigger) {
        val bankingActive = viewModel.getSetting("banking_mode_active") == "true"
        viewModel.bankingModeActive.value = bankingActive
    }

    LaunchedEffect(isResumedTrigger) {
        val pct = viewModel.getStudyPlanCompletionPercentage()
        studyPlanCompletionPercentage = pct
        val celebrated = viewModel.getSetting("syllabus_100_celebrated") == "true"
        isCelebratedAlready = celebrated
        
        if (pct != null && pct >= 100f) {
            if (!celebrated) {
                celebrateTrigger = true
                viewModel.saveSetting("syllabus_100_celebrated", "true")
                isCelebratedAlready = true
            }
        } else {
            celebrateTrigger = false
        }
    }

    // Refresh permission states when app resumes
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = viewModel.isAccessibilityServiceEnabled()
                isUsageEnabled = viewModel.isUsageStatsPermissionGranted()
                isResumedTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val sharedPrefs = remember {
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
    }
    val hasWallpaper = remember {
        !sharedPrefs.getString("custom_wallpaper_uri", null).isNullOrEmpty()
    }
    var showBrightnessTray by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(320.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Focuss Buddy",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = "Customization & Settings",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    WallpaperSettingsDrawerCard(
                        onPickWallpaper = {
                            scope.launch { drawerState.close() }
                            onPickWallpaper()
                        },
                        onClearWallpaper = {
                            scope.launch { drawerState.close() }
                            onClearWallpaper()
                        },
                        hasWallpaper = hasWallpaper,
                        onShowBrightnessTray = {
                            scope.launch { drawerState.close() }
                            showBrightnessTray = true
                        }
                    )





                    StudyPlannerDrawerCard(
                        onPlannerClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToStudyPlanner()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    UninstallDrawerCard(
                        onUninstallClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToUninstall()
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Focuss Buddy",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToDebug) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Debug Screen",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Hero Illustration / Card
                    HeroBannerCard(blockedCount = blockedApps.size)

                    BankingModeCard(viewModel = viewModel)

                    // Permission Warning Banner if any is missing
                    if (!isAccessibilityEnabled || !isUsageEnabled) {
                        PermissionsAlertCard(
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            isUsageEnabled = isUsageEnabled,
                            onGrantAccessibility = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            onGrantUsage = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            }
                        )
                    }

                    // Live active session widget
                    AnimatedVisibility(
                        visible = activeSession != null && activeSession?.isActive == true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        activeSession?.let { session ->
                            ActiveSessionWidget(
                                session = session,
                                onStopSession = { viewModel.stopActiveSession() },
                                onEmergencyUnlock = onNavigateToEmergencyUnlock,
                                completionPercentage = studyPlanCompletionPercentage,
                                celebrateTrigger = celebrateTrigger
                            )
                        }
                    }

                    // Blocks & Daily Progress - now a dedicated screen (see drawer menu)
                    SectionLinkCard(
                        icon = Icons.Default.Shield,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Blocks & Daily Progress",
                        subtitle = "${longTermBlocks.size + websiteBlocks.size} active blocks",
                        onClick = onNavigateToBlocksProgress,
                        testTag = "home_blocks_progress_link"
                    )

                    // Content-Level Blocking Settings
                    ContentLevelBlockingCard(viewModel = viewModel)

                    // Accent Theme Settings
                    AccentThemeCard(viewModel = viewModel)

                    // My Forest Gallery - now a dedicated screen (see drawer menu)
                    SectionLinkCard(
                        icon = Icons.Default.Park,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "My Forest Gallery",
                        subtitle = "Your completed focus milestones",
                        onClick = onNavigateToForestGallery,
                        testTag = "home_forest_gallery_link"
                    )

                    // Focuss Buddy Insights - now a dedicated screen (see drawer menu)
                    SectionLinkCard(
                        icon = Icons.Default.BarChart,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Focuss Buddy Insights",
                        subtitle = "Daily, weekly & monthly analytics",
                        onClick = onNavigateToInsights,
                        testTag = "home_insights_link"
                    )

                    // Quick Actions Title
                    Text(
                        text = "Session Quick Actions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Action Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onNavigateToTimer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("start_focus_session_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Icon"
                                )
                                Text(
                                    text = "Start Focus Session",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onNavigateToAppSelection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("view_blocked_apps_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AppBlocking,
                                    contentDescription = "Blocked Apps Icon"
                                )
                                Text(
                                    text = "View Blocked Apps",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(100.dp))
                }

                // Collapsible Wallpaper Brightness Adjustment Tray
                AnimatedVisibility(
                    visible = showBrightnessTray && hasWallpaper,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("brightness_adjustment_tray"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BrightnessMedium,
                                        contentDescription = "Brightness Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Wallpaper Brightness",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "${(wallpaperOpacity * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Slider(
                                    value = wallpaperOpacity,
                                    onValueChange = onWallpaperOpacityChange,
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                        thumbColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wallpaper_opacity_slider")
                                )

                                Button(
                                    onClick = { showBrightnessTray = false },
                                    modifier = Modifier.testTag("brightness_done_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // Infinite Cyberpunk Neon Hexagon honeycomb Shield Overlay when strict mode is active
                StrictShieldHexagonVisual(
                    isStrictModeActive = isStrictModeActive,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun BankingModeCard(viewModel: FocusViewModel) {
    val scope = rememberCoroutineScope()
    val bankingActive by viewModel.bankingModeActive.collectAsStateWithLifecycle()
    val endsAtMs by viewModel.bankingModeEndsAtMs.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (bankingActive) 0.9f else 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = "Banking Mode",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bankingActive) "Banking Mode active" else "Banking Mode",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (bankingActive) {
                        val remainingMin = ((endsAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L) + 1
                        "Protection off for ~${remainingMin}m so banking apps work. Tap the reminder notification to turn it back on."
                    } else {
                        "Turns off accessibility for 5 minutes so apps like your bank's app will work."
                    },
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            if (!bankingActive) {
                Button(
                    onClick = { showConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Turn on", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Turn on Banking Mode?") },
            text = {
                Text(
                    "This disables Focuss Buddy's accessibility protection for exactly 5 minutes " +
                    "so your banking app will run. It does NOT automatically turn back on after 5 " +
                    "minutes - Android doesn't allow apps to silently re-enable this permission for " +
                    "security reasons. You'll get a notification with a one-tap link to re-enable it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.activateBankingMode()
                }) { Text("Turn on for 5 minutes") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HeroBannerCard(blockedCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Stay in Flow",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Focuss Buddy locks distractions so you can achieve deep, focused work.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Check",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$blockedCount apps configured to block",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionsAlertCard(
    isAccessibilityEnabled: Boolean,
    isUsageEnabled: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantUsage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning icon",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "System Setup Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "Focuss Buddy needs system access permissions to actively detect and block distracted apps in the background.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isAccessibilityEnabled) {
                    Button(
                        onClick = onGrantAccessibility,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Accessibility",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!isUsageEnabled) {
                    Button(
                        onClick = onGrantUsage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Usage Stats",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

enum class GrowthStage {
    SEED,
    SAPLING,
    MATURED,
    WITHERED
}

@Composable
fun PlantVisual(
    stage: GrowthStage,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.radialGradient(
                    colors = when (stage) {
                        GrowthStage.SEED -> listOf(
                            Color(0x334CAF50), // Sprout Green
                            Color(0x004CAF50)
                        )
                        GrowthStage.SAPLING -> listOf(
                            Color(0x338BC34A), // Lime Green
                            Color(0x008BC34A)
                        )
                        GrowthStage.MATURED -> listOf(
                            Color(0x3300E676), // Bright Green
                            Color(0x0000E676)
                        )
                        GrowthStage.WITHERED -> listOf(
                            Color(0x33FF5722), // Autumn Orange
                            Color(0x00FF5722)
                        )
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when (stage) {
                GrowthStage.SEED -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(size * 0.7f)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_seed")
                        val scalePulse by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(scalePulse),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🌱",
                                fontSize = (size.value * 0.45f).sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                GrowthStage.SAPLING -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(size * 0.75f)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_sapling")
                        val scalePulse by infiniteTransition.animateFloat(
                            initialValue = 0.92f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(scalePulse),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🌿",
                                fontSize = (size.value * 0.5f).sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                GrowthStage.MATURED -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(size * 0.85f)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_tree")
                        val scalePulse by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.10f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(scalePulse),
                            contentAlignment = Alignment.Center
                        ) {
                            val angle by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "rotate"
                            )
                            
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val radius = size.toPx() * 0.38f
                                val angleRad = Math.toRadians(angle.toDouble())
                                val sx1 = center.x + radius * Math.cos(angleRad).toFloat()
                                val sy1 = center.y + radius * Math.sin(angleRad).toFloat()
                                val sx2 = center.x + radius * Math.cos(angleRad + Math.PI).toFloat()
                                val sy2 = center.y + radius * Math.sin(angleRad + Math.PI).toFloat()
                                
                                drawCircle(
                                    color = Color(0xFFFFD700),
                                    radius = 3.dp.toPx(),
                                    center = Offset(sx1, sy1)
                                )
                                drawCircle(
                                    color = Color(0xFFFFE082),
                                    radius = 2.dp.toPx(),
                                    center = Offset(sx2, sy2)
                                )
                            }
                            
                            Text(
                                text = "🌳",
                                fontSize = (size.value * 0.58f).sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                GrowthStage.WITHERED -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(size * 0.7f)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_withered")
                        val scalePulse by infiniteTransition.animateFloat(
                            initialValue = 0.96f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(scalePulse),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🥀",
                                fontSize = (size.value * 0.45f).sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FluidWaveProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = Color.Transparent
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "water_level_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave_animation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    if (animatedProgress > 0f) {
        Canvas(
            modifier = modifier.background(backgroundColor)
        ) {
            val width = size.width
            val height = size.height
            val waterLevelY = height * (1f - animatedProgress)
            val waveHeight = 8.dp.toPx()
            val waveLength = width

            // Wave 1
            val path1 = Path()
            path1.moveTo(0f, height)
            for (x in 0..width.toInt() step 2) {
                val angle = (2.0 * Math.PI * (x / waveLength)) - (phase * 2.0 * Math.PI)
                val y = waterLevelY + waveHeight * sin(angle).toFloat()
                path1.lineTo(x.toFloat(), y)
            }
            path1.lineTo(width, height)
            path1.close()

            drawPath(
                path = path1,
                color = waveColor,
                alpha = 0.55f
            )

            // Wave 2 (Phase offset of Math.PI)
            val path2 = Path()
            path2.moveTo(0f, height)
            for (x in 0..width.toInt() step 2) {
                val angle = (2.0 * Math.PI * (x / waveLength)) - (phase * 2.0 * Math.PI) + Math.PI
                val y = waterLevelY + waveHeight * sin(angle).toFloat()
                path2.lineTo(x.toFloat(), y)
            }
            path2.lineTo(width, height)
            path2.close()

            drawPath(
                path = path2,
                color = waveColor,
                alpha = 0.25f
            )
        }
    }
}

@Composable
fun AnimatedPlantBox(
    currentStage: GrowthStage,
    modifier: Modifier = Modifier,
    waveProgress: Float? = null
) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(Color.White.copy(alpha = 0.03f)),
        contentAlignment = Alignment.Center
    ) {
        if (waveProgress != null) {
            FluidWaveProgress(
                progress = waveProgress,
                modifier = Modifier.matchParentSize(),
                waveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                backgroundColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // SEED Animation
            AnimatedVisibility(
                visible = currentStage == GrowthStage.SEED,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                PlantVisual(
                    stage = GrowthStage.SEED,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // SAPLING Animation
            AnimatedVisibility(
                visible = currentStage == GrowthStage.SAPLING,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                PlantVisual(
                    stage = GrowthStage.SAPLING,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // MATURED Animation
            AnimatedVisibility(
                visible = currentStage == GrowthStage.MATURED,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                PlantVisual(
                    stage = GrowthStage.MATURED,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ActiveSessionWidget(
    session: FocusSession,
    onStopSession: () -> Unit,
    onEmergencyUnlock: () -> Unit = {},
    completionPercentage: Float? = null,
    celebrateTrigger: Boolean = false
) {
    var targetScale by remember { mutableStateOf(1f) }
    val scaleFactor by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        finishedListener = {
            if (targetScale == 1.15f) {
                targetScale = 1f
            }
        },
        label = "plantScaleAnimation"
    )

    LaunchedEffect(celebrateTrigger) {
        if (celebrateTrigger) {
            targetScale = 1.15f
        }
    }

    val endTime = session.endTime
    val startTime = session.startTime
    val durationMinutes = session.durationMinutes

    val totalDurationMs = remember(startTime, endTime) { (endTime - startTime).coerceAtLeast(1L) }
    var timeRemaining by remember(endTime) { mutableStateOf(max(0L, endTime - System.currentTimeMillis())) }
    var progressFraction by remember(startTime, endTime) {
        val elapsed = (System.currentTimeMillis() - startTime).coerceIn(0L, totalDurationMs)
        mutableStateOf(elapsed.toFloat() / totalDurationMs.toFloat())
    }

    LaunchedEffect(endTime) {
        while (timeRemaining > 0) {
            kotlinx.coroutines.delay(1000L)
            timeRemaining = max(0L, endTime - System.currentTimeMillis())
            val elapsed = (System.currentTimeMillis() - startTime).coerceIn(0L, totalDurationMs)
            progressFraction = elapsed.toFloat() / totalDurationMs.toFloat()
        }
    }

    val totalSeconds = timeRemaining / 1000
    val days = totalSeconds / (24 * 3600)
    val remainingSecs = totalSeconds % (24 * 3600)
    val hours = remainingSecs / 3600
    val minutes = (remainingSecs % 3600) / 60
    val seconds = remainingSecs % 60

    val timerString = remember(days, hours, minutes, seconds) {
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days Day${if (days != 1L) "s" else ""}")
        if (hours > 0 || days > 0) parts.add("$hours Hour${if (hours != 1L) "s" else ""}")
        if (minutes > 0 || hours > 0 || days > 0) parts.add("$minutes Minute${if (minutes != 1L) "s" else ""}")
        parts.add("$seconds Second${if (seconds != 1L) "s" else ""}")
        parts.joinToString(", ")
    }

    val timeFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val startTimeString = remember(startTime) { timeFormatter.format(Date(startTime)) }
    val endTimeString = remember(endTime) { timeFormatter.format(Date(endTime)) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("active_session_widget_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1F26A69A),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with "Focus Session Active" banner & Stop button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp)
                    ) {
                        QuantumOrbitProgressRing(
                            progress = progressFraction,
                            timeRemainingMs = timeRemaining,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        Icon(
                            imageVector = if (timeRemaining <= 0L) Icons.Default.Check else Icons.Default.HourglassFull,
                            contentDescription = "Session running",
                            tint = if (timeRemaining <= 0L) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "FOCUS SESSION ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        GlitchCountdownText(
                            timerString = timerString,
                            seconds = seconds,
                            isHyperFocus = timeRemaining <= 10 * 60 * 1000L && timeRemaining > 0
                        )
                    }
                }

                val isTimerFinished = timeRemaining <= 0L
                if (!session.isStrict || isTimerFinished) {
                    IconButton(
                        onClick = onStopSession,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isTimerFinished) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            )
                            .testTag("stop_session_icon_button")
                    ) {
                        Icon(
                            imageVector = if (isTimerFinished) Icons.Default.Check else Icons.Default.Stop,
                            contentDescription = if (isTimerFinished) "Complete focus session" else "Stop focus session",
                            tint = if (isTimerFinished) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Strict Mode Locked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Dynamic Plant Growth Visual Centerpiece
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("plant_growth_centerpiece"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isTimerFinished = timeRemaining <= 0L
                val currentStage = if (completionPercentage != null) {
                    when {
                        completionPercentage >= 67f -> GrowthStage.MATURED
                        completionPercentage >= 34f -> GrowthStage.SAPLING
                        else -> GrowthStage.SEED
                    }
                } else {
                    when {
                        isTimerFinished -> GrowthStage.MATURED
                        progressFraction >= 0.50f -> GrowthStage.SAPLING
                        else -> GrowthStage.SEED
                    }
                }

                AnimatedPlantBox(
                    currentStage = currentStage,
                    waveProgress = progressFraction,
                    modifier = Modifier.scale(scaleFactor)
                )

                AnimatedContent(
                    targetState = currentStage,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.9f))
                    },
                    label = "stageTextTransition"
                ) { stage ->
                    val (stageName, stageDesc) = when (stage) {
                        GrowthStage.MATURED -> Pair(
                            "Matured Tree! 🌲",
                            "Incredible work! Your tree has fully grown."
                        )
                        GrowthStage.SAPLING -> Pair(
                            "Sapling Stage 🌱",
                            "Keep going! Your sprout is turning into a sapling."
                        )
                        GrowthStage.SEED -> Pair(
                            "Sprout Stage 🌿",
                            "Your seed is sprouting. Maintain focus!"
                        )
                        GrowthStage.WITHERED -> Pair(
                            "Withered Sprout 🥀",
                            "This session was interrupted. Let's focus next time!"
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stageName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stageDesc,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Smooth linear progress bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("growth_progress_indicator"),
                        color = if (isTimerFinished) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Growth progress",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }


            }

            if (session.isStrict) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Strict Mode Alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "STRICT MODE ACTIVE",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        TextButton(
                            onClick = onEmergencyUnlock,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("emergency_unlock_button"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EMERGENCY UNLOCK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Details panel showing: Total duration, Start, End
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "TOTAL DURATION",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "$durationMinutes Min",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "START TIME",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = startTimeString,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "END TIME",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = endTimeString,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun LongTermBlockSection(
    appBlocks: List<LongTermBlock>,
    websiteBlocks: List<com.example.data.WebsiteBlock>,
    onAddBlockClick: () -> Unit,
    onRemoveBlockClick: (Int) -> Unit,
    onRemoveWebsiteBlockClick: (Int) -> Unit,
    onBlockClick: (Int, String) -> Unit,
    onBatteryGuidanceClick: () -> Unit
) {
    val now = System.currentTimeMillis()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Long-Term Blocks",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )

            TextButton(
                onClick = onAddBlockClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add block icon")
                    Text("Add Block", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (appBlocks.isEmpty() && websiteBlocks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "No long term blocks",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Long-Term Blocks Active",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        } else {
            // App Blocks subsection
            if (appBlocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Apps",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    appBlocks.forEach { block ->
                        val isLocked = now < block.endDate
                        val diff = block.endDate - now
                        val remainingDays = if (diff <= 0) 0 else (diff / (24 * 60 * 60 * 1000L))
                        val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
                        val startDateStr = remember(block.startDate) { dateFormatter.format(Date(block.startDate)) }
                        val endDateStr = remember(block.endDate) { dateFormatter.format(Date(block.endDate)) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBlockClick(block.id, "APP") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0x1F3DFFC4)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AppBlocking,
                                                contentDescription = "App block icon",
                                                tint = Color(0xFF3DFFC4),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = block.targetLabel,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            // Status tag
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = if (isLocked) "LOCKED" else "EXPIRED",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { if (!isLocked) onRemoveBlockClick(block.id) },
                                        enabled = !isLocked,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isLocked) Color(0x05FFFFFF) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete long term block",
                                            tint = if (isLocked) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (block.reason.isNotEmpty()) {
                                    Text(
                                        text = "Reason: ${block.reason}",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium
                                      )
                                  }

                                  block.dailyLimitSeconds?.let { limitSeconds ->
                                      val todayEpochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
                                      val usedSeconds = if (block.lastUsageResetEpochDay != todayEpochDay) 0L else block.usedSecondsToday
                                      val fraction = (usedSeconds.toFloat() / limitSeconds.toFloat()).coerceIn(0f, 1f)
                                      fun fmt(s: Long): String {
                                          val h = s / 3600
                                          val m = (s % 3600) / 60
                                          val sec = s % 60
                                          return when {
                                              h > 0 -> "${h}h ${m}m"
                                              m > 0 -> "${m}m ${sec}s"
                                              else -> "${sec}s"
                                          }
                                      }
                                      Column(
                                          modifier = Modifier.fillMaxWidth(),
                                          verticalArrangement = Arrangement.spacedBy(4.dp)
                                      ) {
                                          Row(
                                              modifier = Modifier.fillMaxWidth(),
                                              horizontalArrangement = Arrangement.SpaceBetween
                                          ) {
                                              Text(
                                                  text = "Today: ${fmt(usedSeconds)} / ${fmt(limitSeconds)}",
                                                  fontSize = 11.sp,
                                                  fontWeight = FontWeight.Bold,
                                                  color = if (fraction >= 1f) MaterialTheme.colorScheme.error else Color(0xFFFFA630)
                                              )
                                          }
                                          LinearProgressIndicator(
                                              progress = { fraction },
                                              modifier = Modifier
                                                  .fillMaxWidth()
                                                  .height(4.dp)
                                                  .clip(RoundedCornerShape(2.dp)),
                                              color = if (fraction >= 1f) MaterialTheme.colorScheme.error else Color(0xFFFFA630),
                                              trackColor = Color(0x10FFFFFF)
                                          )
                                      }
                                  }

                                  HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                  Row(
                                      modifier = Modifier.fillMaxWidth(),
                                      horizontalArrangement = Arrangement.SpaceBetween,
                                      verticalAlignment = Alignment.CenterVertically
                                  ) {
                                      Column(horizontalAlignment = Alignment.Start) {
                                          Text(
                                              text = "START DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold
                                          )
                                          Text(
                                              text = startDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold
                                          )
                                      }
                                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                          Text(
                                              text = "END DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold
                                          )
                                          Text(
                                              text = endDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold
                                          )
                                      }
                                      Column(horizontalAlignment = Alignment.End) {
                                          Text(
                                              text = "REMAINING",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold
                                          )
                                          Text(
                                              text = if (isLocked) "$remainingDays Days" else "Expired",
                                              fontSize = 12.sp,
                                              color = if (isLocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Black
                                          )
                                      }
                                  }
                              }
                          }
                      }
                  }
              }

              // Website Blocks subsection
              if (websiteBlocks.isNotEmpty()) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(
                          text = "Websites",
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color.White.copy(alpha = 0.5f),
                          modifier = Modifier.padding(horizontal = 4.dp)
                      )
                      websiteBlocks.forEach { block ->
                          val isLocked = now < block.endDate
                          val diff = block.endDate - now
                          val remainingDays = if (diff <= 0) 0 else (diff / (24 * 60 * 60 * 1000L))
                          val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
                          val startDateStr = remember(block.startDate) { dateFormatter.format(Date(block.startDate)) }
                          val endDateStr = remember(block.endDate) { dateFormatter.format(Date(block.endDate)) }

                          Card(
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .clickable { onBlockClick(block.id, "WEBSITE") },
                              shape = RoundedCornerShape(16.dp),
                              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                          ) {
                              Column(
                                  modifier = Modifier.padding(16.dp),
                                  verticalArrangement = Arrangement.spacedBy(12.dp)
                              ) {
                                  Row(
                                      modifier = Modifier.fillMaxWidth(),
                                      horizontalArrangement = Arrangement.SpaceBetween,
                                      verticalAlignment = Alignment.CenterVertically
                                  ) {
                                      Row(
                                          verticalAlignment = Alignment.CenterVertically,
                                          horizontalArrangement = Arrangement.spacedBy(12.dp)
                                      ) {
                                          Box(
                                              modifier = Modifier
                                                  .size(36.dp)
                                                  .clip(RoundedCornerShape(8.dp))
                                                  .background(Color(0x1FFFA630)),
                                              contentAlignment = Alignment.Center
                                          ) {
                                              Icon(
                                                  imageVector = Icons.Default.Language,
                                                  contentDescription = "Website block icon",
                                                  tint = Color(0xFFFFA630),
                                                  modifier = Modifier.size(18.dp)
                                              )
                                          }
                                          Column {
                                              Text(
                                                  text = block.domain,
                                                  fontWeight = FontWeight.Bold,
                                                  fontSize = 15.sp,
                                                  color = Color.White
                                              )
                                              // Status tag
                                              Row(
                                                  verticalAlignment = Alignment.CenterVertically,
                                                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                                              ) {
                                                  Icon(
                                                      imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.CheckCircle,
                                                      contentDescription = null,
                                                      tint = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                                                      modifier = Modifier.size(10.dp)
                                                  )
                                                  Text(
                                                      text = if (isLocked) "LOCKED" else "EXPIRED",
                                                      fontSize = 11.sp,
                                                      fontWeight = FontWeight.Bold,
                                                      color = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                                  )
                                              }
                                          }
                                      }

                                      IconButton(
                                          onClick = { if (!isLocked) onRemoveWebsiteBlockClick(block.id) },
                                          enabled = !isLocked,
                                          modifier = Modifier
                                              .size(32.dp)
                                              .clip(CircleShape)
                                              .background(
                                                  if (isLocked) Color(0x05FFFFFF) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                              )
                                      ) {
                                          Icon(
                                              imageVector = Icons.Default.Delete,
                                              contentDescription = "Delete website block",
                                              tint = if (isLocked) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error,
                                              modifier = Modifier.size(16.dp)
                                          )
                                      }
                                  }

                                  if (block.reason.isNotEmpty()) {
                                      Text(
                                          text = "Reason: ${block.reason}",
                                          fontSize = 13.sp,
                                          color = Color.White.copy(alpha = 0.8f),
                                          fontWeight = FontWeight.Medium
                                      )
                                  }

                                  HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                  Row(
                                      modifier = Modifier.fillMaxWidth(),
                                      horizontalArrangement = Arrangement.SpaceBetween,
                                      verticalAlignment = Alignment.CenterVertically
                                  ) {
                                      Column(horizontalAlignment = Alignment.Start) {
                                          Text(
                                              text = "START DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold
                                          )
                                          Text(
                                              text = startDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold
                                          )
                                      }
                                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                          Text(
                                              text = "END DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold
                                          )
                                          Text(
                                              text = endDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold
                                          )
                                      }
                                      Column(horizontalAlignment = Alignment.End) {
                                          Text(
                                              text = "REMAINING",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold
                                          )
                                          Text(
                                              text = if (isLocked) "$remainingDays Days" else "Expired",
                                              fontSize = 12.sp,
                                              color = if (isLocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Black
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

      Spacer(modifier = Modifier.height(4.dp))
      Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
      ) {
          TextButton(
              onClick = onBatteryGuidanceClick,
              colors = ButtonDefaults.textButtonColors(
                  contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
              ),
              modifier = Modifier.testTag("battery_guidance_button")
          ) {
              Text(
                  text = "Battery settings guidance",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
              )
          }
      }
  }

@Composable
fun AnalyticsCard(
    analytics: Analytics?,
    dailyAnalytics: DailyAnalytics,
    weeklyTrends: List<TrendPoint>,
    advancedAnalytics: AdvancedAnalytics,
    allSessions: List<FocusSession>
) {
    var selectedTab by remember { mutableStateOf("Overview") }
    val appLaunches = analytics?.blockedAppLaunches ?: 0

    Card(
        modifier = Modifier.fillMaxWidth().testTag("analytics_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focuss Buddy Insights",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Score: ${advancedAnalytics.productivityScore}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Tab Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf("Overview", "Daily", "Weekly", "Monthly", "History").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "analytics_tabs"
            ) { tab ->
                when (tab) {
                    "Overview" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Circular Productivity Score Gauge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(70.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { advancedAnalytics.productivityScore / 100f },
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 6.dp,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = "${advancedAnalytics.productivityScore}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Productivity Score",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Calculated from completed sessions, total focused time, and early exit rates.",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            // Streak Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = "Streak System",
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "Streak Tracker",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${advancedAnalytics.currentStreak}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp,
                                                color = Color(0xFFFF9800)
                                            )
                                            Text(
                                                text = "Current Streak",
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${advancedAnalytics.longestStreak}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp,
                                                color = Color(0xFF4CAF50)
                                            )
                                            Text(
                                                text = "Longest Streak",
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${advancedAnalytics.totalFocusDays}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Total Days",
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Daily" -> {
                        val formattedTime = remember(advancedAnalytics.todayFocusTimeSeconds) {
                            val h = advancedAnalytics.todayFocusTimeSeconds / 3600
                            val m = (advancedAnalytics.todayFocusTimeSeconds % 3600) / 60
                            val s = advancedAnalytics.todayFocusTimeSeconds % 60
                            when {
                                h > 0 -> "${h}h ${m}m"
                                m > 0 -> "${m}m ${s}s"
                                else -> "${s}s"
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Today's Dashboard",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = "Today Focus Time",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = formattedTime,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Today's Focus Time",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Completed",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "${advancedAnalytics.todaySessionsCompleted}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Completed Sessions",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = "Ended Early",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "${advancedAnalytics.todaySessionsEndedEarly}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Ended Early",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "Weekly" -> {
                        val weeklyFocusHoursFormatted = remember(advancedAnalytics.weeklyFocusHours) {
                            String.format(java.util.Locale.US, "%.1fh", advancedAnalytics.weeklyFocusHours)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Weekly Hours",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = weeklyFocusHoursFormatted,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Weekly Focus Hours",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Weekly Sessions",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${advancedAnalytics.weeklySessionsCount}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Weekly Sessions",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Success Rate",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${advancedAnalytics.weeklySuccessRate}%",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Success Rate",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text(
                                text = "Weekly Focus Trends",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )

                            var selectedTrendTab by remember { mutableStateOf("Success Rate") }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Success Rate", "Focus Time").forEach { tabOption ->
                                    val isSelected = selectedTrendTab == tabOption
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { selectedTrendTab = tabOption }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tabOption,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            TrendChart(trends = weeklyTrends, chartType = selectedTrendTab)
                        }
                    }

                    "Monthly" -> {
                        val monthlyFocusHoursFormatted = remember(advancedAnalytics.monthlyFocusHours) {
                            String.format(java.util.Locale.US, "%.1fh", advancedAnalytics.monthlyFocusHours)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = monthlyFocusHoursFormatted,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Monthly Hours",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${advancedAnalytics.longestSessionMinutes}m",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "Longest Session",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${advancedAnalytics.longestStreak}d",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = Color(0xFFFF9800)
                                    )
                                    Text(
                                        text = "Longest Streak",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text(
                                text = "Focus Calendar (Last 4 Weeks)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val shortWeekdays = listOf("M", "T", "W", "T", "F", "S", "S")
                                    shortWeekdays.forEach { dayLabel ->
                                        Text(
                                            text = dayLabel,
                                            modifier = Modifier.width(36.dp),
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                val chunkedDays = advancedAnalytics.calendarDays.chunked(7)
                                chunkedDays.forEach { rowDays ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        rowDays.forEach { day ->
                                            val bgColor = when {
                                                day.isProductive -> Color(0xFF4CAF50)
                                                day.isMissed -> Color(0xFFFF5722)
                                                else -> Color.White.copy(alpha = 0.05f)
                                            }
                                            
                                            val borderStroke = if (day.isToday) {
                                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                            } else null

                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(bgColor)
                                                    .clickable { }
                                                    .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(8.dp)) else Modifier),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${day.dayOfMonth}",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (day.isProductive || day.isMissed) Color.White else Color.White.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
                                    Text("Productive", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF5722), RoundedCornerShape(2.dp)))
                                    Text("Missed/Early", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(10.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(2.dp)))
                                    Text("No Session", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }

                    "History" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Focus Sessions Log",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )

                            WeeklyInsightsBarChart(trends = weeklyTrends)

                            if (allSessions.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No focus history available",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault()) }
                                    
                                    allSessions.sortedByDescending { it.startTime }.forEach { session ->
                                        val formattedDate = remember(session.startTime) {
                                            dateFormat.format(Date(session.startTime))
                                        }

                                        val actualMinutes = session.actualDurationSeconds / 60
                                        val actualSeconds = session.actualDurationSeconds % 60
                                        val actualText = if (actualMinutes > 0) "${actualMinutes}m ${actualSeconds}s" else "${actualSeconds}s"

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = formattedDate,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Planned: ${session.durationMinutes}m",
                                                            fontSize = 10.sp,
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                        Text(
                                                            text = "Actual: $actualText",
                                                            fontSize = 10.sp,
                                                            color = Color.White.copy(alpha = 0.8f),
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                Color.White.copy(alpha = 0.08f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (session.isStrict) "Strict" else "Normal",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (session.isStrict) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }

                                                    val statusColor = when (session.sessionStatus) {
                                                        "Completed" -> Color(0xFF4CAF50)
                                                        "Expired" -> Color(0xFF4CAF50)
                                                        else -> MaterialTheme.colorScheme.error
                                                    }

                                                    Text(
                                                        text = session.sessionStatus,
                                                        color = statusColor,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        softWrap = false
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

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Extra Shielded/Blocked App Launches Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shielded App Launches",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "$appLaunches times",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TrendChart(
    trends: List<TrendPoint>,
    chartType: String
) {
    if (trends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No focus data recorded yet",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }
        return
    }

    val maxVal = when (chartType) {
        "Success Rate" -> 100f
        else -> {
            val maxMin = trends.maxOfOrNull { it.actualFocusMinutes } ?: 0f
            if (maxMin < 15f) 15f else (maxMin * 1.2f)
        }
    }

    val primaryColor = if (chartType == "Success Rate") Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val width = size.width
            val height = size.height
            
            // Margins for axes labels
            val leftMargin = 70f
            val rightMargin = 30f
            val topMargin = 30f
            val bottomMargin = 50f
            
            val chartWidth = width - leftMargin - rightMargin
            val chartHeight = height - topMargin - bottomMargin
            
            // Draw Gridlines (Horizontal)
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = topMargin + chartHeight * (i.toFloat() / gridLines)
                // Grid line
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(leftMargin, y),
                    end = androidx.compose.ui.geometry.Offset(width - rightMargin, y),
                    strokeWidth = 2f
                )
                // Value text
                val gridVal = maxVal * (gridLines - i) / gridLines
                val labelText = if (chartType == "Success Rate") "${gridVal.toInt()}%" else "${gridVal.toInt()}m"
                
                // Draw text using native canvas
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 100
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    drawText(labelText, leftMargin - 15f, y + 8f, paint)
                }
            }
            
            val stepX = chartWidth / (trends.size - 1).coerceAtLeast(1)
            
            if (chartType == "Success Rate") {
                // LINE CHART with Gradient Fill
                val points = trends.mapIndexed { idx, pt ->
                    val x = leftMargin + idx * stepX
                    val y = topMargin + chartHeight * (1f - (pt.successRate / maxVal))
                    androidx.compose.ui.geometry.Offset(x, y)
                }
                
                // 1. Draw area gradient path under the line
                val fillPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(leftMargin, topMargin + chartHeight)
                    points.forEach { pt ->
                        lineTo(pt.x, pt.y)
                    }
                    lineTo(leftMargin + (trends.size - 1) * stepX, topMargin + chartHeight)
                    close()
                }
                
                drawPath(
                    path = fillPath,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = topMargin,
                        endY = topMargin + chartHeight
                    )
                )
                
                // 2. Draw line segments
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = primaryColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 6f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                
                // 3. Draw dot highlights on points
                points.forEach { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 8f,
                        center = pt
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 5f,
                        center = pt
                    )
                }
            } else {
                // BAR CHART with Rounded Corners
                val barWidth = (stepX * 0.5f).coerceIn(12f, 40f)
                trends.forEachIndexed { idx, pt ->
                    val x = leftMargin + idx * stepX
                    val barHeight = chartHeight * (pt.actualFocusMinutes / maxVal)
                    val top = topMargin + chartHeight - barHeight
                    val bottom = topMargin + chartHeight
                    
                    val rect = androidx.compose.ui.geometry.Rect(
                        left = x - barWidth / 2,
                        top = top,
                        right = x + barWidth / 2,
                        bottom = bottom
                    )
                    
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                rect = rect,
                                topLeft = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                topRight = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                bottomLeft = androidx.compose.ui.geometry.CornerRadius(0f, 0f),
                                bottomRight = androidx.compose.ui.geometry.CornerRadius(0f, 0f)
                            )
                        )
                    }
                    
                    drawPath(
                        path = path,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.6f)),
                            startY = top,
                            endY = bottom
                        )
                    )
                }
            }
            
            // Draw X-Axis Date Labels
            trends.forEachIndexed { idx, pt ->
                val x = leftMargin + idx * stepX
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 150
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(pt.dateLabel, x, height - 15f, paint)
                }
            }
        }
    }
}

@Composable
fun QuotaNumberField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AddLongTermBlockDialog(
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (type: String, target: String, label: String, reason: String, startDate: Long, endDate: Long, dailyLimitSeconds: Long?) -> Unit
) {
    val context = LocalContext.current
    var blockType by remember { mutableStateOf("APP") } // "APP" or "WEBSITE"
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var websiteUrl by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    var quotaEnabled by remember { mutableStateOf(false) }
    var quotaHours by remember { mutableStateOf(0) }
    var quotaMinutes by remember { mutableStateOf(30) }
    var quotaSeconds by remember { mutableStateOf(0) }

    var startDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) } // default 1 week

    var appSearchQuery by remember { mutableStateOf("") }
    var isAppDropdownExpanded by remember { mutableStateOf(false) }

    val filteredApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter { it.appName.contains(appSearchQuery, ignoreCase = true) }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
    val startDateStr = remember(startDateMillis) { dateFormatter.format(Date(startDateMillis)) }
    val endDateStr = remember(endDateMillis) { dateFormatter.format(Date(endDateMillis)) }

    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add Long-Term Block",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                // Type Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { blockType = "APP" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (blockType == "APP") MaterialTheme.colorScheme.primary else Color(0x15FFFFFF),
                            contentColor = if (blockType == "APP") MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AppBlocking, contentDescription = "App Icon", modifier = Modifier.size(16.dp))
                            Text("Block App", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { blockType = "WEBSITE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (blockType == "WEBSITE") MaterialTheme.colorScheme.primary else Color(0x15FFFFFF),
                            contentColor = if (blockType == "WEBSITE") MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = "Website Icon", modifier = Modifier.size(16.dp))
                            Text("Block Website", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (blockType == "APP") {
                    // Search & Select App
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Select Installed App", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                        
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = {
                                appSearchQuery = it
                                isAppDropdownExpanded = true
                            },
                            placeholder = { Text("Search and tap to select...", color = Color.White.copy(alpha = 0.4f)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (selectedApp != null) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            }
                        )

                        if (isAppDropdownExpanded) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredApps) { app ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedApp = app
                                                    appSearchQuery = app.appName
                                                    isAppDropdownExpanded = false
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(app.appName, color = Color.White, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Website URL Input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Website Domain Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                        OutlinedTextField(
                            value = websiteUrl,
                            onValueChange = { websiteUrl = it },
                            placeholder = { Text("e.g. facebook.com", color = Color.White.copy(alpha = 0.4f)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Reason input
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Reason for Blocking", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        placeholder = { Text("e.g. JEE Advanced Preparation", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (blockType == "APP") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x10FFFFFF), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Daily time limit instead of full block",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Allow this app for a set amount of time each day, then block it until the next day.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                            Switch(checked = quotaEnabled, onCheckedChange = { quotaEnabled = it })
                        }

                        if (quotaEnabled) {
                            Text(
                                "Allowed per day",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuotaNumberField(
                                    label = "Hours",
                                    value = quotaHours,
                                    range = 0..23,
                                    onValueChange = { quotaHours = it },
                                    modifier = Modifier.weight(1f)
                                )
                                QuotaNumberField(
                                    label = "Minutes",
                                    value = quotaMinutes,
                                    range = 0..59,
                                    onValueChange = { quotaMinutes = it },
                                    modifier = Modifier.weight(1f)
                                )
                                QuotaNumberField(
                                    label = "Seconds",
                                    value = quotaSeconds,
                                    range = 0..59,
                                    onValueChange = { quotaSeconds = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Date Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Start Date", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    calendar.timeInMillis = startDateMillis
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val c = Calendar.getInstance()
                                            c.set(year, month, day, 0, 0, 0)
                                            startDateMillis = c.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x10FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Calendar Icon", modifier = Modifier.size(16.dp))
                                Text(startDateStr, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("End Date", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    calendar.timeInMillis = endDateMillis
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val c = Calendar.getInstance()
                                            c.set(year, month, day, 23, 59, 59)
                                            endDateMillis = c.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x10FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Calendar Icon", modifier = Modifier.size(16.dp))
                                Text(endDateStr, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Error State Display
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Validations
                            if (blockType == "APP" && selectedApp == null) {
                                validationError = "Please select an installed app."
                                return@Button
                            }
                            if (blockType == "WEBSITE" && websiteUrl.isBlank()) {
                                validationError = "Please enter a website domain."
                                return@Button
                            }
                            if (endDateMillis <= startDateMillis) {
                                validationError = "End Date must be after Start Date."
                                return@Button
                            }

                            validationError = null
                            val finalTarget = if (blockType == "APP") selectedApp!!.packageName else websiteUrl
                            val finalLabel = if (blockType == "APP") selectedApp!!.appName else websiteUrl
                            val dailyLimitSeconds = if (quotaEnabled) {
                                (quotaHours * 3600L + quotaMinutes * 60L + quotaSeconds).coerceAtLeast(1L)
                            } else null
                            onConfirm(blockType, finalTarget, finalLabel, reason, startDateMillis, endDateMillis, dailyLimitSeconds)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Block Now")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToPreSessionRitual: (Int, Boolean, Long?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMinutes by remember { mutableStateOf(15) }
    var daysInput by remember { mutableStateOf("0") }
    var hoursInput by remember { mutableStateOf("0") }
    var minutesInput by remember { mutableStateOf("15") }
    var secondsInput by remember { mutableStateOf("0") }
    var isCustomSelected by remember { mutableStateOf(false) }
    var isStrict by remember { mutableStateOf(false) }

    val presetDurations = listOf(
        15 to "15 Min",
        30 to "30 Min",
        60 to "1 Hour"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Focus", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Choose Session Duration",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            // Grid of Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presetDurations.forEach { (minutes, label) ->
                    val isSelected = !isCustomSelected && selectedMinutes == minutes
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable {
                                isCustomSelected = false
                                selectedMinutes = minutes
                                daysInput = "0"
                                hoursInput = "0"
                                minutesInput = "$minutes"
                                secondsInput = "0"
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Custom Duration Option
            val parsedDays = remember(daysInput) { daysInput.toIntOrNull() ?: 0 }
            val parsedHours = remember(hoursInput) { hoursInput.toIntOrNull() ?: 0 }
            val parsedMinutesState = remember(minutesInput) { minutesInput.toIntOrNull() ?: 0 }
            val parsedSeconds = remember(secondsInput) { secondsInput.toIntOrNull() ?: 0 }

            val isDaysValid = remember(parsedDays) { parsedDays in 0..365 }
            val isHoursValid = remember(parsedHours) { parsedHours in 0..23 }
            val isMinutesValid = remember(parsedMinutesState) { parsedMinutesState in 0..59 }
            val isSecondsValid = remember(parsedSeconds) { parsedSeconds in 0..59 }

            val totalSecondsComputed = remember(parsedDays, parsedHours, parsedMinutesState, parsedSeconds) {
                parsedDays.toLong() * 24 * 3600 + parsedHours.toLong() * 3600 + parsedMinutesState.toLong() * 60 + parsedSeconds.toLong()
            }
            val isInputValid = remember(isDaysValid, isHoursValid, isMinutesValid, isSecondsValid, totalSecondsComputed) {
                isDaysValid && isHoursValid && isMinutesValid && isSecondsValid && totalSecondsComputed > 0
            }

            if (isCustomSelected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Custom duration details",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Set Precise Duration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Days input
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = daysInput,
                                    onValueChange = {
                                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 3)) {
                                            daysInput = it
                                        }
                                    },
                                    label = { Text("Days", fontSize = 10.sp) },
                                    isError = !isDaysValid,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        errorBorderColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    singleLine = true
                                )
                            }

                            // Hours input
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = hoursInput,
                                    onValueChange = {
                                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 2)) {
                                            hoursInput = it
                                        }
                                    },
                                    label = { Text("Hrs", fontSize = 10.sp) },
                                    isError = !isHoursValid,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        errorBorderColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    singleLine = true
                                )
                            }

                            // Minutes input
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = minutesInput,
                                    onValueChange = {
                                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 2)) {
                                            minutesInput = it
                                        }
                                    },
                                    label = { Text("Mins", fontSize = 10.sp) },
                                    isError = !isMinutesValid,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        errorBorderColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Next,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    singleLine = true
                                )
                            }

                            // Seconds input
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = secondsInput,
                                    onValueChange = {
                                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 2)) {
                                            secondsInput = it
                                        }
                                    },
                                    label = { Text("Secs", fontSize = 10.sp) },
                                    isError = !isSecondsValid,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        errorBorderColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Done,
                                        keyboardType = KeyboardType.Number
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomSelected = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Custom duration icon"
                            )
                            Text(
                                text = "Custom Duration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "Configure",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isCustomSelected && !isInputValid) {
                val errorMsg = when {
                    !isDaysValid -> "Days must be between 0 and 365."
                    !isHoursValid -> "Hours must be between 0 and 23."
                    !isMinutesValid -> "Minutes must be between 0 and 59."
                    !isSecondsValid -> "Seconds must be between 0 and 59."
                    totalSecondsComputed <= 0L -> "Please enter a total duration greater than 0 seconds."
                    else -> "Please enter valid duration values."
                }
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Focus Mode Selector Section (Normal vs Strict)
            Text(
                text = "Choose Focus Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Normal Mode Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isStrict = false },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isStrict) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (!isStrict) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Normal focus icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Normal Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Can exit anytime",
                            fontSize = 11.sp,
                            color = (if (!isStrict) MaterialTheme.colorScheme.onPrimary else Color.White).copy(alpha = 0.6f)
                        )
                    }
                }

                // Strict Mode Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isStrict = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isStrict) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isStrict) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Strict focus icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Strict Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "No early exit",
                            fontSize = 11.sp,
                            color = (if (isStrict) MaterialTheme.colorScheme.onPrimary else Color.White).copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isStrict) {
                val devicePolicyManager = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager }
                val adminComponent = remember { android.content.ComponentName(context, com.example.receiver.MyDeviceAdminReceiver::class.java) }
                var isAdminActive by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) }
                
                // Keep isAdminActive updated on lifecycle resume
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            isAdminActive = devicePolicyManager.isAdminActive(adminComponent)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                /*
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_admin_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(com.example.R.string.enhanced_uninstall_protection),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Use Device Admin to lock app uninstallation",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = isAdminActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                        putExtra(
                                            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                            "Enabling Device Admin blocks uninstallation of Focus Buddy."
                                        )
                                    }
                                    context.startActivity(intent)
                                } else {
                                    try {
                                        devicePolicyManager.removeActiveAdmin(adminComponent)
                                        isAdminActive = false
                                    } catch (e: Exception) {
                                        // Handle gracefully
                                    }
                                }
                            },
                            modifier = Modifier.testTag("device_admin_switch")
                        )
                    }
                }
                */
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large visualization of selected timer
            val previewText = remember(isCustomSelected, selectedMinutes, parsedDays, parsedHours, parsedMinutesState, parsedSeconds, isInputValid) {
                if (!isCustomSelected) {
                    if (selectedMinutes >= 60) "${selectedMinutes / 60} Hour" else "$selectedMinutes Min"
                } else if (!isInputValid) {
                    "—"
                } else {
                    val parts = mutableListOf<String>()
                    if (parsedDays > 0) parts.add("${parsedDays}d")
                    if (parsedHours > 0) parts.add("${parsedHours}h")
                    if (parsedMinutesState > 0) parts.add("${parsedMinutesState}m")
                    if (parsedSeconds > 0) parts.add("${parsedSeconds}s")
                    parts.joinToString(" ")
                }
            }

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = previewText,
                        fontSize = if (previewText.length > 8) 20.sp else 28.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isCustomSelected && !isInputValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "DURATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card(
                onClick = onNavigateToSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("set_recurring_schedule_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set a Recurring Schedule",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Schedule automated strict focus sessions",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Button(
                onClick = {
                    val minutes: Int
                    val totalMs: Long?
                    if (isCustomSelected) {
                        val computedMs = (parsedDays.toLong() * 24 * 3600 + parsedHours.toLong() * 3600 + parsedMinutesState.toLong() * 60 + parsedSeconds.toLong()) * 1000L
                        minutes = (computedMs / 60000L).toInt().coerceAtLeast(1)
                        totalMs = computedMs
                    } else {
                        minutes = selectedMinutes
                        totalMs = null
                    }
                    onNavigateToPreSessionRitual(minutes, isStrict, totalMs)
                },
                enabled = !isCustomSelected || isInputValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_timer_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Start Session Now",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appList by viewModel.appListState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()

    var isStrictSessionActive by remember { mutableStateOf(false) }
    LaunchedEffect(activeSession) {
        while (true) {
            val session = activeSession
            isStrictSessionActive = session?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
            kotlinx.coroutines.delay(1000L)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(appList, searchQuery) {
        if (searchQuery.isBlank()) {
            appList
        } else {
            appList.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Block Distractions", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isStrictSessionActive) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Strict Mode Locked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Strict Mode Active - Block List is Locked",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...", color = Color.White.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )

            Text(
                text = "Toggle the apps you want to prevent yourself from opening during your deep focus sessions. Your choices are automatically stored.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                lineHeight = 16.sp
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "No apps found",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Apps Found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredList, key = { it.packageName }) { app ->
                        AppListItemRow(
                            app = app,
                            isReadOnly = isStrictSessionActive,
                            onToggle = { viewModel.toggleAppBlocked(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItemRow(
    app: AppInfo,
    isReadOnly: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReadOnly) { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReadOnly) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Placeholder beautiful color avatar instead of simple icon, matching modern design
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = if (isReadOnly) 0.15f else 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isReadOnly) 0.15f else 0.3f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isReadOnly) Color.White.copy(alpha = 0.5f) else Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isReadOnly) Color.White.copy(alpha = 0.5f) else Color.White
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        color = if (isReadOnly) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.4f),
                        maxLines = 1
                    )
                }
            }

            Switch(
                checked = app.isBlocked,
                onCheckedChange = { if (!isReadOnly) onToggle() },
                enabled = !isReadOnly,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDetailsScreen(
    viewModel: FocusViewModel,
    blockId: Int,
    blockType: String, // "APP" or "WEBSITE"
    onNavigateBack: () -> Unit
) {
    val now = System.currentTimeMillis()

    // Retrieve the block based on ID and Type
    val appBlocks by viewModel.allLongTermBlocks.collectAsStateWithLifecycle()
    val websiteBlocks by viewModel.allWebsiteBlocks.collectAsStateWithLifecycle()

    val blockInfo = remember(blockId, blockType, appBlocks, websiteBlocks) {
        if (blockType == "APP") {
            appBlocks.firstOrNull { it.id == blockId }?.let {
                BlockDetailData(
                    name = it.targetLabel,
                    reason = it.reason,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    type = "APP",
                    id = it.id
                )
            }
        } else {
            websiteBlocks.firstOrNull { it.id == blockId }?.let {
                BlockDetailData(
                    name = it.domain,
                    reason = it.reason,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    type = "WEBSITE",
                    id = it.id
                )
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Block Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (blockInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Block not found", color = Color.White)
            }
        } else {
            val isLocked = now < blockInfo.endDate
            val diff = blockInfo.endDate - now
            val remainingDays = if (diff <= 0) 0 else (diff / (24 * 60 * 60 * 1000L))
            val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
            val startDateStr = dateFormatter.format(Date(blockInfo.startDate))
            val endDateStr = dateFormatter.format(Date(blockInfo.endDate))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon & Header
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (blockInfo.type == "WEBSITE") Color(0x1FFFA630) else Color(0x1F3DFFC4)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (blockInfo.type == "WEBSITE") Icons.Default.Language else Icons.Default.AppBlocking,
                        contentDescription = "Block type",
                        tint = if (blockInfo.type == "WEBSITE") Color(0xFFFFA630) else Color(0xFF3DFFC4),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = blockInfo.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (blockInfo.type == "WEBSITE") "WEBSITE BLOCK" else "APP BLOCK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = (if (blockInfo.type == "WEBSITE") Color(0xFFFFA630) else Color(0xFF3DFFC4)),
                    letterSpacing = 1.5.sp
                )

                // Status Badge and Locked Message (if applicable)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else Color(0x15FFFFFF)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                  imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.CheckCircle,
                                  contentDescription = "Status Icon",
                                  tint = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                            )
                            Text(
                                  text = if (isLocked) "Status: LOCKED" else "Status: EXPIRED",
                                  fontWeight = FontWeight.Bold,
                                  fontSize = 16.sp,
                                  color = if (isLocked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                            )
                        }

                        if (isLocked) {
                            Text(
                                text = "This block is active and cannot be modified until it expires.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Block Configuration details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val labelName = if (blockInfo.type == "WEBSITE") "WEBSITE NAME" else "APP NAME"
                        DetailRow(label = labelName, value = blockInfo.name)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        DetailRow(label = "REASON", value = blockInfo.reason.ifEmpty { "None specified" })
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        DetailRow(label = "START DATE", value = startDateStr)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        DetailRow(label = "END DATE", value = endDateStr)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        DetailRow(label = "REMAINING DAYS", value = if (isLocked) "$remainingDays Days" else "0 Days (Expired)")
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        DetailRow(label = "STATUS", value = if (isLocked) "LOCKED" else "EXPIRED")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action Button (Delete only allowed when EXPIRED)
                Button(
                    onClick = {
                        if (!isLocked) {
                            if (blockInfo.type == "APP") {
                                viewModel.removeLongTermBlock(blockInfo.id)
                            } else {
                                viewModel.removeWebsiteBlock(blockInfo.id)
                            }
                            onNavigateBack()
                        }
                    },
                    enabled = !isLocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0x1F26A69A).copy(alpha = 0.1f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        Text("Delete Block", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

data class BlockDetailData(
    val name: String,
    val reason: String,
    val startDate: Long,
    val endDate: Long,
    val type: String,
    val id: Int
)

@Composable
fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
fun DailyDashboardCard(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val dailyAnalytics by viewModel.dailyAnalytics.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val totalTimeSeconds = dailyAnalytics.totalActualFocusTimeSeconds

    val todayCompletedSessions = remember(allSessions) {
        val cal1 = java.util.Calendar.getInstance()
        allSessions.count { session ->
            val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = session.startTime }
            cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR) &&
            session.sessionStatus == "Completed"
        }
    }

    val formattedTime = remember(totalTimeSeconds) {
        val h = totalTimeSeconds / 3600
        val m = (totalTimeSeconds % 3600) / 60
        val s = totalTimeSeconds % 60
        when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Daily Dashboard icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Daily Dashboard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                // Reset Action
                IconButton(
                    onClick = { viewModel.resetDailyCounters() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset offsets",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "View your progress counters for today's focus sessions.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 16.sp
            )

            // Rows of Interactive Counter Controls
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Counter 1: Total Focus Time
                CounterRow(
                    title = "Daily Focus Time",
                    valueText = formattedTime,
                    icon = Icons.Default.Timer,
                    iconColor = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                // Counter 2: Completed Sessions
                CounterRow(
                    title = "Completed Sessions",
                    valueText = "$todayCompletedSessions",
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF4CAF50)
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                // Counter 3: Early Exits
                CounterRow(
                    title = "Early Exits",
                    valueText = "${dailyAnalytics.endedEarlySessions}",
                    icon = Icons.Default.Cancel,
                    iconColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun CounterRow(
    title: String,
    valueText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color.White
            )
        }

        Text(
            text = valueText,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = iconColor
        )
    }
}

@Composable
fun ContentLevelBlockingCard(viewModel: FocusViewModel) {
    val youtubeEnabled by viewModel.youtubeBlockShorts.collectAsStateWithLifecycle()
    val instagramEnabled by viewModel.instagramBlockReels.collectAsStateWithLifecycle()
    val snapchatEnabled by viewModel.snapchatBlockSpotlight.collectAsStateWithLifecycle()
    val isStrictActive by viewModel.isStrictModeActive.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Content Shield Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Strict Mode Content Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Text(
                text = "These content filters will only block Shorts, Reels, and Spotlight when an active Focus Session is in Strict Mode.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // YouTube Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isStrictActive) { viewModel.setYoutubeBlockShorts(!youtubeEnabled) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Block YouTube Shorts",
                            fontWeight = FontWeight.Bold,
                            color = if (isStrictActive) Color.White.copy(alpha = 0.6f) else Color.White,
                            fontSize = 15.sp
                        )
                        if (isStrictActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isStrictActive) "This setting is locked while Strict Mode is active." else "Allow normal videos, subscriptions, and search",
                        color = if (isStrictActive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Checkbox(
                    checked = youtubeEnabled,
                    enabled = !isStrictActive,
                    onCheckedChange = { viewModel.setYoutubeBlockShorts(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White.copy(alpha = 0.4f)
                    )
                )
            }

            // Instagram Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isStrictActive) { viewModel.setInstagramBlockReels(!instagramEnabled) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Block Instagram Reels",
                            fontWeight = FontWeight.Bold,
                            color = if (isStrictActive) Color.White.copy(alpha = 0.6f) else Color.White,
                            fontSize = 15.sp
                        )
                        if (isStrictActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isStrictActive) "This setting is locked while Strict Mode is active." else "Allow feed, messages, and stories",
                        color = if (isStrictActive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Checkbox(
                    checked = instagramEnabled,
                    enabled = !isStrictActive,
                    onCheckedChange = { viewModel.setInstagramBlockReels(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White.copy(alpha = 0.4f)
                    )
                )
            }

            // Snapchat Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isStrictActive) { viewModel.setSnapchatBlockSpotlight(!snapchatEnabled) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Block Snapchat Spotlight",
                            fontWeight = FontWeight.Bold,
                            color = if (isStrictActive) Color.White.copy(alpha = 0.6f) else Color.White,
                            fontSize = 15.sp
                        )
                        if (isStrictActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isStrictActive) "This setting is locked while Strict Mode is active." else "Allow chat and friends",
                        color = if (isStrictActive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Checkbox(
                    checked = snapchatEnabled,
                    enabled = !isStrictActive,
                    onCheckedChange = { viewModel.setSnapchatBlockSpotlight(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
fun AccentThemeCard(viewModel: FocusViewModel) {
    val currentTheme by viewModel.accentTheme.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier.fillMaxWidth().testTag("accent_theme_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Palette Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "App Theme Accent",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Text(
                text = "Choose your custom primary accent color to personalize Focuss Buddy's deep dark interface.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            val isNightModeActive = com.example.ui.helper.SmartNightModeManager.isNightModeActive.collectAsState().value
            if (isNightModeActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("smart_night_mode_active_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🌙",
                            fontSize = 20.sp
                        )
                        Column {
                            Text(
                                text = "Smart Night Mode Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Active between 9:00 PM and 6:00 AM. AMOLED Pitch Black palette is enabled for low-glare deep focus.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Scrollable row of themes
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AccentTheme.values()) { theme ->
                    val isSelected = theme.displayName == currentTheme
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { viewModel.setAccentTheme(theme.displayName) }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(theme.primary)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = theme.displayName,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPkg by com.example.service.FocusAccessibilityService.currentPackage.collectAsStateWithLifecycle()
    val currentAct by com.example.service.FocusAccessibilityService.currentActivity.collectAsStateWithLifecycle()
    val textOnScreen by com.example.service.FocusAccessibilityService.visibleText.collectAsStateWithLifecycle()
    val lastEvt by com.example.service.FocusAccessibilityService.lastEvent.collectAsStateWithLifecycle()
    val shortsStatus by com.example.service.FocusAccessibilityService.shortsDetectionStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility Debugger", fontWeight = FontWeight.Bold, color = Color.White) },
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
                    containerColor = MaterialTheme.colorScheme.background
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Live Accessibility Diagnostics",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DebugRow(label = "Current Package", value = currentPkg)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    DebugRow(label = "Current Activity", value = currentAct)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    DebugRow(label = "Shorts Status", value = shortsStatus, isError = shortsStatus.contains("Detected"))
                }
            }

            // Last Event Card
            Text(
                text = "Last Accessibility Event",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = lastEvt,
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Visible Text Card
            Text(
                text = "Visible Screen Text Detected",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = textOnScreen.ifEmpty { "No visible text detected on screen." },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DebugRow(label: String, value: String, isError: Boolean = false) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = if (isError) MaterialTheme.colorScheme.error else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WallpaperSettingsDrawerCard(
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    hasWallpaper: Boolean,
    onShowBrightnessTray: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallpaper_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Wallpaper Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Wallpaper Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Text(
                text = "Set a custom image as your focus workspace background to personalize your deep focus environment.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            if (hasWallpaper) {
                Button(
                    onClick = onShowBrightnessTray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("brightness_shortcut_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Adjust Brightness",
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Adjust Brightness", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickWallpaper,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .testTag("set_wallpaper_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Change", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onClearWallpaper,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("remove_wallpaper_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Remove", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                Button(
                    onClick = onPickWallpaper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("set_wallpaper_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Choose Wallpaper", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun WallpaperSettingsCard(
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    opacityValue: Float,
    onOpacityChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
    }
    val hasWallpaper = remember {
        !sharedPrefs.getString("custom_wallpaper_uri", null).isNullOrEmpty()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallpaper_settings_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Wallpaper Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Wallpaper Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Text(
                text = "Set a custom image as your focus workspace background to personalize your deep focus environment. A subtle dark overlay is applied automatically to maintain Material 3 contrast and high readability.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            if (hasWallpaper) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wallpaper Brightness",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(opacityValue * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = opacityValue,
                        onValueChange = onOpacityChange,
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallpaper_opacity_slider")
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onPickWallpaper,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("set_wallpaper_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Change Wallpaper", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onClearWallpaper,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("remove_wallpaper_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Remove Wallpaper", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onPickWallpaper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("set_wallpaper_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Choose Wallpaper", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun generateDisciplineParagraph(): String {
    val wordPool = listOf(
        "discipline", "focus", "attention", "strength", "commitment", "mindfulness", "patience", 
        "clarity", "purpose", "resolve", "intention", "resilience", "growth", "mastery", "practice", 
        "effort", "determination", "success", "habit", "conquest", "willpower", "victory", "calm", 
        "progress", "wisdom", "learning", "action", "achievement", "pursuit", "dedication", "control"
    )
    val sentences = listOf(
        "True strength lies in the quiet persistence of our daily choices to remain focused.",
        "We build our future through the deliberate practice of resisting instant gratification.",
        "Maintaining absolute focus on our primary objectives is the key to deep mastery.",
        "Every moment spent in distraction is a moment stolen from our potential.",
        "Self-discipline is not restriction, but the ultimate expression of personal freedom.",
        "By channeling our attention inward, we cultivate a powerful state of mental clarity.",
        "We must learn to embrace the discomfort of difficult tasks to unlock genuine growth.",
        "An organized mind is capable of extraordinary achievements when shielded from chaos.",
        "Consistency is the foundation upon which all great and lasting endeavors are built.",
        "I am fully committed to my goals, guarding my focus against passing temptations."
    )
    
    val resultWords = mutableListOf<String>()
    val rand = kotlin.random.Random(System.currentTimeMillis())
    
    // First, let's add some sentences
    while (resultWords.size < 280) {
        val sentence = sentences[rand.nextInt(sentences.size)]
        resultWords.addAll(sentence.split(" "))
    }
    
    // Pad or trim to exactly 300 words
    if (resultWords.size > 300) {
        return resultWords.take(300).joinToString(" ")
    } else {
        while (resultWords.size < 300) {
            val word = wordPool[rand.nextInt(wordPool.size)]
            resultWords.add(word)
        }
        return resultWords.joinToString(" ")
    }
}

@Composable
fun EmergencyUnlockScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val targetText = remember { generateDisciplineParagraph() }
    var typedText by remember { mutableStateOf("") }
    
    val targetWords = remember(targetText) { targetText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() } }
    val typedWords = remember(typedText) { typedText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() } }
    
    // Calculate matched words (order-sensitive, character-perfect)
    val matchedWordsCount = remember(targetWords, typedWords) {
        var count = 0
        for (i in 0 until minOf(targetWords.size, typedWords.size)) {
            if (targetWords[i] == typedWords[i]) {
                count++
            }
        }
        count
    }
    
    val isPerfectMatch = remember(typedText, targetText) {
        typedText.trim() == targetText.trim()
    }
    
    val disabledClipboardManager = remember {
        object : androidx.compose.ui.platform.ClipboardManager {
            override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) {}
            override fun getText(): androidx.compose.ui.text.AnnotatedString? = null
        }
    }

    val disabledTextToolbar = remember {
        object : androidx.compose.ui.platform.TextToolbar {
            override val status: androidx.compose.ui.platform.TextToolbarStatus = androidx.compose.ui.platform.TextToolbarStatus.Hidden
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {}
            override fun hide() {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "EMERGENCY UNLOCK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning Icon",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "STRICT DISCIPLINE CHALLENGE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "To bypass strict mode, you must perfectly type the 300-word paragraph below. Copy-pasting, clipboard features, and selection utilities are completely disabled on the input field.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Target paragraph card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REQUIRED TEXT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "300 Words",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = targetText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
            }

            // Real-time progress row
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TYPED WORDS",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${typedWords.size} / 300",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (typedWords.size == 300) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.1f)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PERFECTLY MATCHED",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$matchedWordsCount",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (matchedWordsCount == 300) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Input TextField with Copy/Paste/Selection menu disabled
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalClipboardManager provides disabledClipboardManager,
                androidx.compose.ui.platform.LocalTextToolbar provides disabledTextToolbar
            ) {
                OutlinedTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("emergency_unlock_input"),
                    placeholder = { Text("Begin typing the paragraph perfectly here...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isPerfectMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color.White
                    )
                )
            }

            // Action Button
            Button(
                onClick = {
                    if (isPerfectMatch) {
                        viewModel.deactivateStrictMode()
                        android.widget.Toast.makeText(context, "Strict Mode Prematurely Deactivated.", android.widget.Toast.LENGTH_LONG).show()
                        onBack()
                    }
                },
                enabled = isPerfectMatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_unlock_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = if (isPerfectMatch) "COMPLETE UNLOCK" else "TYPE ENTIRE PARAGRAPH PERFECTLY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}



@Composable
fun WeeklyInsightsBarChart(
    trends: List<TrendPoint>
) {
    if (trends.isEmpty()) return

    val maxVal = remember(trends) {
        val maxMin = trends.maxOfOrNull { it.actualFocusMinutes } ?: 0f
        if (maxMin < 15f) 15f else (maxMin * 1.1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("weekly_insights_bar_chart")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Past 7 Days Focus Distribution",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
                    Text("Completed", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF5722), RoundedCornerShape(2.dp)))
                    Text("Early/Missed", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val width = size.width
            val height = size.height
            
            val leftMargin = 60f
            val rightMargin = 20f
            val topMargin = 20f
            val bottomMargin = 40f
            
            val chartWidth = width - leftMargin - rightMargin
            val chartHeight = height - topMargin - bottomMargin
            
            // Draw horizontal dotted gridlines
            val gridLines = 3
            for (i in 0..gridLines) {
                val y = topMargin + chartHeight * (i.toFloat() / gridLines)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = androidx.compose.ui.geometry.Offset(leftMargin, y),
                    end = androidx.compose.ui.geometry.Offset(width - rightMargin, y),
                    strokeWidth = 2f
                )
                
                // Label Y-Axis
                val gridVal = maxVal * (gridLines - i) / gridLines
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 100
                        textSize = 20f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    drawText("${gridVal.toInt()}m", leftMargin - 10f, y + 6f, paint)
                }
            }
            
            val stepX = chartWidth / (trends.size - 1).coerceAtLeast(1)
            val barWidth = (stepX * 0.45f).coerceIn(10f, 30f)
            
            // Draw Stacked Bar Charts
            trends.forEachIndexed { idx, pt ->
                val x = leftMargin + idx * stepX
                val totalMinutes = pt.actualFocusMinutes
                
                if (totalMinutes > 0f) {
                    val successFraction = pt.successRate / 100f
                    val completedMinutes = totalMinutes * successFraction
                    val missedMinutes = totalMinutes * (1f - successFraction)
                    
                    val completedHeight = chartHeight * (completedMinutes / maxVal)
                    val missedHeight = chartHeight * (missedMinutes / maxVal)
                    
                    val bottom = topMargin + chartHeight
                    
                    // 1. Draw completed segment at the bottom
                    if (completedHeight > 0f) {
                        val completedRect = androidx.compose.ui.geometry.Rect(
                            left = x - barWidth / 2,
                            top = bottom - completedHeight,
                            right = x + barWidth / 2,
                            bottom = bottom
                        )
                        val completedPath = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect = completedRect,
                                    topLeft = androidx.compose.ui.geometry.CornerRadius(if (missedHeight > 0f) 0f else 4.dp.toPx(), if (missedHeight > 0f) 0f else 4.dp.toPx()),
                                    topRight = androidx.compose.ui.geometry.CornerRadius(if (missedHeight > 0f) 0f else 4.dp.toPx(), if (missedHeight > 0f) 0f else 4.dp.toPx()),
                                    bottomLeft = androidx.compose.ui.geometry.CornerRadius(0f, 0f),
                                    bottomRight = androidx.compose.ui.geometry.CornerRadius(0f, 0f)
                                )
                            )
                        }
                        drawPath(
                            path = completedPath,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    
                    // 2. Draw missed/early segment on top of completed
                    if (missedHeight > 0f) {
                        val missedRect = androidx.compose.ui.geometry.Rect(
                            left = x - barWidth / 2,
                            top = bottom - completedHeight - missedHeight,
                            right = x + barWidth / 2,
                            bottom = bottom - completedHeight
                        )
                        val missedPath = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect = missedRect,
                                    topLeft = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    topRight = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    bottomLeft = androidx.compose.ui.geometry.CornerRadius(0f, 0f),
                                    bottomRight = androidx.compose.ui.geometry.CornerRadius(0f, 0f)
                                )
                            )
                        }
                        drawPath(
                            path = missedPath,
                            color = Color(0xFFFF5722)
                        )
                    }
                } else {
                    // Draw a tiny placeholder circle or line for 0 focus minutes so user sees it
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = 4f,
                        center = androidx.compose.ui.geometry.Offset(x, topMargin + chartHeight)
                    )
                }
                
                // Draw X-axis label
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 150
                        textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(pt.dateLabel, x, height - 10f, paint)
                }
            }
        }
    }
}

@Composable
fun MyForestGalleryCard(
    maturedSessions: List<FocusSession>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("my_forest_gallery_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = "Forest Gallery",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "My Forest Gallery",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Your completed focus milestones",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                // Display count of total matured trees
                Box(
                    modifier = Modifier
                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("matured_forest_badge")
                ) {
                    Text(
                        text = "${maturedSessions.size} Trees",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            if (maturedSessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("empty_forest_state"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "Sprout Seed",
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Your forest is empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Complete a focus session to grow your very first tree! 🌱",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val chunked = remember(maturedSessions) { maturedSessions.chunked(3) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chunked.take(4).forEach { rowItems -> // Show up to 12 trees in dashboard preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { session ->
                                ForestTreeItem(session = session, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForestTreeItem(
    session: FocusSession,
    modifier: Modifier = Modifier
) {
    val dateFormater = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateString = remember(session.startTime) { dateFormater.format(Date(session.startTime)) }
    
    val plantStage = remember(session.plantStatus) {
        when (session.plantStatus.uppercase()) {
            "SEED" -> GrowthStage.SEED
            "GROWING" -> GrowthStage.SAPLING
            "MATURED" -> GrowthStage.MATURED
            "WITHERED" -> GrowthStage.WITHERED
            else -> GrowthStage.MATURED
        }
    }

    Card(
        modifier = modifier
            .testTag("forest_tree_item_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlantVisual(
                stage = plantStage,
                size = 56.dp,
                modifier = Modifier.padding(4.dp)
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${session.plannedDurationMinutes}m Focus",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = dateString,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun QuantumOrbitProgressRing(
    progress: Float,
    timeRemainingMs: Long,
    modifier: Modifier = Modifier
) {
    // "Precision Instrument" dial: tick marks around the circumference like a
    // lab gauge/oscilloscope, with a phosphor-glow progress arc. This is the
    // signature visual motif reused across the timer, stat gauges, and the
    // bottom navigation active-state indicator elsewhere in the theme.
    val isHyperFocus = timeRemainingMs <= 10 * 60 * 1000L && timeRemainingMs > 0
    val phosphor = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.secondary
    val glowColor = if (isHyperFocus) amber else phosphor

    val pulse by rememberInfiniteTransition(label = "dial_pulse").animateFloat(
        initialValue = if (isHyperFocus) 0.6f else 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isHyperFocus) 500 else 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dial_pulse_alpha"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = size.minDimension / 2f - 2.dp.toPx()
            val trackR = outerR - 7.dp.toPx()
            val tickOuterR = outerR
            val tickInnerMinorR = outerR - 3.dp.toPx()
            val tickInnerMajorR = outerR - 5.dp.toPx()

            // Tick marks - 60 total, every 5th one longer/brighter (major).
            for (i in 0 until 60) {
                val angle = (i / 60f) * 2f * Math.PI.toFloat() - (Math.PI / 2f).toFloat()
                val major = i % 5 == 0
                val inner = if (major) tickInnerMajorR else tickInnerMinorR
                val start = Offset(cx + tickOuterR * cos(angle), cy + tickOuterR * sin(angle))
                val end = Offset(cx + inner * cos(angle), cy + inner * sin(angle))
                drawLine(
                    color = if (major) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                    start = start,
                    end = end,
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Background track for the progress arc.
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                center = Offset(cx, cy),
                radius = trackR,
                style = Stroke(width = 3.dp.toPx())
            )

            // Phosphor/amber glow arc - soft wide layer + sharp core layer.
            drawArc(
                color = glowColor.copy(alpha = 0.30f * pulse),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(cx - trackR, cy - trackR),
                size = androidx.compose.ui.geometry.Size(trackR * 2, trackR * 2),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = glowColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(cx - trackR, cy - trackR),
                size = androidx.compose.ui.geometry.Size(trackR * 2, trackR * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Bright "needle tip" dot at the current progress position.
            val tipAngle = -90f + 360f * progress
            val tipRad = Math.toRadians(tipAngle.toDouble())
            val tipX = cx + trackR * cos(tipRad).toFloat()
            val tipY = cy + trackR * sin(tipRad).toFloat()
            drawCircle(color = glowColor.copy(alpha = 0.35f * pulse), center = Offset(tipX, tipY), radius = 6.dp.toPx())
            drawCircle(color = Color.White, center = Offset(tipX, tipY), radius = 2.2.dp.toPx())
        }
    }
}

@Composable
fun GlitchCountdownText(
    timerString: String,
    seconds: Long,
    isHyperFocus: Boolean,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 32.sp
) {
    var isGlitching by remember { mutableStateOf(false) }
    var glitchOffsetX by remember { mutableStateOf(0f) }
    var glitchOffsetY by remember { mutableStateOf(0f) }
    var glitchColor1 by remember { mutableStateOf(Color(0xFF3DFFC4)) }
    var glitchColor2 by remember { mutableStateOf(Color(0xFFFFA630)) }

    // Execute glitch effect on minute boundary change (seconds == 0) and random 30-second intervals
    LaunchedEffect(seconds) {
        if (seconds == 0L || seconds == 30L) {
            isGlitching = true
            // Fast horizontal & vertical vibration loop over ~180ms
            repeat(6) {
                glitchOffsetX = (-8..8).random().toFloat()
                glitchOffsetY = (-4..4).random().toFloat()
                // Randomly swap foreground layers
                if ((0..1).random() == 0) {
                    glitchColor1 = Color(0xFF3DFFC4)
                    glitchColor2 = Color(0xFFFFA630)
                } else {
                    glitchColor1 = Color(0xFFFFA630)
                    glitchColor2 = Color(0xFF3DFFC4)
                }
                kotlinx.coroutines.delay(30L)
            }
            glitchOffsetX = 0f
            glitchOffsetY = 0f
            isGlitching = false
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.fillMaxWidth()
    ) {
        val containerWidth = maxWidth
        val calculatedFontSize = (containerWidth.value / (timerString.length * 0.62f)).coerceIn(10f, 32f).sp
        val calculatedLineHeight = (calculatedFontSize.value * 1.3).sp

        // High fidelity glow effect for the retro-futuristic digital clock
        val neonGlowShadow = Shadow(
            color = if (isHyperFocus) Color(0xFFFF5252).copy(alpha = 0.85f) else Color(0xFF3DFFC4).copy(alpha = 0.85f),
            offset = Offset(0f, 0f),
            blurRadius = 14f
        )

        if (isGlitching) {
            // Cyberpunk Cyan background shadow layer offset
            Text(
                text = timerString,
                fontSize = calculatedFontSize,
                lineHeight = calculatedLineHeight,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = glitchColor1.copy(alpha = 0.8f),
                style = TextStyle(
                    shadow = Shadow(color = glitchColor1, offset = Offset(0f, 0f), blurRadius = 8f)
                ),
                modifier = Modifier.offset(x = (glitchOffsetX + 4f).dp, y = (glitchOffsetY - 2f).dp),
                softWrap = false,
                maxLines = 1
            )
            // Cyberpunk Magenta background shadow layer offset
            Text(
                text = timerString,
                fontSize = calculatedFontSize,
                lineHeight = calculatedLineHeight,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = glitchColor2.copy(alpha = 0.8f),
                style = TextStyle(
                    shadow = Shadow(color = glitchColor2, offset = Offset(0f, 0f), blurRadius = 8f)
                ),
                modifier = Modifier.offset(x = (glitchOffsetX - 4f).dp, y = (glitchOffsetY + 2f).dp),
                softWrap = false,
                maxLines = 1
            )
        }

        // Main text layer
        Text(
            text = timerString,
            fontSize = calculatedFontSize,
            lineHeight = calculatedLineHeight,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = if (isHyperFocus) Color(0xFFFF5252) else Color.White,
            style = TextStyle(
                shadow = neonGlowShadow
            ),
            modifier = Modifier.offset(x = glitchOffsetX.dp, y = glitchOffsetY.dp),
            softWrap = false,
            maxLines = 1
        )
    }
}

@Composable
fun StrictShieldHexagonVisual(
    isStrictModeActive: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // scale and alpha Animatable for the entrance animation
    val entranceScale = remember { Animatable(if (isStrictModeActive) 1f else 0f) }
    val entranceAlpha = remember { Animatable(if (isStrictModeActive) 1f else 0f) }

    LaunchedEffect(isStrictModeActive) {
        if (isStrictModeActive) {
            // Start expanded from corners and scale/alpha in
            entranceScale.snapTo(1.4f)
            entranceAlpha.snapTo(0f)
            
            scope.launch {
                entranceScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
            scope.launch {
                entranceAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500, easing = LinearEasing)
                )
            }
        } else {
            // Smoothly collapse out
            scope.launch {
                entranceScale.animateTo(
                    targetValue = 0.85f,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                )
            }
            scope.launch {
                entranceAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing)
                )
            }
        }
    }

    // Gentle low-frequency alpha pulse transition while strict mode is active
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val currentAlpha = entranceAlpha.value
    if (currentAlpha > 0.01f) {
        val hexSize = 36.dp

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            
            // Performance optimization: Pre-calculate relative 2D offsets of a hexagon
            val hexOffsets = remember(hexSize) {
                with(density) {
                    val sizePx = hexSize.toPx()
                    List(6) { i ->
                        val angleRad = Math.toRadians((i * 60 - 30).toDouble())
                        Offset(
                            (sizePx * Math.cos(angleRad)).toFloat(),
                            (sizePx * Math.sin(angleRad)).toFloat()
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = entranceScale.value
                        scaleY = entranceScale.value
                        alpha = currentAlpha * pulseAlpha
                    }
            ) {
                val width = size.width
                val height = size.height

                // Draw edge glowing vignette force-field gradient
                val vignetteGradient = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF3DFFC4).copy(alpha = 0.05f),
                        Color(0xFF3DFFC4).copy(alpha = 0.25f)
                    ),
                    center = Offset(width / 2f, height / 2f),
                    radius = Math.max(width, height) / 1.1f
                )
                drawRect(brush = vignetteGradient)

                val sizePx = hexSize.toPx()
                val h = sizePx * 2f
                val w = Math.sqrt(3.0).toFloat() * sizePx
                
                val cols = (width / w).toInt() + 2
                val rows = (height / (sizePx * 1.5f)).toInt() + 2

                val path = Path()

                // Intricate honeycomb matrix path generation using optimized pre-allocated vertex offsets
                for (row in -1..rows) {
                    for (col in -1..cols) {
                        val cx = col * w + (if (row % 2 == 1) w / 2f else 0f)
                        val cy = row * sizePx * 1.5f

                        for (i in 0..5) {
                            val vertexOffset = hexOffsets[i]
                            val x = cx + vertexOffset.x
                            val y = cy + vertexOffset.y
                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        path.close()
                    }
                }

                // Outer neon cyan glow path layer
                drawPath(
                    path = path,
                    color = Color(0xFF3DFFC4).copy(alpha = 0.2f),
                    style = Stroke(width = 4.dp.toPx())
                )

                // High definition inner sharp grid line layer
                drawPath(
                    path = path,
                    color = Color(0xFF3DFFC4).copy(alpha = 0.5f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictScheduleManagerScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schedules by viewModel.allSchedules.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    
    var hasAlarmPermission by remember { mutableStateOf(viewModel.hasExactAlarmPermission(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAlarmPermission = viewModel.hasExactAlarmPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showBatteryOptimization by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Schedules", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_schedule_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Schedule")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasAlarmPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("alarm_permission_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Exact Alarm Permission Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "To start scheduled strict sessions precisely on time, Focus Buddy needs the Exact Alarm permission.",
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = { viewModel.requestExactAlarmPermission(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("grant_permission_button")
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No schedules set yet",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Create one using the button below",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("schedules_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleItemCard(
                            schedule = schedule,
                            isSessionActive = activeSession?.origin == "SCHEDULE:${schedule.id}",
                            onToggle = { viewModel.toggleSchedule(context, schedule) },
                            onDelete = { viewModel.deleteSchedule(context, schedule) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { label, startH, startM, endH, endM, days ->
                val newSchedule = StrictSchedule(
                    label = label,
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    daysOfWeek = days.joinToString(",")
                )
                viewModel.addSchedule(context, newSchedule)
                showAddDialog = false

                scope.launch {
                    if (!viewModel.isBatteryOptimizationPromptShown()) {
                        showBatteryOptimization = true
                    }
                }
            }
        )
    }

    if (showBatteryOptimization) {
        BatteryOptimizationDialog(
            onDismiss = {
                scope.launch {
                    viewModel.setBatteryOptimizationPromptShown()
                }
                showBatteryOptimization = false
            }
        )
    }
}

@Composable
fun ScheduleItemCard(
    schedule: StrictSchedule,
    isSessionActive: Boolean = false,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val daysList = listOf("M", "T", "W", "T", "F", "S", "S")
    val enabledDaysSet = remember(schedule.daysOfWeek) {
        schedule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("schedule_item_${schedule.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.label.ifBlank { "Strict Session" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        if (isSessionActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE NOW",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format("%02d:%02d - %02d:%02d", schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("schedule_switch_${schedule.id}")
                    )
                    IconButton(
                        onClick = onDelete,
                        enabled = !isSessionActive,
                        modifier = Modifier.testTag("schedule_delete_${schedule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = if (isSessionActive) "Can't delete while this session is active" else "Delete",
                            tint = if (isSessionActive) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Days indicator row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysList.forEachIndexed { index, dayLetter ->
                    val dayNum = index + 1
                    val isActive = enabledDaysSet.contains(dayNum)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                } else {
                                    Color.White.copy(alpha = 0.05f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLetter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) {
                                if (schedule.isEnabled) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f)
                            } else {
                                Color.White.copy(alpha = 0.3f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, startH: Int, startM: Int, endH: Int, endM: Int, days: List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    
    var startHour by remember { mutableStateOf(9) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(17) }
    var endMinute by remember { mutableStateOf(0) }
    
    var selectedDays by remember { mutableStateOf((1..7).toSet()) }
    
    val daysList = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        modifier = modifier.testTag("add_schedule_dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Strict Schedule",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text("e.g., Work Hours") },
                    modifier = Modifier.fillMaxWidth().testTag("schedule_label_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    )
                )

                // Time picking controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        startHour = h
                                        startMinute = m
                                    },
                                    startHour,
                                    startMinute,
                                    true
                                ).show()
                            }
                            .testTag("pick_start_time"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "START TIME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%02d:%02d", startHour, startMinute),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        endHour = h
                                        endMinute = m
                                    },
                                    endHour,
                                    endMinute,
                                    true
                                ).show()
                            }
                            .testTag("pick_end_time"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "END TIME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%02d:%02d", endHour, endMinute),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Days selector
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "REPEAT ON",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    
                    // Simple wrapping day selection chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daysList.forEachIndexed { index, dayName ->
                            val dayNum = index + 1
                            val isSelected = selectedDays.contains(dayNum)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        selectedDays = if (isSelected) {
                                            selectedDays - dayNum
                                        } else {
                                            selectedDays + dayNum
                                        }
                                    }
                                    .testTag("day_chip_$dayNum"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayName.take(1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        label,
                        startHour,
                        startMinute,
                        endHour,
                        endMinute,
                        selectedDays.sorted()
                    )
                },
                enabled = selectedDays.isNotEmpty(),
                modifier = Modifier.testTag("save_schedule_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_schedule_button")
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

@Composable
fun UninstallDrawerCard(onUninstallClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("uninstall_drawer_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Icon",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Uninstall Focus Buddy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Text(
                text = "To completely remove Focus Buddy, you must first write a personal reflection via our security gate.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Button(
                onClick = onUninstallClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("uninstall_drawer_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = "Begin Uninstall Process",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SectionLinkCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FeatureDrawerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("${testTag}_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = iconTint,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun StudyPlannerDrawerCard(onPlannerClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("study_planner_drawer_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Planner Icon",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Smart Study Planner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Text(
                text = "Configure and generate a dynamic custom-tailored plan.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Button(
                onClick = onPlannerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("study_planner_drawer_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(
                    text = "Launch Planner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SessionReflectionPrompt(
    onSave: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onSkip,
        modifier = modifier.testTag("session_reflection_dialog"),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Reflect",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Nice work!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "What's one thing you understood better today?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Write a positive reflection (optional)...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("reflection_note_input"),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("save_reflection_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("skip_reflection_button")
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

