package com.sasayaki.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sasayaki.ui.home.HomeScreen
import com.sasayaki.ui.profiles.ProfilesScreen
import com.sasayaki.ui.settings.SettingsScreen
import com.sasayaki.ui.theme.SasayakiIcons

object Routes {
    const val HOME = "home"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
}
private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Transcriptions", SasayakiIcons.Description),
    BottomDestination(Routes.PROFILES, "Profiles", SasayakiIcons.Tune),
    BottomDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun SasayakiNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            NavigationBar {
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) {
                HomeScreen(outerPadding = padding)
            }
            composable(Routes.PROFILES) {
                ProfilesScreen(outerPadding = padding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(outerPadding = padding)
            }
        }
    }
}
