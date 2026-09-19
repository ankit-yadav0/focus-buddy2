package com.example.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import com.example.receiver.MyDeviceAdminReceiver

val noOpTextToolbar = object : TextToolbar {
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // No-op: prevent standard Copy/Paste toolbar overlay
    }
    override fun hide() {}
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallReflectionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComp = remember { ComponentName(context, MyDeviceAdminReceiver::class.java) }
    var adminRemovedPendingUninstall by remember { mutableStateOf(false) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            adminRemovedPendingUninstall = true
        }
    }

    var textState by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val words = remember(textState) {
        textState.split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    val wordCount = words.size
    val isReady = wordCount >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Reflection Gate", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "A Moment of Reflection Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please write a 600-word reflection explaining your decision to uninstall Focus Buddy. Hasty deinstallation often ruins focus routines. Typing must be done manually — copy-paste is disabled.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Text(
                text = "Live Word Count: $wordCount / 600 words",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isReady) Color(0xFF3DFFC4) else MaterialTheme.colorScheme.error
            )

            LinearProgressIndicator(
                progress = { (wordCount.toFloat() / 600f).coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .testTag("word_count_progress"),
                color = if (isReady) Color(0xFF3DFFC4) else MaterialTheme.colorScheme.error,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (adminRemovedPendingUninstall) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_cancelled_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Uninstall was cancelled",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "The app is still installed, but Device Admin protection was turned off as part of that attempt. Please reactivate protection to maintain your focus routines.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                                        putExtra(
                                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                            "Enabling Device Admin blocks uninstallation of Focus Buddy."
                                        )
                                    }
                                    context.startActivity(intent)
                                    adminRemovedPendingUninstall = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error enabling admin: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("reactivate_protection_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3DFFC4),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Reactivate Protection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalTextToolbar provides noOpTextToolbar) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { newVal ->
                        val delta = newVal.length - textState.length
                        if (delta > 15) {
                            errorMessage = "Copy/paste detected! Writing must be manual."
                        } else {
                            if (errorMessage != null && delta >= 0) {
                                errorMessage = null
                            }
                            textState = newVal
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .testTag("reflection_text_field"),
                    placeholder = {
                        Text(
                            "Express your thoughts here...",
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = if (isReady) Color(0xFF3DFFC4) else Color.White.copy(alpha = 0.3f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Button(
                onClick = {
                    if (isReady) {
                        try {
                            if (dpm.isAdminActive(adminComp)) {
                                dpm.removeActiveAdmin(adminComp)
                            }
                            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                            uninstallLauncher.launch(uninstallIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error executing uninstallation: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_uninstall_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "Confirm Deactivation & Uninstall",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
