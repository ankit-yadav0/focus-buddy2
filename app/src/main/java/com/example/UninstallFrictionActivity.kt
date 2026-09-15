package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class UninstallFrictionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) { // Force Dark Theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UninstallFrictionScreen(
                        onCancel = {
                            finish()
                        },
                        onProceedToUninstall = {
                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.parse("package:" + packageName)
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UninstallFrictionScreen(
    onCancel: () -> Unit,
    onProceedToUninstall: () -> Unit
) {
    // Standard back handler to allow user to exit/cancel
    BackHandler {
        onCancel()
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // Word count = trim the text, split on whitespace regex, count non-blank tokens
    val wordCount = remember(textFieldValue.text) {
        val trimmed = textFieldValue.text.trim()
        if (trimmed.isEmpty()) {
            0
        } else {
            trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        }
    }

    val originalTextToolbar = LocalTextToolbar.current
    val copyOnlyTextToolbar = remember(originalTextToolbar) {
        object : TextToolbar {
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                // Remove the paste option by passing null for onPasteRequested
                originalTextToolbar.showMenu(
                    rect = rect,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = null,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }

            override fun hide() {
                originalTextToolbar.hide()
            }

            override val status: TextToolbarStatus
                get() = originalTextToolbar.status
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20), // Deep rich space theme background matching BlockActivity
                        Color(0xFF15102A),
                        Color(0xFF0A0714)
                    )
                )
            )
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Banner / Status
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2C1D4D).copy(alpha = 0.4f))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info Icon",
                    tint = Color(0xFFB39DDB),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "UNINSTALL FRICTION GUARD",
                    color = Color(0xFFB39DDB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // Center Scrollable Box for Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Before you go...",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Write at least 400 words about why you're uninstalling Focus Buddy and what you were hoping to achieve with it. Use this space for a mindful reflection on your productivity journey.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Word counter progress card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x15FFFFFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REFLECTION PROGRESS",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$wordCount / 400 words",
                        color = if (wordCount >= 400) Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (wordCount / 400f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (wordCount >= 400) Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }

            // Input field with custom local toolbar
            CompositionLocalProvider(LocalTextToolbar provides copyOnlyTextToolbar) {
                TextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val oldTextLength = textFieldValue.text.length
                        val newTextLength = newValue.text.length
                        // Rejects pastes of more than 15 characters at once
                        if (newTextLength - oldTextLength > 15) {
                            // Reject by keeping current value
                        } else {
                            textFieldValue = newValue
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x0AFFFFFF),
                        unfocusedContainerColor = Color(0x05FFFFFF),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = {
                        Text(
                            text = "Write your reflections here...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                )
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onProceedToUninstall,
                enabled = wordCount >= 400,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF81C784),
                    contentColor = Color(0xFF0A0714),
                    disabledContainerColor = Color(0x1F81C784),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Continue to Uninstall",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Cancel & Keep Focus Buddy",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
