package com.sasayaki.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.sasayaki.R
import com.sasayaki.ui.theme.SasayakiIcons
import kotlin.reflect.KClass

enum class TopLevelDestination(val route: Any, @StringRes val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(HomeDestination, R.string.nav_home, SasayakiIcons.Home, SasayakiIcons.HomeFilled),
    HISTORY(HistoryDestination(), R.string.nav_history, SasayakiIcons.History, SasayakiIcons.HistoryFilled),
    PROFILES(ProfilesDestination, R.string.nav_profiles, SasayakiIcons.Tune, SasayakiIcons.TuneFilled),
    SETTINGS(SettingsDestination, R.string.nav_settings, SasayakiIcons.Settings, SasayakiIcons.SettingsFilled);

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
