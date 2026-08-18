package com.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.view.WindowManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.helper.WallpaperBox
import com.example.ui.screens.AppSelectionScreen
import com.example.ui.screens.FocusTimerScreen
import com.example.ui.screens.PreSessionRitualScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.BlockDetailsScreen
import com.example.ui.screens.StrictScheduleManagerScreen
import com.example.ui.screens.BlocksProgressScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.ForestGalleryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FocusViewModel
import com.example.viewmodel.FocusViewModelFactory

import android.provider.Settings
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
fun LaunchEnforcementGate(
    hasOverlay: Boolean,
    hasAdmin: Boolean,
    onGrantOverlay: () -> Unit,
    onActivateAdmin: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler {
        onExit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("launch_gate_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete, // visual lock / shield
                    contentDescription = "Lock Icon",
                    tint = Color(0xFF3DFFC4),
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "Launch Security Gate",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.White
                )

                Text(
                    text = "Focus Buddy requires Overlay & Device Administrator permissions active to prevent unauthorized bypass and guarantee focus-locking consistency.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionRow(
                    title = "System Overlay Permission",
                    description = "Required for instant app-blocking window overlay.",
                    isGranted = hasOverlay,
                    onGrant = onGrantOverlay,
                    grantButtonText = "Grant Overlay"
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                PermissionRow(
                    title = "Device Admin Activation",
                    description = "Required to lock deinstallation of Focus Buddy.",
                    isGranted = hasAdmin,
                    onGrant = onActivateAdmin,
                    grantButtonText = "Activate Admin"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("gate_exit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Focus Buddy", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    grantButtonText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isGranted) "Status: ACTIVE" else "Status: INACTIVE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) Color(0xFF3DFFC4) else Color(0xFFFF5252)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onGrant,
            enabled = !isGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3DFFC4),
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                disabledContentColor = Color.White.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(grantButtonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

class MainActivity : ComponentActivity() {

    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkRoute.value = intent.getStringExtra("deep_link_route")
    }

    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
                    .edit()
                    .putString("custom_wallpaper_uri", uri.toString())
                    .apply()
                recreate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun launchWallpaperPicker() {
        pickWallpaperLauncher.launch(arrayOf("image/*"))
    }

    private fun removeCustomWallpaper() {
        getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
            .edit()
            .remove("custom_wallpaper_uri")
            .apply()
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkRoute.value = intent.getStringExtra("deep_link_route")
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val label = getString(R.string.app_name)
                withContext(Dispatchers.Main) {
                    @Suppress("DEPRECATION")
                    setTaskDescription(
                        ActivityManager.TaskDescription(label, R.mipmap.ic_launcher)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        enableEdgeToEdge()

        setContent {
            val app = application as FocusApplication
            val factory = FocusViewModelFactory(app.repository, applicationContext)
            val focusViewModel: FocusViewModel = viewModel(factory = factory)
            val accentTheme by focusViewModel.accentTheme.collectAsStateWithLifecycle()
            val isInitialized by focusViewModel.isInitialized.collectAsStateWithLifecycle()

            if (!isInitialized) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color(0xFF3DFFC4)
                    )
                }
            } else {
                val dpm = remember { getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
                val adminComp = remember { ComponentName(this@MainActivity, com.example.receiver.MyDeviceAdminReceiver::class.java) }
                var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(this@MainActivity)) }
                var hasDeviceAdmin by remember { mutableStateOf(dpm.isAdminActive(adminComp)) }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                            hasDeviceAdmin = dpm.isAdminActive(adminComp)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                MyApplicationTheme(accentThemeName = accentTheme) {
                    if (!hasOverlayPermission || !hasDeviceAdmin) {
                        LaunchEnforcementGate(
                            hasOverlay = hasOverlayPermission,
                            hasAdmin = hasDeviceAdmin,
                            onGrantOverlay = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                    } catch (ex: Exception) {
                                        // Handle gracefully
                                    }
                                }
                            },
                            onActivateAdmin = {
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                                    putExtra(
                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "Enabling Device Admin blocks uninstallation of Focus Buddy."
                                    )
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    // Handle gracefully
                                }
                            },
                            onExit = {
                                finishAffinity()
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        LaunchedEffect(pendingDeepLinkRoute.value) {
                            val route = pendingDeepLinkRoute.value
                            if (route != null) {
                                try {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                } catch (e: Exception) {
                                }
                                pendingDeepLinkRoute.value = null
                            }
                        }
                        LaunchedEffect(Unit) {
                            focusViewModel.seedTestScheduleIfNeeded()
                            try {
                                com.example.scheduler.TestCountdownScheduler.clearNotification(applicationContext)
                                com.example.scheduler.TestCountdownScheduler.cancelUpdates(applicationContext)
                            } catch (e: Exception) {
                            }
                        }
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        DisposableEffect(currentRoute) {
                            if (currentRoute == "uninstall_reflection") {
                                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                            onDispose { }
                        }

                        val sharedPrefs = remember {
                            getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
                        }
                        val hasWallpaper = remember {
                            !sharedPrefs.getString("custom_wallpaper_uri", null).isNullOrEmpty()
                        }
                        var wallpaperOpacity by remember {
                            mutableStateOf(sharedPrefs.getFloat("wallpaper_opacity", 0.5f))
                        }

                        // If a custom wallpaper is active, override background to transparent so it shows through the Scaffolds
                        val originalColorScheme = MaterialTheme.colorScheme
                        val customColorScheme = remember(originalColorScheme, hasWallpaper) {
                            if (hasWallpaper) {
                                originalColorScheme.copy(background = Color.Transparent)
                            } else {
                                originalColorScheme
                            }
                        }

                        MaterialTheme(
                            colorScheme = customColorScheme,
                            typography = MaterialTheme.typography,
                            shapes = MaterialTheme.shapes
                        ) {
                            WallpaperBox(
                                modifier = Modifier.fillMaxSize(),
                                opacity = wallpaperOpacity
                            ) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = Color.Transparent,
                                    bottomBar = {
                                        if (currentRoute in com.example.ui.components.controlDeckTabs.map { it.route }) {
                                            com.example.ui.components.ControlDeckBottomNav(
                                                currentRoute = currentRoute,
                                                onTabSelected = { route ->
                                                    navController.navigate(route) {
                                                        popUpTo("home") { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        NavHost(
                                            navController = navController,
                                            startDestination = "home",
                                            modifier = Modifier.padding(innerPadding)
                                        ) {
                                            composable("home") {
                                                HomeScreen(
                                                    viewModel = focusViewModel,
                                                    onNavigateToTimer = { navController.navigate("timer") },
                                                    onNavigateToAppSelection = { navController.navigate("app_selection") },
                                                    onNavigateToBlockDetails = { id, type -> navController.navigate("block_details/$type/$id") },
                                                    onPickWallpaper = { launchWallpaperPicker() },
                                                    onClearWallpaper = { removeCustomWallpaper() },
                                                    wallpaperOpacity = wallpaperOpacity,
                                                    onWallpaperOpacityChange = { newOpacity ->
                                                        wallpaperOpacity = newOpacity
                                                        sharedPrefs.edit().putFloat("wallpaper_opacity", newOpacity).apply()
                                                    },
                                                    onNavigateToUninstall = { navController.navigate("uninstall_reflection") },
                                                    onNavigateToStudyPlanner = { navController.navigate("study_planner") }
                                                )
                                            }
                                            composable("blocks_progress") {
                                                BlocksProgressScreen(
                                                    viewModel = focusViewModel,
                                                    onBack = { navController.popBackStack() },
                                                    onNavigateToBlockDetails = { id, type -> navController.navigate("block_details/$type/$id") }
                                                )
                                            }
                                            composable("insights") {
                                                InsightsScreen(
                                                    viewModel = focusViewModel,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable("forest_gallery") {
                                                ForestGalleryScreen(
                                                    viewModel = focusViewModel,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable("timer") {
                                                FocusTimerScreen(
                                                    viewModel = focusViewModel,
                                                    onBack = { navController.popBackStack() },
                                                    onNavigateToSchedule = { navController.navigate("schedule_manager") },
                                                    onNavigateToPreSessionRitual = { minutes, isStrict, totalMs ->
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("minutes", minutes)
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("isStrict", isStrict)
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("totalMs", totalMs)
                                                        navController.navigate("pre_session_ritual")
                                                     }
                                                )
                                            }
                                            composable("pre_session_ritual") {
                                                val prevBackStackEntry = remember { navController.previousBackStackEntry }
                                                val minutes = prevBackStackEntry?.savedStateHandle?.get<Int>("minutes") ?: 15
                                                val isStrict = prevBackStackEntry?.savedStateHandle?.get<Boolean>("isStrict") ?: false
                                                val totalMs = prevBackStackEntry?.savedStateHandle?.get<Long?>("totalMs")

                                                PreSessionRitualScreen(
                                                    viewModel = focusViewModel,
                                                    onRitualComplete = {
                                                        focusViewModel.startFocusSession(minutes, isStrict, totalMs)
                                                        navController.navigate("home") {
                                                            popUpTo("home") { inclusive = false }
                                                        }
                                                    },
                                                    onSkip = {
                                                        focusViewModel.startFocusSession(minutes, isStrict, totalMs)
                                                        navController.navigate("home") {
                                                            popUpTo("home") { inclusive = false }
                                                        }
                                                    }
                                                )
                                            }
                                            composable("schedule_manager") {
                                                StrictScheduleManagerScreen(
                                                    viewModel = focusViewModel,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable("app_selection") {
                                                AppSelectionScreen(
                                                    viewModel = focusViewModel,
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                            composable("block_details/{type}/{id}") { backStackEntry ->
                                                val type = backStackEntry.arguments?.getString("type") ?: ""
                                                val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: -1
                                                BlockDetailsScreen(
                                                    viewModel = focusViewModel,
                                                    blockId = id,
                                                    blockType = type,
                                                    onNavigateBack = { navController.popBackStack() }
                                                )
                                            }
                                             composable("study_planner") {
                                                 var checkingState by remember { mutableStateOf(true) }
                                                 var startInDashboard by remember { mutableStateOf(false) }

                                                 LaunchedEffect(Unit) {
                                                     val plan = focusViewModel.getSetting("saved_study_plan")
                                                     startInDashboard = !plan.isNullOrBlank()
                                                     checkingState = false
                                                 }

                                                 if (checkingState) {
                                                     Box(
                                                         modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
                                                         contentAlignment = Alignment.Center
                                                     ) {
                                                         androidx.compose.material3.CircularProgressIndicator(
                                                             modifier = Modifier.testTag("planner_router_loader"),
                                                             color = MaterialTheme.colorScheme.primary
                                                         )
                                                     }
                                                 } else {
                                                     if (startInDashboard) {
                                                         com.example.ui.screens.StudyPlanDashboardScreen(
                                                             viewModel = focusViewModel,
                                                             onBack = { navController.popBackStack() },
                                                             onEditPlan = { navController.navigate("study_planner_input") }
                                                         )
                                                     } else {
                                                         com.example.ui.screens.SmartPlannerInputScreen(
                                                             viewModel = focusViewModel,
                                                             onBack = { navController.popBackStack() },
                                                             onSaved = {
                                                                 navController.navigate("study_plan_dashboard") {
                                                                     popUpTo("study_planner") { inclusive = true }
                                                                 }
                                                             }
                                                         )
                                                     }
                                                 }
                                             }
                                             composable("study_plan_dashboard") {
                                                 com.example.ui.screens.StudyPlanDashboardScreen(
                                                     viewModel = focusViewModel,
                                                     onBack = { navController.popBackStack() },
                                                     onEditPlan = { navController.navigate("study_planner_input") }
                                                 )
                                             }
                                             composable("study_planner_input") {
                                                 com.example.ui.screens.SmartPlannerInputScreen(
                                                     viewModel = focusViewModel,
                                                     onBack = { navController.popBackStack() },
                                                     onSaved = {
                                                         navController.navigate("study_plan_dashboard") {
                                                             popUpTo("study_planner_input") { inclusive = true }
                                                         }
                                                     }
                                                 )
                                             }
                                             composable("uninstall_reflection") {
                                                com.example.ui.screens.UninstallReflectionScreen(
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }

                                        }

                                        // Floating Wallpaper Action Row specifically on the HomeScreen has been removed to keep the dashboard screen clean
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
