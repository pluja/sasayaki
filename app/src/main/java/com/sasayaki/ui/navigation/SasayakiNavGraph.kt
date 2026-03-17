package com.sasayaki.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
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

private val enterTransition: EnterTransition = slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()
private val exitTransition: ExitTransition = slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
private val popEnterTransition: EnterTransition = slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
private val popExitTransition: ExitTransition = slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()

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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToDictionary = { navController.navigate(Routes.DICTIONARY) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) }
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
