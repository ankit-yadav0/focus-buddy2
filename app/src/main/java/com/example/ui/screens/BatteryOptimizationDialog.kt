package com.example.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.TextSecondary

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AlertDialog(
        modifier = modifier.testTag("battery_optimization_dialog"),
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = "Battery optimization icon",
                tint = NeonCyan
            )
        },
        title = {
            Text(
                text = "One more step for reliable scheduling",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        text = {
            Text(
                text = "To ensure that your scheduled Strict Mode starts and stops exactly on time, we need background execution permissions.\n\nOn devices like Realme (and other ColorOS / Oppo systems), aggressive battery saver restrictions can kill background schedulers.\n\nPlease select 'Open Battery Settings', find Focuss Buddy, and configure it to 'Allow background activity' or disable energy-saving optimization.",
                fontSize = 14.sp,
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("open_battery_settings_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = NeonBlack
                ),
                onClick = {
                    launchBatterySettings(context)
                    onDismiss()
                }
            ) {
                Text("Open Battery Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("not_now_battery_button"),
                onClick = onDismiss
            ) {
                Text("Not now", color = TextSecondary)
            }
        }
    )
}

private fun launchBatterySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        val intent = Intent().apply {
            component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
