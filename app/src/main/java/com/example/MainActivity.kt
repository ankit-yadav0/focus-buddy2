package com.example

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.FocusBuddyNeonTitle
import com.example.ui.components.NeonCardBorderBrush
import com.example.ui.components.NeonGradientProgressBar
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonTargetLightningLogo
import com.example.ui.helper.WallpaperBox
import com.example.ui.screens.AllAppsDrawerScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AppSelectionScreen
import com.example.ui.screens.AppUsageQuotaScreen
import com.example.ui.screens.BankingPauseScreen
import com.example.ui.screens.BlockDetailsScreen
import com.example.ui.screens.BlocksProgressScreen
import com.example.ui.screens.FocusTimerScreen
import com.example.ui.screens.ForestGalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.NeonSettingsHubScreen
import com.example.ui.screens.PyqPracticeScreen
import com.example.ui.screens.StrictScheduleManagerScreen
import com.example.ui.screens.StudyPlannerScreen
import com.example.ui.screens.TestCalendarScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.FocusViewModel
import com.example.viewmodel.FocusViewModelFactory
import com.example.widget.FocusAppWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Splash Screen matching Panel 1 of `Neon Focus Buddy UI Theme Board.png`:
 *   • Deep #08080F background with diagonal neon magenta & cyan laser beams
 *   • Target + Lightning Bolt emblem in cyan-to-magenta gradient
 *   • "Focus Buddy" dual-tone headline
 *   • "Block Distractions / Build Your Future" subtitle
 *   • Bottom neon cyan-to-magenta loading bar
 */
