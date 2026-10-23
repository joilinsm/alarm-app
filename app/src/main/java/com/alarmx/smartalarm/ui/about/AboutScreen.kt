package com.alarmx.smartalarm.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.BuildConfig
import com.alarmx.smartalarm.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About and privacy") },
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
                Text("AlarmX", style = MaterialTheme.typography.headlineMedium)
                Text("Smart Alarm Booster")
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("Package ${BuildConfig.APPLICATION_ID}")
            }
            SectionCard {
                Text("Privacy", style = MaterialTheme.typography.titleLarge)
                Text("AlarmX works offline. It does not include ads, analytics, accounts, or a backend. Alarm data, imported photos, and imported sounds stay on this device.")
            }
            SectionCard {
                Text("Hearing and loudness", style = MaterialTheme.typography.titleLarge)
                Text("Software gain cannot exceed the speaker’s physical maximum. High volume can distort and may damage hearing. Use amplification only when you understand that risk.")
            }
            SectionCard {
                Text("Sounds and wallpapers", style = MaterialTheme.typography.titleLarge)
                Text("Bundled tones are original short synthesizer loops generated for this app. Bundled wallpapers are original color gradients. Imported media remains your responsibility.")
            }
        }
    }
}
