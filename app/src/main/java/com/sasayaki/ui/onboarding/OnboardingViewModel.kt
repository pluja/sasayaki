package com.sasayaki.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.di.ApplicationScope
import com.sasayaki.service.BubbleServiceController
import com.sasayaki.ui.settings.ProviderDraft
import com.sasayaki.ui.settings.ProviderEditorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val profileRepository: ProfileRepository,
    private val preferencesDataStore: PreferencesDataStore,
    private val serviceController: BubbleServiceController,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    val draft = ProviderDraft(providerRepository, viewModelScope)
    val providerState: StateFlow<ProviderEditorUiState> = draft.state
    val serviceRunning: StateFlow<Boolean> = serviceController.running
    private var savedProviderId: Long? = null

    private var saving = false

    /** saveProvider saves the provider and turns cleanup on for the active profile when the provider offers it. */
    fun saveProvider(onSaved: () -> Unit) {
        if (saving || !draft.validateUrl()) return
        saving = true
        viewModelScope.launch {
            val provider = draft.toProvider().copy(id = savedProviderId ?: 0)
            savedProviderId = providerRepository.save(provider)
            if (provider.cleanupModel != null) {
                val active = profileRepository.getActiveProfile()
                profileRepository.save(active.copy(llmEnabled = true))
            }
            saving = false
            onSaved()
        }
    }

    fun startBubble() = serviceController.start()

    fun complete(onDone: () -> Unit) {
        applicationScope.launch { preferencesDataStore.setOnboardingCompleted() }
        onDone()
    }
}
