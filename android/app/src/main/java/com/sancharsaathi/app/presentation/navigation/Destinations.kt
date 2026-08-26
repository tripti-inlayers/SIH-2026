package com.sancharsaathi.app.presentation.navigation

sealed class Destinations(val route: String) {
    object Home : Destinations("home")
    object Analyzing : Destinations("analyzing/{requestJson}") {
        fun createRoute(requestJson: String) = "analyzing/${java.net.URLEncoder.encode(requestJson, "UTF-8")}"
    }
    object Result : Destinations("result/{analysisId}") {
        fun createRoute(analysisId: String) = "result/$analysisId"
    }
    object Blocked : Destinations("blocked/{analysisId}") {
        fun createRoute(analysisId: String) = "blocked/$analysisId"
    }
    object ReportConfirmation : Destinations("report_confirmation/{analysisId}") {
        fun createRoute(analysisId: String) = "report_confirmation/$analysisId"
    }
    object History : Destinations("history")
    object Settings : Destinations("settings")
}
