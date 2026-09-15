package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.example.data.LongTermBlock
import com.example.viewmodel.AppInfo
import com.example.viewmodel.FocusViewModel
import com.example.ui.theme.AccentTheme
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
    onNavigateToAppSelection: () -> Unit,
    onNavigateToBlockDetails: (Int, String) -> Unit,
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    wallpaperOpacity: Float = 0.5f,
    onWallpaperOpacityChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()

    var isAccessibilityEnabled by remember { mutableStateOf(viewModel.isAccessibilityServiceEnabled()) }
    var isUsageEnabled by remember { mutableStateOf(viewModel.isUsageStatsPermissionGranted()) }

    // Refresh permission states when app resumes
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = viewModel.isAccessibilityServiceEnabled()
                isUsageEnabled = viewModel.isUsageStatsPermissionGranted()
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

                    // Content-Level Blocking Settings (YouTube Shorts / Reels / Spotlight)
                    ContentLevelBlockingCard(viewModel = viewModel)

                    // Quick Actions Title
                    Text(
                        text = "Quick Actions",
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
            }
        }
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
                                      Column(
                                          modifier = Modifier.weight(1f),
                                          horizontalAlignment = Alignment.Start
                                      ) {
                                          Text(
                                              text = "START DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold,
                                              maxLines = 1
                                          )
                                          Text(
                                              text = startDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold,
                                              maxLines = 1
                                          )
                                      }
                                      Column(
                                          modifier = Modifier.weight(1f),
                                          horizontalAlignment = Alignment.CenterHorizontally
                                      ) {
                                          Text(
                                              text = "END DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold,
                                              maxLines = 1
                                          )
                                          Text(
                                              text = endDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold,
                                              maxLines = 1
                                          )
                                      }
                                      Column(
                                          modifier = Modifier.weight(1f),
                                          horizontalAlignment = Alignment.End
                                      ) {
                                          Text(
                                              text = "REMAINING",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold,
                                              maxLines = 1
                                          )
                                          Text(
                                              text = if (isLocked) "$remainingDays Days" else "Expired",
                                              fontSize = 12.sp,
                                              color = if (isLocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Black,
                                              maxLines = 1
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
                                      Column(
                                          modifier = Modifier.weight(1f),
                                          horizontalAlignment = Alignment.Start
                                      ) {
                                          Text(
                                              text = "START DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold,
                                              maxLines = 1
                                          )
                                          Text(
                                              text = startDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold,
                                              maxLines = 1
                                          )
                                      }
                                      Column(
                                          modifier = Modifier.weight(1f),
                                          horizontalAlignment = Alignment.CenterHorizontally
                                      ) {
                                          Text(
                                              text = "END DATE",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold,
                                              maxLines = 1
                                          )
                                          Text(
                                              text = endDateStr,
                                              fontSize = 12.sp,
                                              color = Color.White,
                                              fontWeight = FontWeight.SemiBold,
                                              maxLines = 1
                                          )
                                      }
                                      Column(
                                          modifier = Modifier.weight(1f),
                                          horizontalAlignment = Alignment.End
                                      ) {
                                          Text(
                                              text = "REMAINING",
                                              fontSize = 10.sp,
                                              color = Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Bold,
                                              maxLines = 1
                                          )
                                          Text(
                                              text = if (isLocked) "$remainingDays Days" else "Expired",
                                              fontSize = 12.sp,
                                              color = if (isLocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                              fontWeight = FontWeight.Black,
                                              maxLines = 1
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

