package com.sasayaki.ui.profiles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.repository.ProcessingRepository
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.TextReplacementRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val rules: List<TextReplacementRule> = emptyList(),
    val prompts: List<PostProcessingPrompt> = emptyList()
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    processingRepository: ProcessingRepository
) : ViewModel() {
    val uiState: StateFlow<ProfilesUiState> = combine(
        profileRepository.profiles,
        processingRepository.rules,
        processingRepository.prompts
    ) { profiles, rules, prompts ->
        ProfilesUiState(profiles = profiles, rules = rules, prompts = prompts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfilesUiState())

    fun save(profile: Profile) {
        viewModelScope.launch {
            val id = profileRepository.save(profile)
            if (profile.isActive) profileRepository.activate(id)
        }
    }

    fun activate(id: Long) {
        viewModelScope.launch { profileRepository.activate(id) }
    }

    fun duplicate(profile: Profile) {
        viewModelScope.launch { profileRepository.duplicate(profile) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { profileRepository.delete(id) }
    }
}

