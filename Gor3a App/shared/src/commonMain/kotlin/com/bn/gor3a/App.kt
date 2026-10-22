package com.bn.gor3a

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.bn.gor3a.core.localization.AppLanguage
import com.bn.gor3a.core.localization.Gor3aLocalizationProvider
import com.bn.gor3a.core.navigation.PlatformBackHandler
import com.bn.gor3a.core.theme.Gor3aTheme
import com.bn.gor3a.core.theme.LocalThemeIsDark
import com.bn.gor3a.di.startGor3aKoin
import com.bn.gor3a.data.mapper.toAnalysisResult
import com.bn.gor3a.domain.model.PatientInfo
import com.bn.gor3a.domain.model.PrescriptionAnalysisResult
import com.bn.gor3a.presentation.navigation.Screen
import com.bn.gor3a.presentation.screens.DrugExplorerScreen
import com.bn.gor3a.presentation.screens.HistoryScreen
import com.bn.gor3a.presentation.screens.HomeScreen
import com.bn.gor3a.presentation.screens.RemindersScreen
import com.bn.gor3a.presentation.screens.ResultsScreen
import com.bn.gor3a.presentation.screens.ScannerScreen
import com.bn.gor3a.presentation.screens.ScanningAnimationScreen
import com.bn.gor3a.presentation.screens.SplashScreen
import com.bn.gor3a.presentation.screens.UnrecognizedPrescriptionScreen
import com.bn.gor3a.presentation.viewmodel.AuthViewModel
import com.bn.gor3a.presentation.viewmodel.DrugExplorerViewModel
import com.bn.gor3a.presentation.viewmodel.HistoryViewModel
import com.bn.gor3a.presentation.viewmodel.HomeViewModel
import com.bn.gor3a.presentation.viewmodel.RemindersViewModel
import com.bn.gor3a.presentation.viewmodel.ResultsViewModel
import com.bn.gor3a.presentation.viewmodel.ScannerEffect
import com.bn.gor3a.presentation.viewmodel.ScannerViewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    // Ensure Koin is initialized
    remember {
        startGor3aKoin()
    }

    val currentLanguageState = remember { mutableStateOf(AppLanguage.ARABIC) }
    val isDarkState = remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalThemeIsDark provides isDarkState) {
        Gor3aLocalizationProvider(currentLanguageState = currentLanguageState) {
            Gor3aTheme(darkTheme = isDarkState.value) {
                KoinContext {
                    AppNavigationContent()
                }
            }
        }
    }
}

