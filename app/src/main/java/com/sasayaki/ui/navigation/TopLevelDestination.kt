package com.sasayaki.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.sasayaki.ui.theme.SasayakiIcons
import kotlin.reflect.KClass

enum class TopLevelDestination(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(HomeDestination, "Home", SasayakiIcons.Home, SasayakiIcons.HomeFilled),
    HISTORY(HistoryDestination(), "History", SasayakiIcons.History, SasayakiIcons.HistoryFilled),
    PROFILES(ProfilesDestination, "Profiles", SasayakiIcons.Tune, SasayakiIcons.TuneFilled),
    SETTINGS(SettingsDestination, "Settings", SasayakiIcons.Settings, SasayakiIcons.SettingsFilled);

    val routeClass: KClass<*> get() = route::class
}

fun NavDestination?.topLevelDestination(): TopLevelDestination? = this?.let { destination ->
    TopLevelDestination.entries.firstOrNull { destination.hasRoute(it.routeClass) }
}

fun NavDestination?.isIn(destination: TopLevelDestination): Boolean = this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

// Tabs pop back to Home rather than the graph's start destination, which is onboarding
// on a first launch and is gone from the back stack once it finishes.
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo<HomeDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// A focused dictation needs a fresh History entry: restoring the saved one would bring back
// its old arguments.
fun NavController.navigateToDictation(id: Long) {
    navigate(HistoryDestination(focusId = id)) {
        popUpTo<HomeDestination> { saveState = true }
        launchSingleTop = true
    }
}
