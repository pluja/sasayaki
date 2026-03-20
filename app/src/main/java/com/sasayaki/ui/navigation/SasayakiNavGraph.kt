package com.sasayaki.ui.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
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

private val fadeIn = fadeIn(animationSpec = tween(durationMillis = 150))
private val noExit = ExitTransition.None

@Composable
fun SasayakiNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn },
        exitTransition = { noExit },
        popEnterTransition = { fadeIn },
        popExitTransition = { noExit }
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