@Composable
private fun AppNavigationContent() {
    // Navigation back stack allowing proper back-button handling
    val backStack = remember { mutableStateListOf<Screen>(Screen.Splash) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home

    fun navigateTo(screen: Screen) {
        if (screen == Screen.Home) {
            backStack.clear()
            backStack.add(Screen.Home)
        } else {
            if (backStack.lastOrNull() != screen) {
                backStack.add(screen)
            }
        }
    }

    fun navigateToTab(tabScreen: Screen) {
        if (currentScreen == tabScreen) return
        if (tabScreen == Screen.Home) {
            backStack.clear()
            backStack.add(Screen.Home)
        } else {
            backStack.clear()
            backStack.add(Screen.Home)
            backStack.add(tabScreen)
        }
    }

    fun popBack(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }

    val homeViewModel: HomeViewModel = koinInject()
    val scannerViewModel: ScannerViewModel = koinInject()
    val resultsViewModel: ResultsViewModel = koinInject()
    val drugExplorerViewModel: DrugExplorerViewModel = koinInject()
    val remindersViewModel: RemindersViewModel = koinInject()
    val historyViewModel: HistoryViewModel = koinInject()
    val authViewModel: AuthViewModel = koinInject()

    // Intercept hardware / gesture Back on Android
    PlatformBackHandler(enabled = backStack.size > 1 && currentScreen != Screen.Home) {
        if (currentScreen == Screen.ScanningAnimation || currentScreen == Screen.UnrecognizedPrescription) {
            scannerViewModel.resetScanner()
            while (backStack.isNotEmpty() && (backStack.lastOrNull() == Screen.ScanningAnimation || backStack.lastOrNull() == Screen.UnrecognizedPrescription)) {
                backStack.removeAt(backStack.lastIndex)
            }
            if (backStack.isEmpty()) {
                backStack.add(Screen.Scanner)
            }
        } else {
            popBack()
        }
    }

    // Listen to Scanner effects
    LaunchedEffect(scannerViewModel) {
        scannerViewModel.effects.collectLatest { effect ->
            when (effect) {
                is ScannerEffect.NavigateToResults -> {
                    resultsViewModel.setAnalysisResult(effect.result, effect.imageBytes)
                    // Remove ScanningAnimation so back button takes user directly to Scanner/Home
                    while (backStack.isNotEmpty() && backStack.lastOrNull() == Screen.ScanningAnimation) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    navigateTo(Screen.Results)
                }
                is ScannerEffect.NavigateToUnrecognized -> {
                    // Remove ScanningAnimation so back button takes user directly to Scanner
                    while (backStack.isNotEmpty() && backStack.lastOrNull() == Screen.ScanningAnimation) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    navigateTo(Screen.UnrecognizedPrescription)
                }
                is ScannerEffect.ShowError -> {
                    while (backStack.isNotEmpty() && backStack.lastOrNull() == Screen.ScanningAnimation) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    navigateTo(Screen.UnrecognizedPrescription)
                }
            }
        }
    }

    // Automatically transition to ScanningAnimation when analysis begins
    val scannerState = scannerViewModel.uiState.collectAsState()
    LaunchedEffect(scannerState.value.isAnalyzing, currentScreen) {
        if (scannerState.value.isAnalyzing && currentScreen == Screen.Scanner) {
            navigateTo(Screen.ScanningAnimation)
        }
    }

    Crossfade(
        targetState = currentScreen,
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            Screen.Splash -> {
                SplashScreen(
                    onGetStartedClick = {
                        navigateToTab(Screen.Home)
                    }
                )
            }

            Screen.Home -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    authViewModel = authViewModel,
                    onNavigateToScanner = {
                        navigateTo(Screen.Scanner)
                    },
                    onNavigateToSearch = {
                        navigateToTab(Screen.DrugExplorer)
                    },
                    onSearchBarClick = {
                        drugExplorerViewModel.requestSearchFocus()
                        navigateToTab(Screen.DrugExplorer)
                    },
                    onNavigateToHistory = {
                        navigateToTab(Screen.History)
                    },
                    onNavigateToReminders = {
                        navigateToTab(Screen.Reminders)
                    },
                    onSelectPrescription = { rx ->
                        // Open saved prescription into Results Screen
                        val analysisResult = rx.toAnalysisResult()
                        resultsViewModel.setAnalysisResult(analysisResult, rx.prescriptionImageBytes)
                        navigateTo(Screen.Results)
                    }
                )
            }

            Screen.Scanner -> {
                ScannerScreen(
                    viewModel = scannerViewModel,
                    onCloseClick = {
                        if (!popBack()) navigateToTab(Screen.Home)
                    }
                )
            }

            Screen.ScanningAnimation -> {
                ScanningAnimationScreen(
                    viewModel = scannerViewModel,
                    onBackClick = {
                        scannerViewModel.resetScanner()
                        while (backStack.isNotEmpty() && backStack.lastOrNull() == Screen.ScanningAnimation) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        if (backStack.isEmpty() || backStack.lastOrNull() != Screen.Scanner) {
                            backStack.add(Screen.Scanner)
                        }
                    }
                )
            }

            Screen.UnrecognizedPrescription -> {
                UnrecognizedPrescriptionScreen(
                    onTryAgainClick = {
                        scannerViewModel.resetScanner()
                        while (backStack.isNotEmpty() && (backStack.lastOrNull() == Screen.UnrecognizedPrescription || backStack.lastOrNull() == Screen.ScanningAnimation)) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        if (backStack.isEmpty() || backStack.lastOrNull() != Screen.Scanner) {
                            backStack.add(Screen.Scanner)
                        }
                    },
                    onChooseFromGalleryClick = {
                        scannerViewModel.resetScanner()
                        while (backStack.isNotEmpty() && (backStack.lastOrNull() == Screen.UnrecognizedPrescription || backStack.lastOrNull() == Screen.ScanningAnimation)) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        if (backStack.isEmpty() || backStack.lastOrNull() != Screen.Scanner) {
                            backStack.add(Screen.Scanner)
                        }
                    },
                    onBackToHomeClick = {
                        scannerViewModel.resetScanner()
                        navigateToTab(Screen.Home)
                    }
                )
            }

            Screen.Results -> {
                ResultsScreen(
                    viewModel = resultsViewModel,
                    onBackClick = {
                        scannerViewModel.resetScanner()
                        while (backStack.isNotEmpty() && (backStack.lastOrNull() == Screen.Results || backStack.lastOrNull() == Screen.ScanningAnimation)) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        if (backStack.isEmpty()) {
                            backStack.add(Screen.Home)
                        }
                    }
                )
            }

            Screen.DrugExplorer -> {
                DrugExplorerScreen(
                    viewModel = drugExplorerViewModel,
                    onNavigateToHome = {
                        navigateToTab(Screen.Home)
                    },
                    onNavigateToHistory = {
                        navigateToTab(Screen.History)
                    },
                    onNavigateToReminders = {
                        navigateToTab(Screen.Reminders)
                    }
                )
            }

            Screen.Reminders -> {
                RemindersScreen(
                    viewModel = remindersViewModel,
                    onNavigateToHome = {
                        navigateToTab(Screen.Home)
                    },
                    onNavigateToSearch = {
                        navigateToTab(Screen.DrugExplorer)
                    },
                    onNavigateToHistory = {
                        navigateToTab(Screen.History)
                    }
                )
            }

            Screen.History -> {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onNavigateToHome = {
                        navigateToTab(Screen.Home)
                    },
                    onNavigateToSearch = {
                        navigateToTab(Screen.DrugExplorer)
                    },
                    onNavigateToReminders = {
                        navigateToTab(Screen.Reminders)
                    },
                    onNavigateToScanner = {
                        navigateTo(Screen.Scanner)
                    },
                    onSelectPrescription = { rx ->
                        val analysisResult = rx.toAnalysisResult()
                        resultsViewModel.setAnalysisResult(analysisResult, rx.prescriptionImageBytes)
                        navigateTo(Screen.Results)
                    }
                )
            }
        }
    }
}