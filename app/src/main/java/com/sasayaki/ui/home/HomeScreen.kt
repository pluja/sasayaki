package com.sasayaki.ui.home

import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sasayaki.service.BubbleService
import com.sasayaki.ui.common.PermissionCard
import com.sasayaki.ui.common.PermissionStatus
import com.sasayaki.ui.common.checkAccessibilityPermission
import com.sasayaki.ui.common.checkOverlayPermission
import com.sasayaki.ui.common.rememberMicrophonePermissionState
import com.sasayaki.ui.common.rememberNotificationPermissionState
import com.sasayaki.ui.common.requestAccessibilityPermission
import com.sasayaki.ui.common.requestOverlayPermission

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val todayCount by viewModel.todayCount.collectAsState(initial = 0)
    val todayWordCount by viewModel.todayWordCount.collectAsState(initial = 0)

    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayGranted = remember(refreshTick) { checkOverlayPermission(context) }
    val accessibilityGranted = remember(refreshTick) { checkAccessibilityPermission(context) }
    val serviceRunning = remember(refreshTick) { BubbleService.isRunning }

    val micPermission = rememberMicrophonePermissionState()
    val notifPermission = rememberNotificationPermissionState()

    val allPermissionsGranted = overlayGranted && accessibilityGranted && micPermission.granted && notifPermission.granted

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "title") {
                Text(
                    text = "Sasayaki",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "service_toggle") {
                var running by remember(serviceRunning) { mutableStateOf(serviceRunning) }
                Button(
                    onClick = {
                        if (running) {
                            BubbleService.stop(context)
                            running = false
                        } else {
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Please grant overlay permission first", Toast.LENGTH_LONG).show()
                            } else {
                                BubbleService.start(context)
                                running = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        if (running) "Dictation Service Running" else "Start Dictation Service",
                        color = Color.White
                    )
                }
            }

            // Restricted settings hint for sideloaded apps (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!overlayGranted || !accessibilityGranted)) {
                item(key = "sideload_hint") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Sideloaded App Setup",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Since this app was installed outside the Play Store, " +
                                        "Android restricts overlay and accessibility permissions. " +
                                        "To enable them:\n\n" +
                                        "1. Tap the button below to open App Info\n" +
                                        "2. Tap the menu (three dots) in the top right\n" +
                                        "3. Select \"Allow restricted settings\"\n" +
                                        "4. Return here and grant the permissions",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }) {
                                Text("Open App Info")
                            }
                        }
                    }
                }
            }

            // Stats
            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Today", style = MaterialTheme.typography.labelLarge)
                            Text("$todayCount dictations", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Words", style = MaterialTheme.typography.labelLarge)
                            Text("$todayWordCount", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }

            // Permissions - only show if any are missing
            if (!allPermissionsGranted) {
                item(key = "perm_header") {
                    Text("Permissions", style = MaterialTheme.typography.titleLarge)
                }
                if (!overlayGranted) {
                    item(key = "perm_overlay") {
                        PermissionCard(PermissionStatus("Overlay", false) { requestOverlayPermission(context) })
                    }
                }
                if (!accessibilityGranted) {
                    item(key = "perm_accessibility") {
                        PermissionCard(PermissionStatus("Accessibility", false) { requestAccessibilityPermission(context) })
                    }
                }
                if (!micPermission.granted) {
                    item(key = "perm_mic") { PermissionCard(micPermission) }
                }
                if (!notifPermission.granted) {
                    item(key = "perm_notif") { PermissionCard(notifPermission) }
                }
            }

            // Navigation
            item(key = "nav") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onNavigateToSettings, modifier = Modifier.weight(1f)) {
                        Text("Settings")
                    }
                    OutlinedButton(onClick = onNavigateToDictionary, modifier = Modifier.weight(1f)) {
                        Text("Dictionary")
                    }
                    OutlinedButton(onClick = onNavigateToHistory, modifier = Modifier.weight(1f)) {
                        Text("History")
                    }
                }
            }
        }
    }
}
