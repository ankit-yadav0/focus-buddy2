package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictModeSetupWizardScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expandedStep by remember { mutableStateOf(1) }
    var step1Done by remember { mutableStateOf(false) }
    var step2Done by remember { mutableStateOf(false) }
    var step3Done by remember { mutableStateOf(false) }

    // Step 1
    var editingMode by remember { mutableStateOf("ALL") }
    var restrictRulesSpecific by remember { mutableStateOf(true) }
    var restrictSchedulesSpecific by remember { mutableStateOf(true) }

    // Step 2
    var restrictUninstall by remember { mutableStateOf(false) }
    var restrictSettings by remember { mutableStateOf(false) }
    var requirePassword by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordIsSet by remember { mutableStateOf(false) }

    // Step 3
    var deactivationMethod by remember { mutableStateOf("TIME_ONLY") }

    // Step 4
    val devicePolicyManager = remember { context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager }
    val adminComponent = remember { android.content.ComponentName(context, com.example.receiver.MyDeviceAdminReceiver::class.java) }
    var isAdminActive by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) }

    LaunchedEffect(Unit) {
        passwordIsSet = viewModel.hasStrictModePassword()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAdminActive = devicePolicyManager.isAdminActive(adminComponent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val deviceAdminStepSatisfied = !restrictUninstall || isAdminActive
    val passwordStepSatisfied = !requirePassword || passwordIsSet
    val canActivate = deviceAdminStepSatisfied && passwordStepSatisfied

    if (showPasswordDialog) {
        SetPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { pwd ->
                scope.launch {
                    viewModel.setStrictModePassword(pwd)
                    passwordIsSet = true
                    showPasswordDialog = false
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D12),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Activate Strict Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D12))
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.saveStrictModeWizardConfig(
                                FocusViewModel.StrictModeWizardConfig(
                                    restrictionEditingMode = editingMode,
                                    restrictRulesSpecific = restrictRulesSpecific,
                                    restrictSchedulesSpecific = restrictSchedulesSpecific,
                                    restrictUninstall = restrictUninstall,
                                    restrictSettings = restrictSettings,
                                    requirePassword = requirePassword,
                                    deactivationMethod = deactivationMethod
                                )
                            )
                            onActivated()
                        }
                    },
                    enabled = canActivate,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Text("ACTIVATE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if (!canActivate) {
                    Text(
                        text = when {
                            !passwordStepSatisfied -> "Set a password in step 2 to continue."
                            !deviceAdminStepSatisfied -> "Grant Device Admin in step 4 to continue."
                            else -> ""
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // STEP 1
            WizardStep(
                number = 1,
                title = "Restriction Editing",
                subtitle = "Prevent changes to your restrictions.",
                done = step1Done,
                expanded = expandedStep == 1,
                onToggle = { expandedStep = if (expandedStep == 1) 0 else 1 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WizardRadioRow(
                        title = "Restrict All",
                        description = "Editing, deleting, or loosening any restriction won't be allowed. Your blocked-apps list is always locked during Strict Mode, regardless of this choice.",
                        selected = editingMode == "ALL",
                        onClick = { editingMode = "ALL" }
                    )
                    WizardRadioRow(
                        title = "Restrict Specific",
                        description = "Choose which categories can't be edited, deleted, or loosened.",
                        selected = editingMode == "SPECIFIC",
                        onClick = { editingMode = "SPECIFIC" }
                    )
                    if (editingMode == "SPECIFIC") {
                        Column(
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            WizardCheckboxRow(
                                title = "Blocked Apps List",
                                checked = true,
                                enabled = false,
                                onCheckedChange = {}
                            )
                            WizardCheckboxRow(
                                title = "Blocking Rules (long-term blocks & websites)",
                                checked = restrictRulesSpecific,
                                enabled = true,
                                onCheckedChange = { restrictRulesSpecific = it }
                            )
                            WizardCheckboxRow(
                                title = "Schedules",
                                checked = restrictSchedulesSpecific,
                                enabled = true,
                                onCheckedChange = { restrictSchedulesSpecific = it }
                            )
                        }
                    }
                    WizardNextButton {
                        step1Done = true
                        expandedStep = 2
                    }
                }
            }

            // STEP 2
            WizardStep(
                number = 2,
                title = "Extra Restrictions",
                subtitle = "Add additional safeguards to stay in control.",
                done = step2Done,
                expanded = expandedStep == 2,
                onToggle = { expandedStep = if (expandedStep == 2) 0 else 2 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WizardSwitchRow(
                        title = "App Uninstallation",
                        description = "You won't be able to uninstall apps, including Focuss Buddy.",
                        checked = restrictUninstall,
                        onCheckedChange = { restrictUninstall = it }
                    )
                    WizardSwitchRow(
                        title = "Phone Settings",
                        description = "Settings screens that could disable enforcement (Accessibility, Device Admin, overlay permission, your app's own page) are always blocked during Strict Mode. This adds a few broader screens on top. WiFi, Bluetooth, and other normal settings always stay accessible.",
                        checked = restrictSettings,
                        onCheckedChange = { restrictSettings = it }
                    )
                    WizardSwitchRow(
                        title = "Set Password",
                        description = if (passwordIsSet) "Password set - required to open Focuss Buddy while Strict Mode is active."
                                       else "Also require a password to open Focuss Buddy while Strict Mode is active.",
                        checked = requirePassword,
                        onCheckedChange = { checked ->
                            requirePassword = checked
                            if (checked && !passwordIsSet) {
                                showPasswordDialog = true
                            }
                        }
                    )
                    if (requirePassword && passwordIsSet) {
                        TextButton(onClick = { showPasswordDialog = true }) {
                            Text("Change password", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    WizardNextButton {
                        step2Done = true
                        expandedStep = 3
                    }
                }
            }

            // STEP 3
            WizardStep(
                number = 3,
                title = "Deactivation Method",
                subtitle = "Choose how Strict Mode can be deactivated.",
                done = step3Done,
                expanded = expandedStep == 3,
                onToggle = { expandedStep = if (expandedStep == 3) 0 else 3 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WizardRadioRow(
                        title = "Time-Locked (Recommended)",
                        description = "No manual deactivation exists at all. The session can only end once its timer reaches zero.",
                        selected = deactivationMethod == "TIME_ONLY",
                        onClick = { deactivationMethod = "TIME_ONLY" }
                    )
                    WizardRadioRow(
                        title = "Extreme Override",
                        description = "Manual deactivation is possible, but only by perfectly typing a 300-word paragraph (no copy-paste), then waiting a mandatory 45-minute cooldown. Fully disabled 10 PM-6 AM regardless of the cooldown.",
                        selected = deactivationMethod == "EXTREME_OVERRIDE",
                        onClick = { deactivationMethod = "EXTREME_OVERRIDE" }
                    )
                    WizardNextButton {
                        step3Done = true
                        expandedStep = 4
                    }
                }
            }

            // STEP 4
            WizardStep(
                number = 4,
                title = "Activate Device Admin",
                subtitle = "Grant Focuss Buddy the permission to enforce restrictions.",
                done = isAdminActive,
                expanded = expandedStep == 4,
                onToggle = { expandedStep = if (expandedStep == 4) 0 else 4 }
            ) {
                if (!restrictUninstall) {
                    Text(
                        text = "Not required - \"App Uninstallation\" is off in step 2.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else if (isAdminActive) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        Text("Device Admin is active.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Required because \"App Uninstallation\" is on in step 2.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                    putExtra(
                                        android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "Enabling Device Admin blocks uninstallation of Focuss Buddy while Strict Mode is active."
                                    )
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("GRANT DEVICE ADMIN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardStep(
    number: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (done) Color(0xFF22C55E) else Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                } else {
                    Text("$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp), content = content)
            }
        }
    }
}

@Composable
private fun WizardRadioRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = Color.White.copy(alpha = 0.4f))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun WizardCheckboxRow(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Text(
            title,
            color = if (enabled) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp
        )
        if (!enabled) {
            Text("(always locked)", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun WizardSwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun WizardNextButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text("Next", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val mismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Required to open Focuss Buddy while Strict Mode is active. Choose something you won't easily remember without effort.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = mismatch,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) {
                    Text("Passwords don't match.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.length >= 4 && password == confirmPassword
            ) { Text("Set Password") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
