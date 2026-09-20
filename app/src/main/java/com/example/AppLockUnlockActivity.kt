package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import com.example.service.FocusAccessibilityService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class AppLockUnlockActivity : ComponentActivity() {

    // Held in mutableStateOf (not local vals) because this activity is
    // launchMode="singleTask": if it's still alive in the background (e.g. the
    // user pressed the physical Home button instead of the in-screen Cancel)
    // and a DIFFERENT locked app is opened next, Android reuses this same
    // instance via onNewIntent() instead of a fresh onCreate(). Without this,
    // the screen kept showing the PIN prompt for the first app and, on a
    // correct PIN, unlocked that stale package instead of the one the user
    // actually just opened.
    private var lockedPackageState by mutableStateOf("")
    private var appLabelState by mutableStateOf("")

    override fun onResume() {
        super.onResume()
        FocusAccessibilityService.instance?.dismissInstantOverlay()
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

        // Same reasoning as BlockActivity: the only deliberate way out of this screen
        // is "Cancel" (go home) or a correct PIN. A back press/gesture must not just
        // pop this activity and reveal the still-locked app underneath.
        onBackPressedDispatcher.addCallback(this) {
            goHomeAndFinish()
        }

        applyIntent(intent)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = application as FocusApplication
                    val scope = rememberCoroutineScope()

                    PinUnlockScreen(
                        appName = appLabelState,
                        onCancel = { goHomeAndFinish() },
                        onVerifyPin = { pin, onResult ->
                            scope.launch {
                                val correct = app.repository.let { repo ->
                                    val stored = repo.getSetting("app_lock_pin_hash")
                                    stored != null && stored == sha256(pin)
                                }
                                if (correct) {
                                    FocusAccessibilityService.instance?.grantAppUnlock(lockedPackageState)
                                    finish()
                                } else {
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

private fun sha256(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

@Composable
fun PinUnlockScreen(
    appName: String,
    onCancel: () -> Unit,
    onVerifyPin: (pin: String, onResult: (Boolean) -> Unit) -> Unit
) {
    // Keyed on appName: if onNewIntent() swaps the target app while this screen
    // is still alive (singleTask reuse), any digits typed for the previous app
    // must not carry over.
    var pin by remember(appName) { mutableStateOf("") }
    var showError by remember(appName) { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "App Lock",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Enter PIN",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "to open $appName",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.15f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showError) {
            Text(
                text = "Wrong PIN, try again",
                color = Color(0xFFFF5252),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Spacer(modifier = Modifier.height(18.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "back")
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
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
                                            tint = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                key.isNotEmpty() -> {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.06f))
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

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}
