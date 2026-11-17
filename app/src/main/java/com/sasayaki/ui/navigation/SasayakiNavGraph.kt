package com.sasayaki.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sasayaki.ui.history.HistoryRoute
import com.sasayaki.ui.home.HomeRoute
import com.sasayaki.ui.onboarding.OnboardingRoute
import com.sasayaki.ui.profiles.ProfileEditorNavigation
import com.sasayaki.ui.profiles.ProfileEditorRoute
import com.sasayaki.ui.profiles.ProfilesRoute
import com.sasayaki.ui.settings.AppearanceSettingsRoute
import com.sasayaki.ui.settings.AppsSettingsRoute
import com.sasayaki.ui.settings.BubbleSettingsRoute
import com.sasayaki.ui.settings.HistorySettingsRoute
import com.sasayaki.ui.settings.LicensesRoute
import com.sasayaki.ui.settings.PermissionsRoute
import com.sasayaki.ui.settings.PromptsRoute
import com.sasayaki.ui.settings.ProviderEditorRoute
import com.sasayaki.ui.settings.ProvidersRoute
import com.sasayaki.ui.settings.RecordingSettingsRoute
import com.sasayaki.ui.settings.ReplacementRulesRoute
import com.sasayaki.ui.settings.SettingsDestinations
import com.sasayaki.ui.settings.SettingsRoute
import com.sasayaki.ui.settings.VocabularyRoute

@Composable
fun SasayakiNavGraph(startDestination: Any) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigationBar = currentDestination == null || currentDestination.topLevelDestination() != null
    val transitions = rememberNavTransitions()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showNavigationBar,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                ShortNavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.isIn(destination)
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
            predictivePopEnterTransition = transitions.predictivePopEnter,
            predictivePopExitTransition = transitions.predictivePopExit
        ) {
            composable<OnboardingDestination> {
                OnboardingRoute(
                    onFinished = {
                        navController.navigate(HomeDestination) {
                            popUpTo<OnboardingDestination> { inclusive = true }
                        }
                    }
                )
            }
            composable<HomeDestination> {
                HomeRoute(
                    onOpenHistory = { navController.navigateToTopLevel(TopLevelDestination.HISTORY) },
                    onOpenDictation = { id -> navController.navigateToDictation(id) },
                    onOpenProfiles = { navController.navigateToTopLevel(TopLevelDestination.PROFILES) },
                    onOpenProviders = { navController.navigate(ProvidersDestination) }
                )
            }
            composable<HistoryDestination> {
                HistoryRoute()
            }
            composable<ProfilesDestination> {
                ProfilesRoute(onOpenProfile = { id -> navController.navigate(ProfileEditorDestination(id)) })
            }
            composable<ProfileEditorDestination> {
                ProfileEditorRoute(
                    navigation = ProfileEditorNavigation(
                        onBack = dropUnlessResumed { navController.popBackStack() },
                        onOpenProfile = { id ->
                            navController.navigate(ProfileEditorDestination(id)) {
                                popUpTo<ProfileEditorDestination> { inclusive = true }
                            }
                        },
                        onManageProviders = { navController.navigate(ProvidersDestination) },
                        onManagePrompts = { navController.navigate(PromptsDestination) },
                        onManageRules = { navController.navigate(ReplacementRulesDestination) }
                    )
                )
            }
            composable<SettingsDestination> {
                SettingsRoute(
                    destinations = SettingsDestinations(
                        onProviders = { navController.navigate(ProvidersDestination) },
                        onBubble = { navController.navigate(BubbleSettingsDestination) },
                        onRecording = { navController.navigate(RecordingSettingsDestination) },
                        onApps = { navController.navigate(AppsSettingsDestination) },
                        onRules = { navController.navigate(ReplacementRulesDestination) },
                        onVocabulary = { navController.navigate(VocabularyDestination) },
                        onPrompts = { navController.navigate(PromptsDestination) },
                        onPermissions = { navController.navigate(PermissionsDestination) },
                        onHistory = { navController.navigate(HistorySettingsDestination) },
                        onAppearance = { navController.navigate(AppearanceSettingsDestination) },
                        onLicenses = { navController.navigate(LicensesDestination) }
                    )
                )
            }
            composable<ProvidersDestination> {
                ProvidersRoute(
                    onOpenProvider = { id -> navController.navigate(ProviderEditorDestination(id)) },
                    onBack = dropUnlessResumed { navController.popBackStack() }
                )
            }
            composable<ProviderEditorDestination> {
                ProviderEditorRoute(onDone = dropUnlessResumed { navController.popBackStack() })
            }
            composable<BubbleSettingsDestination> {
                BubbleSettingsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<LicensesDestination> {
                LicensesRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<RecordingSettingsDestination> {
                RecordingSettingsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<HistorySettingsDestination> {
                HistorySettingsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<AppsSettingsDestination> {
                AppsSettingsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<AppearanceSettingsDestination> {
                AppearanceSettingsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<PermissionsDestination> {
                PermissionsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<VocabularyDestination> {
                VocabularyRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<ReplacementRulesDestination> {
                ReplacementRulesRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
            composable<PromptsDestination> {
                PromptsRoute(onBack = dropUnlessResumed { navController.popBackStack() })
            }
        }
    }
}
