package com.sasayaki.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.service.BubbleService
import com.sasayaki.ui.common.FeatureCard
import com.sasayaki.ui.common.PermissionCard
import com.sasayaki.ui.common.PermissionStatus
import com.sasayaki.ui.common.SasayakiScaffold
import com.sasayaki.ui.common.SectionCard
import com.sasayaki.ui.common.StatusPill
import com.sasayaki.ui.common.rememberAccessibilityPermissionState
import com.sasayaki.ui.common.rememberMicrophonePermissionState
import com.sasayaki.ui.common.rememberNotificationPermissionState
import com.sasayaki.ui.common.rememberOverlayPermissionState

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle(initialValue = 0)
    val todayWordCount by viewModel.todayWordCount.collectAsStateWithLifecycle(initialValue = 0)
    val serviceRunning by BubbleService.runningState.collectAsStateWithLifecycle()

    val overlayPermission = rememberOverlayPermissionState()
    val accessibilityPermission = rememberAccessibilityPermissionState()
    val microphonePermission = rememberMicrophonePermissionState()
    val notificationPermission = rememberNotificationPermissionState()

    val setupStatuses = listOf(
        overlayPermission,
        accessibilityPermission,
        microphonePermission,
        notificationPermission
    )
    val missingPermissions = setupStatuses.filterNot(PermissionStatus::granted)
    val serviceReady = overlayPermission.granted && accessibilityPermission.granted && microphonePermission.granted

    SasayakiScaffold { padding ->
        LazyColumn(
            contentPadding = homeContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeHeroCard(
                    serviceRunning = serviceRunning,
                    serviceReady = serviceReady,
                    onToggleService = {
                        when {
                            serviceRunning -> BubbleService.stop(context)
                            !microphonePermission.granted -> microphonePermission.onRequest()
                            !overlayPermission.granted -> overlayPermission.onRequest()
                            !accessibilityPermission.granted -> accessibilityPermission.onRequest()
                            else -> BubbleService.start(context)
                        }
                    }
                )
            }

            item {
                StatsRow(
                    todayCount = todayCount,
                    todayWordCount = todayWordCount
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (!overlayPermission.granted || !accessibilityPermission.granted)
            ) {
                item {
                    RestrictedSettingsCard(
                        onOpenAppInfo = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            if (missingPermissions.isNotEmpty()) {
                item {
                    Text(
                        text = "Finish setup",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(missingPermissions, key = { it.name }) { status ->
                    PermissionCard(status = status)
                }
            }

            item {
                Text(
                    text = "Quick actions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                FeatureCard(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    description = "Tune endpoints, cleanup behavior, and service preferences.",
                    onClick = onNavigateToSettings
                )
            }

            item {
                FeatureCard(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Dictionary",
                    description = "Add names, jargon, and terms that should transcribe cleanly.",
                    onClick = onNavigateToDictionary
                )
            }

            item {
                FeatureCard(
                    icon = Icons.Default.History,
                    title = "History",
                    description = "Review recent dictations, copy them again, or remove them.",
                    onClick = onNavigateToHistory
                )
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    serviceRunning: Boolean,
    serviceReady: Boolean,
    onToggleService: () -> Unit
) {
    val actionColor by animateColorAsState(
        targetValue = if (serviceRunning) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "serviceActionColor"
    )

    SectionCard(
        title = "Fast dictation, right where you type.",
        subtitle = "Keep the floating bubble ready, dictate into any text field, and stay in flow while you work."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = if (serviceRunning) "Service active" else "Service stopped",
                containerColor = if (serviceRunning) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (serviceRunning) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )

            if (!serviceReady && !serviceRunning) {
                StatusPill(
                    label = "Setup needed",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        AnimatedContent(targetState = serviceRunning, label = "serviceCopy") { running ->
            Text(
                text = if (running) {
                    "The bubble is live and ready to insert speech into the focused field."
                } else if (serviceReady) {
                    "Everything is ready. Start the service when you want dictation on standby."
                } else {
                    "Grant microphone, overlay, and direct text insertion so Sasayaki can work across apps."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onToggleService,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = actionColor)
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (serviceRunning) Icons.Default.StopCircle else Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            AnimatedContent(targetState = serviceRunning, label = "serviceButtonLabel") { running ->
                Text(if (running) "Stop dictation service" else "Start dictation service")
            }
        }
    }
}

@Composable
private fun StatsRow(
    todayCount: Int,
    todayWordCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Today",
            value = todayCount.toString(),
            description = if (todayCount == 1) "dictation captured" else "dictations captured",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Words",
            value = todayWordCount.toString(),
            description = "spoken so far",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    SectionCard(
        title = "Restricted settings on Android 13+",
        subtitle = "Sideloaded apps need one extra step before overlay and accessibility permissions can be enabled."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Open App Info, tap the overflow menu, choose Allow restricted settings, then come back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Open App Info")
            }
        }
    }
}

private fun homeContentPadding(padding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 20.dp,
        bottom = padding.calculateBottomPadding() + 28.dp
    )
}