@Composable
fun NeonSplashScreen() {
    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                NeonTargetLightningLogo(size = 112.dp)
                FocusBuddyNeonTitle(fontSize = 34.sp, focusInCyan = false)
                Text(
                    text = "Block Distractions\nBuild Your Future",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Box(
                modifier = Modifier
                    .width(140.dp)
                    .padding(bottom = 16.dp)
            ) {
                NeonGradientProgressBar(progress = 0.68f, height = 4.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictPasswordGate(
    viewModel: com.example.viewmodel.FocusViewModel,
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler { onExit() }

    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("Incorrect password.") }
    val scope = rememberCoroutineScope()

    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("strict_password_gate_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NeonSurface),
                border = BorderStroke(1.2.dp, NeonCardBorderBrush)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = NeonMagenta,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Strict Mode Locked",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Enter your password to open Focuss Buddy while this Strict Mode session is active.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = false },
                        label = { Text("Password") },
                        isError = error,
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("strict_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )
                    if (error) {
                        Text(errorMessage, color = NeonMagenta, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                if (viewModel.verifyStrictModePassword(password)) {
                                    onUnlocked()
                                } else {
                                    val lockSeconds = viewModel.strictPasswordLockoutSeconds()
                                    errorMessage = if (lockSeconds > 0) "Too many attempts. Try again in ${lockSeconds}s" else "Incorrect password."
                                    error = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("strict_password_unlock_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBlack),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Unlock", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onExit) {
                        Text("Close Focuss Buddy", color = TextSecondary)
                    }
                }
            }
        }
    }
}

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

    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("launch_gate_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NeonSurface
                ),
                border = BorderStroke(1.2.dp, NeonCardBorderBrush)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Lock Icon",
                        tint = NeonCyan,
                        modifier = Modifier.size(64.dp)
                    )

                    Text(
                        text = "Launch Security Gate",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = Color.White
                    )

                    Text(
                        text = "Focuss Buddy requires Overlay & Device Administrator permissions active to prevent unauthorized bypass and guarantee focus-locking consistency.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionRow(
                        title = "System Overlay Permission",
                        description = "Required for instant app-blocking window overlay.",
                        isGranted = hasOverlay,
                        onGrant = onGrantOverlay,
                        grantButtonText = "Grant Overlay"
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    PermissionRow(
                        title = "Device Admin Activation",
                        description = "Required to lock deinstallation of Focuss Buddy.",
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
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Close Focuss Buddy", fontWeight = FontWeight.Bold)
                    }
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
                color = TextSecondary,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isGranted) "Status: ACTIVE" else "Status: INACTIVE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) NeonCyan else NeonMagenta
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onGrant,
            enabled = !isGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = NeonBlack,
                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                disabledContentColor = Color.White.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(grantButtonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val ALLOWED_DEEP_LINK_ROUTES = setOf("uninstall_reflection", "timer")

private fun sanitizeDeepLink(route: String?): String? = route?.takeIf { it in ALLOWED_DEEP_LINK_ROUTES }

class MainActivity : ComponentActivity() {

    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)
    private val availableUpdate = mutableStateOf<com.example.update.UpdateInfo?>(null)

    override fun onStart() {
        super.onStart()
        FocusAppWidgetProvider.requestUpdate(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            val info = com.example.update.UpdateManager.checkForUpdate(applicationContext)
            if (info != null) {
                com.example.update.UpdateManager.showUpdateNotification(applicationContext, info)
                availableUpdate.value = info
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkRoute.value = sanitizeDeepLink(intent.getStringExtra("deep_link_route"))
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent) {
        if (!intent.getBooleanExtra("start_update_download", false)) return
        val url = intent.getStringExtra("update_download_url") ?: return
        val versionName = intent.getStringExtra("update_version_name") ?: "latest"
        val started = com.example.update.UpdateManager.startDownload(this, url, versionName)
        android.widget.Toast.makeText(
            this,
            if (started) "Downloading update $versionName - you'll get a notification when it's ready to install."
            else "Update link rejected (not an official release).",
            android.widget.Toast.LENGTH_LONG
        ).show()
        intent.putExtra("start_update_download", false)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private fun requestNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
        if (prefs.getBoolean("notif_permission_asked", false)) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        prefs.edit().putBoolean("notif_permission_asked", true).apply()
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
        pendingDeepLinkRoute.value = sanitizeDeepLink(intent.getStringExtra("deep_link_route"))
        handleUpdateIntent(intent)
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
        requestNotificationPermissionOnce()

        setContent {
            val app = application as FocusApplication
            val factory = FocusViewModelFactory(app.repository, applicationContext)
            val focusViewModel: FocusViewModel = viewModel(factory = factory)
            val accentTheme by focusViewModel.accentTheme.collectAsStateWithLifecycle()
            val isInitialized by focusViewModel.isInitialized.collectAsStateWithLifecycle()

            val pendingUpdate = availableUpdate.value
            if (pendingUpdate != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { availableUpdate.value = null },
                    containerColor = NeonSurface,
                    title = { androidx.compose.material3.Text("Update available: ${pendingUpdate.versionName}", color = Color.White) },
                    text = {
                        androidx.compose.material3.Text(
                            if (pendingUpdate.releaseNotes.isBlank()) "Tap Update to download and install. Your data stays exactly as it is."
                            else pendingUpdate.releaseNotes.take(400),
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val started = com.example.update.UpdateManager.startDownload(this@MainActivity, pendingUpdate.downloadUrl, pendingUpdate.versionName)
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                if (started) "Downloading update..." else "Update link rejected (not an official release).",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            availableUpdate.value = null
                        }) { androidx.compose.material3.Text("Update", color = NeonCyan) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { availableUpdate.value = null }) { androidx.compose.material3.Text("Later", color = TextSecondary) }
                    }
                )
            }

            if (!isInitialized) {
                NeonSplashScreen()
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
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                        }
                                    }
                                },
                                onActivateAdmin = {
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                                        putExtra(
                                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                            "Enabling Device Admin blocks uninstallation of Focuss Buddy."
                                        )
                                    }
                                    try {
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                    }
                                },
                                onExit = {
                                    finishAffinity()
                                }
                            )
                        } else {
                            val activeSession by focusViewModel.activeSession.collectAsStateWithLifecycle()
                            var isPasswordGateActive by remember { mutableStateOf(false) }
                            var passwordVerified by remember { mutableStateOf(false) }

                            LaunchedEffect(activeSession) {
                                isPasswordGateActive = focusViewModel.isPasswordGateActive()
                                while (isPasswordGateActive) {
                                    kotlinx.coroutines.delay(1000L)
                                    isPasswordGateActive = focusViewModel.isPasswordGateActive()
                                }
                            }

                            val gateLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                            DisposableEffect(gateLifecycleOwner) {
                                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                    when (event) {
                                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                            lifecycleScope.launch {
                                                val active = focusViewModel.isPasswordGateActive()
                                                isPasswordGateActive = active
                                            }
                                        }
                                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                                            passwordVerified = false
                                        }
                                        else -> {}
                                    }
                                }
                                gateLifecycleOwner.lifecycle.addObserver(observer)
                                onDispose { gateLifecycleOwner.lifecycle.removeObserver(observer) }
                            }

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
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route
                            val isGateShowing = isPasswordGateActive && !passwordVerified

                            DisposableEffect(currentRoute, isGateShowing) {
                                if (currentRoute == "uninstall_reflection" ||
                                    currentRoute == "strict_override" ||
                                    currentRoute == "app_lock" ||
                                    isGateShowing
                                ) {
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
                                                        onNavigateToAppLock = { navController.navigate("app_lock") },
                                                        onNavigateToBankingPause = { navController.navigate("banking_pause") },
                                                        onNavigateToBlockDetails = { id, type -> navController.navigate("block_details/$type/$id") },
                                                        onPickWallpaper = { launchWallpaperPicker() },
                                                        onClearWallpaper = { removeCustomWallpaper() },
                                                        wallpaperOpacity = wallpaperOpacity,
                                                        onWallpaperOpacityChange = { newOpacity ->
                                                            wallpaperOpacity = newOpacity
                                                            sharedPrefs.edit().putFloat("wallpaper_opacity", newOpacity).apply()
                                                        },
                                                        onNavigateToUninstall = { navController.navigate("uninstall_reflection") },
                                                        onNavigateToStrictOverride = { navController.navigate("strict_override") },
                                                        onNavigateToRoute = { route ->
                                                            navController.navigate(route) {
                                                                launchSingleTop = true
                                                            }
                                                        }
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
                                                composable("study_planner") {
                                                    StudyPlannerScreen(
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                                composable("test_calendar") {
                                                    TestCalendarScreen(
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                                composable("pyq_practice") {
                                                    PyqPracticeScreen(
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                                composable("app_usage") {
                                                    AppUsageQuotaScreen(
                                                        viewModel = focusViewModel,
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                                composable("all_apps") {
                                                    AllAppsDrawerScreen(
                                                        viewModel = focusViewModel,
                                                        onBack = { navController.popBackStack() },
                                                        onNavigateToRoute = { route -> navController.navigate(route) }
                                                    )
                                                }
                                                composable("neon_settings") {
                                                    NeonSettingsHubScreen(
                                                        onBack = { navController.popBackStack() },
                                                        onNavigateToRoute = { route -> navController.navigate(route) }
                                                    )
                                                }
                                                composable("timer") {
                                                    FocusTimerScreen(
                                                        viewModel = focusViewModel,
                                                        onBack = { navController.popBackStack() },
                                                        onNavigateToSchedule = { navController.navigate("schedule_manager") },
                                                        onStartSession = { minutes, isStrict, totalMs ->
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
                                                composable("app_lock") {
                                                    AppLockScreen(
                                                        viewModel = focusViewModel,
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                                composable("banking_pause") {
                                                    BankingPauseScreen(
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
                                                composable("uninstall_reflection") {
                                                    com.example.ui.screens.UninstallReflectionScreen(
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                                composable("strict_override") {
                                                    com.example.ui.screens.StrictOverrideScreen(
                                                        viewModel = focusViewModel,
                                                        onBack = { navController.popBackStack() }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isGateShowing) {
                                StrictPasswordGate(
                                    viewModel = focusViewModel,
                                    onUnlocked = { passwordVerified = true },
                                    onExit = { finishAffinity() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
