package com.alarmx.smartalarm.ui.reliability

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val powerManager = context.getSystemService(PowerManager::class.java)
    var notificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationGranted = it }

    val exactOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else true
    val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reliability guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                Text("Checklist", style = MaterialTheme.typography.titleLarge)
                Text("Some manufacturers still restrict background work after these steps. AlarmX cannot promise delivery on every device.")
            }
            CheckItem(
                title = "Notifications",
                ok = notificationGranted,
                body = "Needed to show the full-screen alarm on a locked phone.",
                action = "Allow notifications"
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            CheckItem(
                title = "Exact alarms",
                ok = exactOk,
                body = "Without this, Android may delay the alarm. AlarmX will not hide that by using a fake timer.",
                action = "Open exact alarm setting"
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
            }
            CheckItem(
                title = "Battery optimization",
                ok = ignoringBattery,
                body = "Unrestricted battery use helps alarms fire after the app is unused.",
                action = "Open battery settings"
            ) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            SectionCard {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open app settings") }
                Text(
                    "If alarms still fail, look for OEM autostart, locked apps, or sleeping apps menus on Xiaomi, Huawei, Oppo, Vivo, and Samsung devices.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CheckItem(title: String, ok: Boolean, body: String, action: String, onClick: () -> Unit) {
    SectionCard {
        Text(title + if (ok) " · ready" else " · needs attention", style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ok) {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}
