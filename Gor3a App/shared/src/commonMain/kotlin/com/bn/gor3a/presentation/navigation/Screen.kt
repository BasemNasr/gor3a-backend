package com.bn.gor3a.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object ScanningAnimation : Screen("scanning_animation")
    object UnrecognizedPrescription : Screen("unrecognized_prescription")
    object Results : Screen("results")
    object DrugExplorer : Screen("drug_explorer")
    object Reminders : Screen("reminders")
    object History : Screen("history")
}
