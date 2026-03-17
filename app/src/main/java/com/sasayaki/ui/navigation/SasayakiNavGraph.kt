package com.sasayaki.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sasayaki.ui.dictionary.DictionaryScreen
import com.sasayaki.ui.history.HistoryScreen
import com.sasayaki.ui.home.HomeScreen
import com.sasayaki.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DICTIONARY = "dictionary"
    const val HISTORY = "history"
}

private val enterTransition: EnterTransition = fadeIn(animationSpec = tween(durationMillis = 180))
private val exitTransition: ExitTransition = fadeOut(animationSpec = tween(durationMillis = 120))
private val popEnterTransition: EnterTransition = fadeIn(animationSpec = tween(durationMillis = 180))
private val popExitTransition: ExitTransition = fadeOut(animationSpec = tween(durationMillis = 120))

@Composable
fun SasayakiNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigateSingleTop(Routes.SETTINGS) },
                onNavigateToDictionary = { navController.navigateSingleTop(Routes.DICTIONARY) },
                onNavigateToHistory = { navController.navigateSingleTop(Routes.HISTORY) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DICTIONARY) {
            DictionaryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}
