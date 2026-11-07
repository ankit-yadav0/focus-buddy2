package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.FocusAccessibilityService
import com.example.ui.components.NeonCircularLockEmblem
import com.example.ui.components.NeonLaserBackground
import com.example.ui.components.NeonOutlinedPillButton
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class AppLockUnlockActivity : ComponentActivity() {

    private var lockedPackageState by mutableStateOf("")
    private var appLabelState by mutableStateOf("")
    private var lockoutMessageState by mutableStateOf<String?>(null)
    private var promptSeqState by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        FocusAccessibilityService.instance?.dismissInstantOverlay()
        val left = PinSecurity.remainingLockMs(this)
        lockoutMessageState = if (left > 0) PinSecurity.lockMessage(left) else null
    }

    private fun goHomeAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun resolveAppLabel(lockedPackage: String): String {
        val pm = packageManager
        return try {
            val appInfo = pm.getApplicationInfo(lockedPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            lockedPackage.substringAfterLast(".")
        }
    }

    private fun applyIntent(newIntent: Intent) {
        val lockedPackage = newIntent.getStringExtra("LOCKED_PACKAGE") ?: ""
        lockedPackageState = lockedPackage
        appLabelState = resolveAppLabel(lockedPackage)
        val left = PinSecurity.remainingLockMs(this)
        lockoutMessageState = if (left > 0) PinSecurity.lockMessage(left) else null
        promptSeqState++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this) {
            goHomeAndFinish()
        }

        applyIntent(intent)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NeonBlack
                ) {
                    val app = application as FocusApplication
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(lockedPackageState, promptSeqState) {
                        val stored = app.repository.getSetting("app_lock_pin_hash")
                        if (stored.isNullOrBlank()) {
                            android.util.Log.w("AppLockUnlockActivity", "No PIN configured in repository; unlocking to prevent lockout.")
                            FocusAccessibilityService.instance?.grantAppUnlock(lockedPackageState)
                            finish()
                        }
                    }

                    PinUnlockScreen(
                        appName = appLabelState,
                        promptKey = promptSeqState,
                        onCancel = { goHomeAndFinish() },
                        lockoutMessage = lockoutMessageState,
                        onVerifyPin = { pin, onResult ->
                            scope.launch {
                                val ctx = this@AppLockUnlockActivity
                                val lockMs = PinSecurity.remainingLockMs(ctx)
                                if (lockMs > 0) {
                                    lockoutMessageState = PinSecurity.lockMessage(lockMs)
                                    onResult(false)
                                    return@launch
                                }
                                val stored = app.repository.getSetting("app_lock_pin_hash")
                                if (stored.isNullOrBlank()) {
                                    android.util.Log.w("AppLockUnlockActivity", "No PIN configured; auto-unlocking.")
                                    FocusAccessibilityService.instance?.grantAppUnlock(lockedPackageState)
                                    finish()
                                    return@launch
                                }
                                val correct = PinSecurity.verify(pin, stored)
                                if (correct) {
                                    if (PinSecurity.isLegacy(stored)) {
                                        app.repository.saveSetting("app_lock_pin_hash", PinSecurity.hash(pin))
                                    }
                                    PinSecurity.recordSuccess(ctx)
                                    lockoutMessageState = null
                                    FocusAccessibilityService.instance?.grantAppUnlock(lockedPackageState)
                                    finish()
                                } else {
                                    PinSecurity.recordFailure(ctx)
                                    val left = PinSecurity.remainingLockMs(ctx)
                                    lockoutMessageState = if (left > 0) PinSecurity.lockMessage(left) else null
                                    onResult(false)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PinUnlockScreen(
    appName: String,
    lockoutMessage: String? = null,
    promptKey: Int = 0,
    onCancel: () -> Unit,
    onVerifyPin: (pin: String, onResult: (Boolean) -> Unit) -> Unit
) {
    var pin by remember(appName, promptKey) { mutableStateOf("") }
    var showError by remember(appName, promptKey) { mutableStateOf(false) }

    fun onDigit(d: String) {
        if (pin.length < 6) {
            showError = false
            pin += d
            if (pin.length == 6) {
                onVerifyPin(pin) { correct ->
                    if (!correct) {
                        showError = true
                        pin = ""
                    }
                }
            }
        }
    }

    fun onBackspace() {
        showError = false
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    NeonLaserBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NeonCircularLockEmblem(size = 112.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "App Locked",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonMagenta,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter PIN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "to open $appName",
                fontSize = 13.5.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) {
                                    Brush.linearGradient(listOf(NeonCyan, NeonMagenta))
                                } else {
                                    Brush.linearGradient(listOf(NeonSurface, NeonSurface))
                                }
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (filled) NeonCyan else NeonViolet.copy(alpha = 0.45f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (showError || lockoutMessage != null) {
                Text(
                    text = lockoutMessage ?: "Wrong PIN, try again",
                    color = NeonMagenta,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "back")
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier.size(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    key == "back" -> {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .clickable { onBackspace() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Backspace",
                                                tint = NeonCyan
                                            )
                                        }
                                    }
                                    key.isNotEmpty() -> {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(NeonSurface)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.linearGradient(
                                                        listOf(NeonCyan.copy(alpha = 0.4f), NeonMagenta.copy(alpha = 0.4f))
                                                    ),
                                                    shape = CircleShape
                                                )
                                                .clickable { onDigit(key) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            NeonOutlinedPillButton(
                text = "Go Back",
                onClick = onCancel,
                modifier = Modifier.width(180.dp),
                height = 48.dp
            )
        }
    }
}
