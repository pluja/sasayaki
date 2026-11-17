package com.sasayaki.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.languageName
import com.sasayaki.domain.provider.Provider
import com.sasayaki.domain.provider.Purpose
import com.sasayaki.domain.provider.Resolution
import com.sasayaki.domain.provider.resolveEndpoint
import com.sasayaki.ui.components.ItemIcon
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

@Composable
fun ProfilesRoute(onOpenProfile: (Long) -> Unit, viewModel: ProfilesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val newProfileName = stringResource(R.string.profile_new_default_name)
    ProfilesScreen(
        state = state,
        onActivate = viewModel::activate,
        onOpenProfile = onOpenProfile,
        onCreate = { viewModel.create(newProfileName, onOpenProfile) }
    )
}

@Composable
fun ProfilesScreen(state: ProfilesUiState, onActivate: (Long) -> Unit, onOpenProfile: (Long) -> Unit, onCreate: () -> Unit) {
    val intro = stringResource(R.string.profile_list_intro)
    SettingsPage(
        title = stringResource(R.string.profile_list_title),
        onBack = null,
        bottomInset = Spacing.xxxl * 2,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(SasayakiIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.profile_new)) }
            )
        }
    ) {
        pageIntro(intro)
        if (state.loaded) {
            item(key = "profiles") {
                SegmentedGroup(
                    items = state.profiles.map { profile ->
                        { shapes ->
                            SegmentedListItem(
                                onClick = { onOpenProfile(profile.id) },
                                shapes = shapes,
                                colors = segmentedItemColors(),
                                leadingContent = {
                                    if (profile.isActive) {
                                        ItemIcon(
                                            SasayakiIcons.Check,
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        ItemIcon(SasayakiIcons.Tune)
                                    }
                                },
                                trailingContent = {
                                    if (profile.isActive) {
                                        Text(
                                            stringResource(R.string.profile_active),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        TextButton(onClick = { onActivate(profile.id) }) { Text(stringResource(R.string.profile_use)) }
                                    }
                                },
                                supportingContent = {
                                    Column {
                                        profileSummary(profile, state.providers).forEach {
                                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            ) {
                                Text(profile.name)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun profileSummary(profile: Profile, providers: List<Provider>): List<String> {
    val language = profile.language?.let { languageName(it) } ?: stringResource(R.string.profile_any_language)
    val cleanup = stringResource(if (profile.llmEnabled) R.string.profile_cleanup_on else R.string.profile_no_cleanup)
    val speech = when (val resolution = resolveEndpoint(providers, Purpose.SPEECH, profile.speechProviderId, profile.asrModel)) {
        is Resolution.Ready -> resolution.endpoint.model.substringAfterLast('/')
        else -> stringResource(R.string.profile_no_speech_model)
    }
    return listOf(stringResource(R.string.profile_summary_line, language, cleanup), speech)
}
