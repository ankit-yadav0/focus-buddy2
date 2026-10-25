package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.Button
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
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.delay

/**
 * Banking Pause: banking apps such as Navi refuse to run while a user-installed accessibility
 * service is on. With this on, Focuss Buddy switches itself off when a chosen app opens and back on
 * when it is left. Needs two permissions (see the status card); the list is frozen in Strict Mode.
 */
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
                title = { Text("Banking Pause", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            ) { Text("Open Usage access settings") }
                        }

                        BankingStatusRow("Permission to switch protection back on", hasSecure)
                        if (!hasSecure) {
                            Text(
                                "One-time setup over adb (Android 11: Developer options > Wireless debugging, then " +
                                    "run this command from Termux or any adb shell on the phone). " +
                                    "Turn Wireless debugging off again afterwards.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                adbCommand,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("adb", adbCommand))
                                    Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Copy adb command") }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.setBankingPauseEnabled(it) },
                            enabled = !strictActive && (enabled || ready)
                        )
                    }
                }
            }

            item {
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
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun BankingInfoCard(text: String, warning: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun BankingStatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (ok) "Done" else "Missing",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
