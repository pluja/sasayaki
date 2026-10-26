package com.sasayaki.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.preferences.BubbleSize
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.preferences.ThemeMode
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.data.repository.HistoryRepository
import com.sasayaki.data.repository.ProcessingRepository
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.domain.provider.Provider
import com.sasayaki.service.BubbleServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val loaded: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val providers: List<Provider> = emptyList(),
    val ruleCount: Int = 0,
    val customPromptCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val historyRepository: HistoryRepository,
    private val bubbleServiceController: BubbleServiceController,
    providerRepository: ProviderRepository,
    processingRepository: ProcessingRepository
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesDataStore.preferences,
        providerRepository.providers,
        processingRepository.rules,
        processingRepository.prompts
    ) { prefs, providers, rules, prompts ->
        SettingsUiState(
            loaded = true,
            preferences = prefs,
            providers = providers,
            ruleCount = rules.size,
            customPromptCount = prompts.count { !it.builtIn }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setSilenceThreshold(ms: Long) = persist { preferencesDataStore.setSilenceThresholdMs(ms) }

    fun setPauseOtherAudio(enabled: Boolean) = persist { preferencesDataStore.setPauseOtherAudio(enabled) }

    fun setKeepListeningWhenKeyboardHides(enabled: Boolean) = persist { preferencesDataStore.setKeepListeningWhenKeyboardHides(enabled) }

    fun setVibrateOnRecord(enabled: Boolean) = persist { preferencesDataStore.setVibrateOnRecord(enabled) }

    fun setAutoClipboard(enabled: Boolean) = persist { preferencesDataStore.setAutoClipboard(enabled) }

    fun setStartOnBoot(enabled: Boolean) = persist { preferencesDataStore.setStartOnBoot(enabled) }

    fun setHistoryEnabled(enabled: Boolean) = persist { preferencesDataStore.setHistoryEnabled(enabled) }

    fun setHistoryRetention(limit: Int) = persist {
        preferencesDataStore.setHistoryRetentionLimit(limit)
        historyRepository.prune()
    }

    fun setKeepStatsWithoutHistory(enabled: Boolean) = persist { preferencesDataStore.setKeepStatsWithoutHistory(enabled) }

    fun setThemeMode(mode: ThemeMode) = persist { preferencesDataStore.setThemeMode(mode) }

    fun setWallpaperColors(enabled: Boolean) = persist { preferencesDataStore.setWallpaperColors(enabled) }

    val bubbleRunning: StateFlow<Boolean> = bubbleServiceController.running

    fun startBubble() = bubbleServiceController.start()

    fun stopBubble() = bubbleServiceController.stop()

    fun setCarryKeyboardDictation(enabled: Boolean) = persist { preferencesDataStore.setCarryKeyboardDictation(enabled) }

    fun setBubbleSize(size: BubbleSize) = persist { preferencesDataStore.setBubbleSize(size) }

    fun setControlsSize(size: BubbleSize) = persist { preferencesDataStore.setControlsSize(size) }

    fun setBubbleTranslucent(enabled: Boolean) = persist { preferencesDataStore.setBubbleTranslucent(enabled) }

    fun setBubbleMonochrome(enabled: Boolean) = persist { preferencesDataStore.setBubbleMonochrome(enabled) }

    fun setRememberProfilePerApp(enabled: Boolean) = persist { preferencesDataStore.setRememberProfilePerApp(enabled) }

    fun setSkipCleanupInAiChats(enabled: Boolean) = persist { preferencesDataStore.setSkipCleanupInAiChats(enabled) }

    fun forgetAppProfiles() = persist { preferencesDataStore.forgetAppProfiles() }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
