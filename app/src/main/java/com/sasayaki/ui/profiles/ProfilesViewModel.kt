package com.sasayaki.ui.profiles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.provider.Provider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ProfilesUiState(
    val loaded: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val providers: List<Provider> = emptyList()
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(private val profileRepository: ProfileRepository, providerRepository: ProviderRepository) :
    ViewModel() {
    val uiState: StateFlow<ProfilesUiState> = combine(
        profileRepository.profiles,
        providerRepository.providers
    ) { profiles, providers ->
        ProfilesUiState(loaded = true, profiles = profiles, providers = providers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfilesUiState())

    fun activate(id: Long) {
        viewModelScope.launch { profileRepository.activate(id) }
    }

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(profileRepository.save(Profile(name = name))) }
    }
}
