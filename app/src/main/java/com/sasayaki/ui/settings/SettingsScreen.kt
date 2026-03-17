package com.sasayaki.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val asrTestState by viewModel.asrTestState.collectAsState()
    val llmTestState by viewModel.llmTestState.collectAsState()
    val asrSaved by viewModel.asrSaved.collectAsState()
    val llmSaved by viewModel.llmSaved.collectAsState()

    var asrUrl by remember(prefs.asrBaseUrl) { mutableStateOf(prefs.asrBaseUrl) }
    var asrKey by remember(prefs.asrApiKey) { mutableStateOf(prefs.asrApiKey) }
    var asrModel by remember(prefs.asrModel) { mutableStateOf(prefs.asrModel) }

    var llmUrl by remember(prefs.llmBaseUrl) { mutableStateOf(prefs.llmBaseUrl) }
    var llmKey by remember(prefs.llmApiKey) { mutableStateOf(prefs.llmApiKey) }
    var llmModel by remember(prefs.llmModel) { mutableStateOf(prefs.llmModel) }
    var llmEnabled by remember(prefs.llmEnabled) { mutableStateOf(prefs.llmEnabled) }

    var autoClipboard by remember(prefs.autoClipboard) { mutableStateOf(prefs.autoClipboard) }
    var vibrateOnRecord by remember(prefs.vibrateOnRecord) { mutableStateOf(prefs.vibrateOnRecord) }
    var silenceThreshold by remember(prefs.silenceThresholdMs) { mutableFloatStateOf(prefs.silenceThresholdMs.toFloat()) }
    var historyEnabled by remember(prefs.historyEnabled) { mutableStateOf(prefs.historyEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ASR Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Speech Recognition (ASR)", style = MaterialTheme.typography.titleLarge)
            }
            item {
                OutlinedTextField(
                    value = asrUrl, onValueChange = { asrUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = asrKey, onValueChange = { asrKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
            item {
                OutlinedTextField(
                    value = asrModel, onValueChange = { asrModel = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val saveColor by animateColorAsState(
                        if (asrSaved) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        label = "asrSaveColor"
                    )
                    Button(
                        onClick = { viewModel.saveAsrConfig(asrUrl, asrKey, asrModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = saveColor)
                    ) {
                        if (asrSaved) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Text(" Saved")
                        } else {
                            Text("Save")
                        }
                    }
                    TestButton(
                        state = asrTestState,
                        onClick = { viewModel.testAsrConnection(asrUrl, asrKey, asrModel) },
                        enabled = asrUrl.isNotBlank() && asrKey.isNotBlank()
                    )
                }
            }
            // ASR test error detail
            item {
                TestResultDetail(asrTestState)
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // LLM Section
            item {
                Text("LLM Post-Processing", style = MaterialTheme.typography.titleLarge)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable LLM")
                    Switch(checked = llmEnabled, onCheckedChange = { llmEnabled = it })
                }
            }
            item {
                OutlinedTextField(
                    value = llmUrl, onValueChange = { llmUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = llmEnabled
                )
            }
            item {
                OutlinedTextField(
                    value = llmKey, onValueChange = { llmKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = llmEnabled
                )
            }
            item {
                OutlinedTextField(
                    value = llmModel, onValueChange = { llmModel = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = llmEnabled
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val saveColor by animateColorAsState(
                        if (llmSaved) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        label = "llmSaveColor"
                    )
                    Button(
                        onClick = { viewModel.saveLlmConfig(llmUrl, llmKey, llmModel, llmEnabled) },
                        colors = ButtonDefaults.buttonColors(containerColor = saveColor)
                    ) {
                        if (llmSaved) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Text(" Saved")
                        } else {
                            Text("Save")
                        }
                    }
                    TestButton(
                        state = llmTestState,
                        onClick = { viewModel.testLlmConnection(llmUrl, llmKey, llmModel) },
                        enabled = llmEnabled && llmUrl.isNotBlank() && llmKey.isNotBlank()
                    )
                }
            }
            item {
                TestResultDetail(llmTestState)
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // General Section
            item {
                Text("General", style = MaterialTheme.typography.titleLarge)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-clipboard fallback")
                    Switch(
                        checked = autoClipboard,
                        onCheckedChange = {
                            autoClipboard = it
                            viewModel.saveGeneralSettings(it, vibrateOnRecord, silenceThreshold.toLong(), historyEnabled)
                        }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibrate on record")
                    Switch(
                        checked = vibrateOnRecord,
                        onCheckedChange = {
                            vibrateOnRecord = it
                            viewModel.saveGeneralSettings(autoClipboard, it, silenceThreshold.toLong(), historyEnabled)
                        }
                    )
                }
            }
            item {
                Column {
                    Text("Silence detection: ${"%.1f".format(silenceThreshold / 1000)}s")
                    Slider(
                        value = silenceThreshold,
                        onValueChange = { silenceThreshold = it },
                        onValueChangeFinished = {
                            viewModel.saveGeneralSettings(autoClipboard, vibrateOnRecord, silenceThreshold.toLong(), historyEnabled)
                        },
                        valueRange = 500f..5000f,
                        steps = 8
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save dictation history")
                    Switch(
                        checked = historyEnabled,
                        onCheckedChange = {
                            historyEnabled = it
                            viewModel.saveGeneralSettings(autoClipboard, vibrateOnRecord, silenceThreshold.toLong(), it)
                        }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TestButton(
    state: TestState,
    onClick: () -> Unit,
    enabled: Boolean
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && state !is TestState.Testing
    ) {
        when (state) {
            is TestState.Idle -> Text("Test")
            is TestState.Testing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
            is TestState.Success -> {
                Icon(
                    Icons.Default.Check, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(" OK", color = MaterialTheme.colorScheme.primary)
            }
            is TestState.Error -> {
                Icon(
                    Icons.Default.Close, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(" Fail", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TestResultDetail(state: TestState) {
    when (state) {
        is TestState.Error -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        is TestState.Success -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        else -> {}
    }
}
