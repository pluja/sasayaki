package com.sasayaki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sasayaki.R
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.domain.provider.Provider
import com.sasayaki.ui.components.EmptyState
import com.sasayaki.ui.components.ItemIcon
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProvidersViewModel @Inject constructor(providerRepository: ProviderRepository) : ViewModel() {
    val providers: StateFlow<List<Provider>?> = providerRepository.providers
        .map<List<Provider>, List<Provider>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun ProvidersRoute(onOpenProvider: (Long) -> Unit, onBack: () -> Unit, viewModel: ProvidersViewModel = hiltViewModel()) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    ProvidersScreen(providers, onOpenProvider, onBack)
}

@Composable
fun ProvidersScreen(providers: List<Provider>?, onOpenProvider: (Long) -> Unit, onBack: () -> Unit) {
    val intro = stringResource(R.string.provider_list_intro)
    SettingsPage(
        title = stringResource(R.string.provider_list_title),
        onBack = onBack,
        bottomInset = Spacing.xxxl * 2,
        floatingActionButton = {
            if (!providers.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenProvider(0) },
                    icon = { Icon(SasayakiIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.provider_add)) }
                )
            }
        }
    ) {
        pageIntro(intro)
        when {
            providers == null -> Unit

            providers.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = SasayakiIcons.Dns,
                    title = stringResource(R.string.provider_list_empty_title),
                    body = stringResource(R.string.provider_list_empty_body),
                    action = { Button(onClick = { onOpenProvider(0) }) { Text(stringResource(R.string.provider_add)) } }
                )
            }

            else -> item(key = "list") {
                SegmentedGroup(
                    items = providers.map { provider ->
                        { shapes ->
                            SegmentedListItem(
                                onClick = { onOpenProvider(provider.id) },
                                shapes = shapes,
                                colors = segmentedItemColors(),
                                leadingContent = { ItemIcon(SasayakiIcons.Dns) },
                                trailingContent = { Icon(SasayakiIcons.ChevronRight, contentDescription = null) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                        providerUses(provider).forEach { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            ) {
                                Text(provider.name)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun providerUses(provider: Provider): List<String> = listOfNotNull(
    provider.speechModel?.let { stringResource(R.string.provider_uses_speech, it) },
    provider.cleanupModel?.let { stringResource(R.string.provider_uses_cleanup, it) }
).ifEmpty { listOf(stringResource(R.string.provider_no_default_models)) }
