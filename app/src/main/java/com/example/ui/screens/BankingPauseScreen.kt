package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AccessibilityToggler
import com.example.service.ForegroundProbe
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankingPauseScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enabled by viewModel.bankingPauseEnabled.collectAsStateWithLifecycle()
    val appList by viewModel.bankingPauseListState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val strictActive by viewModel.isStrictModeActive.collectAsStateWithLifecycle()

    var hasSecure by remember { mutableStateOf(AccessibilityToggler.hasSecureSettingsPermission(context)) }
    var hasUsage by remember { mutableStateOf(ForegroundProbe.hasUsageAccess(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            hasSecure = AccessibilityToggler.hasSecureSettingsPermission(context)
            hasUsage = ForegroundProbe.hasUsageAccess(context)
            delay(1500)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(appList, searchQuery) {
        if (searchQuery.isBlank()) appList
        else appList.filter { it.appName.contains(searchQuery, ignoreCase = true) }
    }
    val ready = hasSecure && hasUsage
    val adbCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Banking Mode (Pause)", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("⚡", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = NeonBlack,
        modifier = modifier
    ) { innerPadding ->
        NeonLaserBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    BankingInfoCard(
                        "Banking apps like Navi refuse to run while an accessibility service is on. " +
                            "With Banking Pause, Focuss Buddy switches itself off when you open a chosen app and " +
                            "back on when you leave it (at the latest after 10 minutes). " +
                            "The first open of a banking app may still show its error - just open it again."
                    )
                }

                if (strictActive) {
                    item {
                        BankingInfoCard("Strict Mode is running, so this list cannot be changed until it ends.", warning = true)
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

                            BankingStatusRow("Usage access", hasUsage)
                            if (!hasUsage) {
                                OutlinedButton(
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
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Open Usage access settings", color = NeonCyan) }
                            }

                            BankingStatusRow("Permission to switch protection back on", hasSecure)
                            if (!hasSecure) {
                                Text(
                                    "One-time setup over adb (Android 11: Developer options > Wireless debugging, then " +
                                        "run this command from Termux or any adb shell on the phone). " +
                                        "Turn Wireless debugging off again afterwards.",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    adbCommand,
                                    fontSize = 12.sp,
                                    color = NeonCyan
                                )
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("adb", adbCommand))
                                        Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Copy adb command", color = NeonCyan) }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable Banking Pause", fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    if (ready) "Chosen apps below pause protection while open."
                                    else "Finish the setup above first.",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { viewModel.setBankingPauseEnabled(it) },
                                enabled = !strictActive && (enabled || ready),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonCyan
                                )
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...", color = TextSecondary) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search icon", tint = NeonCyan) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeonSurface,
                            unfocusedContainerColor = NeonSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    }
                } else {
                    items(filteredList, key = { it.packageName }) { app ->
                        AppListItemRow(
                            app = app,
                            isReadOnly = strictActive,
                            checked = app.isBankingPause,
                            onToggle = { viewModel.toggleBankingApp(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BankingInfoCard(text: String, warning: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) NeonMagenta.copy(alpha = 0.14f) else NeonSurface
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun BankingStatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (ok) "Done" else "Missing",
            tint = if (ok) NeonCyan else NeonMagenta,
            modifier = Modifier.size(20.dp)
        )
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
