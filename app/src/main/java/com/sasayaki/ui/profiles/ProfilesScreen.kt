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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    ProfilesScreen(
        state = state,
        onActivate = viewModel::activate,
        onOpenProfile = onOpenProfile,
        onCreate = { viewModel.create(onOpenProfile) }
    )
}

@Composable
fun ProfilesScreen(state: ProfilesUiState, onActivate: (Long) -> Unit, onOpenProfile: (Long) -> Unit, onCreate: () -> Unit) {
    SettingsPage(
        title = "Profiles",
        onBack = null,
        bottomInset = Spacing.xxxl * 2,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(SasayakiIcons.Add, contentDescription = null) },
                text = { Text("New profile") }
            )
        }
    ) {
        pageIntro("Each profile sets a language, models and how your words are cleaned up. Switch between them from the bubble.")
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
                                            "Active",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        TextButton(onClick = { onActivate(profile.id) }) { Text("Use") }
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

fun profileSummary(profile: Profile, providers: List<Provider>): List<String> {
    val language = profile.language?.let { languageName(it) } ?: "Any language"
    val cleanup = if (profile.llmEnabled) "Cleanup on" else "No cleanup"
    val speech = when (val resolution = resolveEndpoint(providers, Purpose.SPEECH, profile.speechProviderId, profile.asrModel)) {
        is Resolution.Ready -> resolution.endpoint.model.substringAfterLast('/')
        else -> "No speech model yet"
    }
    return listOf("$language · $cleanup", speech)
}
