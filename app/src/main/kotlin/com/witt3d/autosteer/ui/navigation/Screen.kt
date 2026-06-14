package com.witt3d.autosteer.ui.navigation

sealed class Screen(val route: String, val label: String) {
    object Map : Screen("map", "Guidance")
    object FieldManagement : Screen("fields", "Fields")
    object Settings : Screen("settings", "Settings")
}
