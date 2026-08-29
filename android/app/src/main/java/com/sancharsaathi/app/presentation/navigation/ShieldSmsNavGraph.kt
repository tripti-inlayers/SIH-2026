package com.sancharsaathi.app.presentation.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.domain.capture.SharedContentChannel
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.analyzing.AnalyzingScreen
import com.sancharsaathi.app.presentation.analyzing.AnalyzingViewModel
import com.sancharsaathi.app.presentation.blocked.BlockedScreen
import com.sancharsaathi.app.presentation.history.HistoryScreen
import com.sancharsaathi.app.presentation.history.HistoryViewModel
import com.sancharsaathi.app.presentation.home.HomeScreen
import com.sancharsaathi.app.presentation.home.HomeViewModel
import com.sancharsaathi.app.presentation.report.ReportConfirmationScreen
import com.sancharsaathi.app.presentation.report.ReportViewModel
import com.sancharsaathi.app.presentation.result.RiskResultScreen
import com.sancharsaathi.app.presentation.result.RiskResultViewModel
import com.sancharsaathi.app.presentation.settings.SettingsScreen

@Composable
fun SancharSaathiNavGraph(
    navController: NavHostController,
    intent: Intent?,
    onRequestSmsPermissions: () -> Unit
) {
    val gson = remember { Gson() }
    var activeResultForBlocked by remember { mutableStateOf<RiskResult?>(null) }
    var activeResultForReport by remember { mutableStateOf<RiskResult?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Handle shared text intent from outside app
    LaunchedEffect(intent) {
        if (intent != null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val req = SharedContentChannel.emitSharedText(sharedText)
                val json = gson.toJson(req)
                navController.navigate(Destinations.Analyzing.createRoute(json))
            }
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(androidx.compose.ui.res.stringResource(id = com.sancharsaathi.app.R.string.perm_rationale_title)) },
            text = {
                Text(androidx.compose.ui.res.stringResource(id = com.sancharsaathi.app.R.string.perm_rationale_desc))
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    onRequestSmsPermissions()
                }) {
                    Text(androidx.compose.ui.res.stringResource(id = com.sancharsaathi.app.R.string.perm_rationale_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(androidx.compose.ui.res.stringResource(id = com.sancharsaathi.app.R.string.perm_rationale_skip))
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.Home.route,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(200)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(200)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(200)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(200)) }
    ) {
        composable(Destinations.Home.route) {
            val vm = viewModel { HomeViewModel(AppModule.historyStore, AppModule.demoContentSource) }
            HomeScreen(
                viewModel = vm,
                onNavigateToAnalyzing = { req ->
                    val json = gson.toJson(req)
                    navController.navigate(Destinations.Analyzing.createRoute(json))
                },
                onNavigateToResult = { analysisId ->
                    navController.navigate(Destinations.Result.createRoute(analysisId))
                },
                onNavigateToHistory = { navController.navigate(Destinations.History.route) },
                onNavigateToSettings = { navController.navigate(Destinations.Settings.route) }
            )
        }

        composable(
            route = Destinations.Analyzing.route,
            arguments = listOf(navArgument("requestJson") { type = NavType.StringType })
        ) { backStackEntry ->
            val json = backStackEntry.arguments?.getString("requestJson") ?: ""
            val decodedJson = java.net.URLDecoder.decode(json, "UTF-8")
            val request = gson.fromJson(decodedJson, AnalysisRequest::class.java)
            val vm = viewModel { AnalyzingViewModel(AppModule.analyzeContentUseCase) }

            AnalyzingScreen(
                viewModel = vm,
                request = request,
                onNavigateToResult = { result ->
                    activeResultForBlocked = result
                    navController.navigate(Destinations.Result.createRoute(result.analysisId)) {
                        popUpTo(Destinations.Home.route) { inclusive = false }
                    }
                },
                onNavigateToBlocked = { result ->
                    activeResultForBlocked = result
                    navController.navigate(Destinations.Blocked.createRoute(result.analysisId)) {
                        popUpTo(Destinations.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Destinations.Result.route,
            arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("analysisId") ?: ""
            val vm = viewModel { RiskResultViewModel(AppModule.historyStore) }

            RiskResultScreen(
                viewModel = vm,
                analysisId = id,
                initialResult = if (activeResultForBlocked?.analysisId == id) activeResultForBlocked else null,
                onNavigateBack = {
                    navController.navigate(Destinations.Home.route) {
                        popUpTo(Destinations.Home.route) { inclusive = true }
                    }
                },
                onNavigateToReport = { result ->
                    activeResultForReport = result
                    navController.navigate(Destinations.ReportConfirmation.createRoute(result.analysisId))
                }
            )
        }

        composable(
            route = Destinations.Blocked.route,
            arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("analysisId") ?: ""
            val cached = AppModule.historyStore.get(id) ?: activeResultForBlocked

            if (cached != null) {
                BlockedScreen(
                    result = cached,
                    onNavigateBack = {
                        navController.navigate(Destinations.Home.route) {
                            popUpTo(Destinations.Home.route) { inclusive = true }
                        }
                    },
                    onNavigateToReport = {
                        activeResultForReport = cached
                        navController.navigate(Destinations.ReportConfirmation.createRoute(cached.analysisId))
                    }
                )
            }
        }

        composable(
            route = Destinations.ReportConfirmation.route,
            arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("analysisId") ?: ""
            val cached = AppModule.historyStore.get(id) ?: activeResultForReport
            val vm = viewModel { ReportViewModel(AppModule.submitReportUseCase) }

            if (cached != null) {
                ReportConfirmationScreen(
                    viewModel = vm,
                    riskResult = cached,
                    onNavigateHome = {
                        navController.navigate(Destinations.Home.route) {
                            popUpTo(Destinations.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Destinations.History.route) {
            val vm = viewModel { HistoryViewModel(AppModule.historyStore) }
            HistoryScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { id ->
                    navController.navigate(Destinations.Result.createRoute(id))
                }
            )
        }

        composable(Destinations.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onRequestSmsPermissions = {
                    showPermissionRationale = true
                }
            )
        }
    }
}
