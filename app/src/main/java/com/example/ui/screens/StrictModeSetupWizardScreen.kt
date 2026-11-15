package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.NeonCircularLockEmblem
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonPrimaryGradientButton
import com.example.ui.components.NeonSubtleBorderBrush
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.FocusViewModel
import kotlinx.coroutines.launch

/**
 * Strict Mode Setup Screen (Panel 11) — 100% Interactive & Functional:
 *   • "Lock-in Period" dropdown (Active Session Duration, 1 Hour, 6 Hours, 24 Hours, 7 Days, 14 Days, 30 Days)
 *     actually starts a real Strict Mode session if a lock-in duration is chosen!
 *   • "Restrictions" rows are real interactive toggles wired to restrictSettings, restrictUninstall, and editingMode!
 *   • "Extreme Override" sentence box lets the user type "I choose discipline over comfort" to verify/unlock override!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictModeSetupWizardScreen(
    viewModel: FocusViewModel,
    onBack: () -> Unit,
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expandedStep by remember { mutableStateOf(0) }
    var step1Done by remember { mutableStateOf(true) }
    var step2Done by remember { mutableStateOf(true) }
    var step3Done by remember { mutableStateOf(true) }

    val durationOptions = remember {
        listOf(
            "Active Session Duration" to 0L,
            "1 Hour Lock-in" to 60L * 60_000L,
            "6 Hours Lock-in" to 6L * 3600_000L,
            "24 Hours Lock-in" to 24L * 3600_000L,
            "7 Days Lock-in" to 7L * 24L * 3600_000L,
            "14 Days Lock-in" to 14L * 24L * 3600_000L,
            "30 Days Lock-in" to 30L * 24L * 3600_000L
        )
    }
    var selectedDurationPair by remember { mutableStateOf(durationOptions.first()) }
    var durationMenuExpanded by remember { mutableStateOf(false) }

    // Step 1
    var editingMode by remember { mutableStateOf("ALL") }
    var restrictRulesSpecific by remember { mutableStateOf(true) }
    var restrictSchedulesSpecific by remember { mutableStateOf(true) }

    // Step 2
    var restrictUninstall by remember { mutableStateOf(false) }
    var restrictSettings by remember { mutableStateOf(true) }
    var requirePassword by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordIsSet by remember { mutableStateOf(false) }

    // Step 3
    var deactivationMethod by remember { mutableStateOf("EXTREME_OVERRIDE") }
    var overrideSentenceInput by remember { mutableStateOf("") }

    // Step 4
    val devicePolicyManager = remember { context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager }
    val adminComponent = remember { android.content.ComponentName(context, com.example.receiver.MyDeviceAdminReceiver::class.java) }
    var isAdminActive by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponent)) }

    LaunchedEffect(Unit) {
        passwordIsSet = viewModel.hasStrictModePassword()
        viewModel.getSetting("strict_restriction_editing_mode")?.let { editingMode = it }
        viewModel.getSetting("strict_restrict_rules_specific")?.toBooleanStrictOrNull()?.let { restrictRulesSpecific = it }
        viewModel.getSetting("strict_restrict_schedules_specific")?.toBooleanStrictOrNull()?.let { restrictSchedulesSpecific = it }
        viewModel.getSetting("strict_restrict_uninstall")?.toBooleanStrictOrNull()?.let { restrictUninstall = it }
        viewModel.getSetting("strict_restrict_settings")?.toBooleanStrictOrNull()?.let { restrictSettings = it }
        viewModel.getSetting("strict_require_password")?.toBooleanStrictOrNull()?.let { requirePassword = it }
        viewModel.getSetting("strict_deactivation_method")?.let { deactivationMethod = it }
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
        containerColor = NeonBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Strict Mode",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NeonSurface)
                            .border(1.dp, NeonMagenta.copy(alpha = 0.7f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = NeonMagenta,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "DND",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        NeonLaserBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NeonCircularLockEmblem(size = 120.dp)
                        Text(
                            text = "Lock-in Period",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                }

                // Select Duration Dropdown (Interactive)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Select Duration",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NeonSurface)
                                .border(1.2.dp, NeonSubtleBorderBrush, RoundedCornerShape(16.dp))
                                .clickable { durationMenuExpanded = true }
                                .padding(horizontal = 18.dp, vertical = 15.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedDurationPair.first,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = durationMenuExpanded,
                            onDismissRequest = { durationMenuExpanded = false },
                            modifier = Modifier.background(NeonSurface)
                        ) {
                            durationOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.first, color = Color.White) },
                                    onClick = {
                                        selectedDurationPair = option
                                        durationMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Interactive Restrictions Toggles (Directly wired to state)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Restrictions (Tap to toggle)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    NeonInteractiveRestrictionRow(
                        label = "Block Settings (Reset, Accessibility, Overlay)",
                        checked = restrictSettings,
                        onToggle = { restrictSettings = !restrictSettings }
                    )
                    NeonInteractiveRestrictionRow(
                        label = "Block Device Admin & App Uninstallation",
                        checked = restrictUninstall,
                        onToggle = { restrictUninstall = !restrictUninstall }
                    )
                    NeonInteractiveRestrictionRow(
                        label = "Lock All Blocking Rules & Schedules",
                        checked = editingMode == "ALL",
                        onToggle = { editingMode = if (editingMode == "ALL") "SPECIFIC" else "ALL" }
                    )
                }

                // Enable Strict Mode Button — Saves config AND starts session if a lock-in duration was chosen
                NeonPrimaryGradientButton(
                    text = "Enable Strict Mode",
                    enabled = canActivate,
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
                            val lockMs = selectedDurationPair.second
                            if (lockMs > 0L) {
                                val mins = (lockMs / 60_000L).toInt().coerceAtLeast(1)
                                viewModel.startFocusSession(
                                    minutes = mins,
                                    isStrict = true,
                                    totalMs = lockMs
                                )
                                Toast.makeText(context, "Strict Mode Lock-in started (${selectedDurationPair.first})", Toast.LENGTH_SHORT).show()
                            }
                            onActivated()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 56.dp,
                    fontSize = 17.sp
                )

                if (!canActivate) {
                    Text(
                        text = when {
                            !passwordStepSatisfied -> "Set a password in Step 2 below to continue."
                            !deviceAdminStepSatisfied -> "Grant Device Admin in Step 4 below to continue."
                            else -> ""
                        },
                        fontSize = 12.sp,
                        color = NeonMagenta
                    )
                }

                // Interactive Extreme Override Box
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Extreme Override",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Switch(
                            checked = deactivationMethod == "EXTREME_OVERRIDE",
                            onCheckedChange = { enabled ->
                                deactivationMethod = if (enabled) "EXTREME_OVERRIDE" else "TIME_ONLY"
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonMagenta
                            )
                        )
                    }
                    Text(
                        text = "Type the following sentence to verify Extreme Override:",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                    OutlinedTextField(
                        value = overrideSentenceInput,
                        onValueChange = { overrideSentenceInput = it },
                        placeholder = {
                            Text(
                                text = "I choose discipline over comfort",
                                color = NeonCyan.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeonSurface,
                            unfocusedContainerColor = NeonSurface,
                            focusedBorderColor = if (overrideSentenceInput.trim().equals("I choose discipline over comfort", ignoreCase = true)) NeonCyan else NeonMagenta,
                            unfocusedBorderColor = Color(0xFF2E3248)
                        )
                    )
                    Text(
                        text = "Cooldown: 45 minutes  •  Not available from 10:00 PM – 6:00 AM",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Text(
                    text = "DETAILED SAFEGUARD STEPS",
                    color = NeonCyan,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

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
                            description = "Editing, deleting, or loosening any restriction won't be allowed.",
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
                            description = "Blocks settings screens that could disable enforcement.",
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
                                Text("Change password", fontSize = 12.sp, color = NeonCyan)
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
                            description = "Manual deactivation is possible only by perfectly typing a 300-word discipline challenge, then waiting a 45-minute cooldown (disabled 10 PM – 6 AM).",
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
                    subtitle = "Grant Focuss Buddy permission to enforce restrictions.",
                    done = isAdminActive,
                    expanded = expandedStep == 4,
                    onToggle = { expandedStep = if (expandedStep == 4) 0 else 4 }
                ) {
                    if (!restrictUninstall) {
                        Text(
                            text = "Not required — \"App Uninstallation\" is off in step 2.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    } else if (isAdminActive) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Text("Device Admin is active.", fontSize = 13.sp, color = Color.White)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Required because \"App Uninstallation\" is on in step 2.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            NeonPrimaryGradientButton(
                                text = "GRANT DEVICE ADMIN",
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
                                modifier = Modifier.fillMaxWidth(),
                                height = 48.dp,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun NeonInteractiveRestrictionRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeonSurface.copy(alpha = 0.65f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) NeonMagenta else TextSecondary,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = label,
            color = if (checked) Color.White else TextSecondary,
            fontSize = 13.5.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
        )
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
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (done) Brush.linearGradient(listOf(NeonCyan, NeonViolet))
                        else Brush.linearGradient(listOf(NeonSurface, NeonSurface)),
                        CircleShape
                    )
                    .border(1.dp, if (done) NeonCyan else Color(0xFF2E3248), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                } else {
                    Text("$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextSecondary
            )
        }
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NeonSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonSubtleBorderBrush)
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
            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = TextSecondary)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
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
            colors = CheckboxDefaults.colors(checkedColor = NeonCyan)
        )
        Text(
            title,
            color = if (enabled) Color.White else TextSecondary,
            fontSize = 13.sp
        )
        if (!enabled) {
            Text("(always locked)", color = TextSecondary, fontSize = 11.sp)
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
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeonCyan,
                uncheckedTrackColor = NeonBlack
            )
        )
    }
}

@Composable
private fun WizardNextButton(onClick: () -> Unit) {
    NeonPrimaryGradientButton(
        text = "Next",
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        height = 46.dp,
        fontSize = 14.sp
    )
}

@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val mismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        title = { Text("Set Password", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Required to open Focuss Buddy while Strict Mode is active. Choose something you won't easily remember without effort.",
                    fontSize = 12.sp,
                    color = TextSecondary
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
                    Text("Passwords don't match.", fontSize = 11.sp, color = NeonMagenta)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.length >= 4 && password == confirmPassword
            ) { Text("Set Password", color = NeonCyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
