package com.sasayaki.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val recentDictations by viewModel.recentDictations.collectAsState(initial = emptyList())

    // Re-check permissions every time the activity resumes (user comes back from settings)
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

    // These recompute when refreshTick changes
    val overlayGranted = remember(refreshTick) { checkOverlayPermission(context) }
    val accessibilityGranted = remember(refreshTick) { checkAccessibilityPermission(context) }
    val serviceRunning = remember(refreshTick) { BubbleService.isRunning }

    val micPermission = rememberMicrophonePermissionState()
    val notifPermission = rememberNotificationPermissionState()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Sasayaki",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Service toggle with color coding
            item {
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
                        containerColor = if (running)
                            Color(0xFF4CAF50) // green
                        else
                            MaterialTheme.colorScheme.error // red
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
                item {
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
            item {
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

            // Permissions
            item {
                Text("Permissions", style = MaterialTheme.typography.titleLarge)
            }
            item {
                PermissionCard(PermissionStatus("Overlay", overlayGranted) { requestOverlayPermission(context) })
            }
            item {
                PermissionCard(PermissionStatus("Accessibility", accessibilityGranted) { requestAccessibilityPermission(context) })
            }
            item { PermissionCard(micPermission) }
            item { PermissionCard(notifPermission) }

            // Navigation
            item {
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

            // Recent dictations
            item {
                Text("Recent Dictations", style = MaterialTheme.typography.titleLarge)
            }
            val recent = recentDictations.take(5)
            if (recent.isEmpty()) {
                item {
                    Text(
                        "No dictations yet. Start the service and tap the bubble to dictate!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(recent) { dictation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = dictation.text.take(100) + if (dictation.text.length > 100) "..." else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${dictation.wordCount} words",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dictation.timestamp)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
