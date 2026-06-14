package com.witt3d.autosteer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.witt3d.autosteer.ui.screens.FieldManagementScreen
import com.witt3d.autosteer.ui.screens.MapScreen
import com.witt3d.autosteer.ui.screens.SettingsScreen

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Map,
        Screen.FieldManagement,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            when (screen) {
                                Screen.Map -> Icon(Icons.Filled.Home, contentDescription = null)
                                Screen.FieldManagement -> Icon(Icons.Filled.List, contentDescription = null)
                                Screen.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Map.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Map.route) { MapScreen() }
            composable(Screen.FieldManagement.route) { FieldManagementScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
