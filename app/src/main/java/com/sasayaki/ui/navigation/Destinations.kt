package com.sasayaki.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingDestination

@Serializable
data object HomeDestination

@Serializable
data object HistoryDestination

@Serializable
data object ProfilesDestination

/** ProfileEditorDestination always points at a saved profile; new ones are created first. */
@Serializable
data class ProfileEditorDestination(val id: Long)

@Serializable
data object SettingsDestination

@Serializable
data object ProvidersDestination

/** ProviderEditorDestination with [id] 0 opens the editor for a new provider. */
@Serializable
data class ProviderEditorDestination(val id: Long)

@Serializable
data object BubbleSettingsDestination

@Serializable
data object RecordingSettingsDestination

@Serializable
data object AppsSettingsDestination

@Serializable
data object HistorySettingsDestination

@Serializable
data object AppearanceSettingsDestination

@Serializable
data object PermissionsDestination

@Serializable
data object ReplacementRulesDestination

@Serializable
data object VocabularyDestination

@Serializable
data object PromptsDestination

@Serializable
data object LicensesDestination
