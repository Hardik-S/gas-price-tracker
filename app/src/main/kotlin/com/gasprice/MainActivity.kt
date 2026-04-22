package com.gasprice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gasprice.service.MonitoringController
import com.gasprice.ui.capture.PriceCaptureScreen
import com.gasprice.ui.dashboard.DashboardScreen
import com.gasprice.ui.history.HistoryScreen
import com.gasprice.ui.onboarding.OnboardingScreen
import com.gasprice.ui.settings.SettingsScreen
import com.gasprice.ui.theme.GasPriceTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var monitoringController: MonitoringController

    private var pendingStationName: String? = null
    private var pendingPlaceId: String? = null
    private var pendingAddress: String? = null
    private var pendingLat: Double? = null
    private var pendingLng: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseStationFromIntent(intent)

        setContent {
            GasPriceTrackerTheme {
                AppNavGraph(
                    initialRoute = if (pendingStationName != null) Screen.PriceCapture.route else Screen.Dashboard.route,
                    pendingStationName = pendingStationName,
                    pendingPlaceId = pendingPlaceId,
                    pendingAddress = pendingAddress,
                    pendingLat = pendingLat,
                    pendingLng = pendingLng,
                    onCaptureComplete = { monitoringController.onPriceCaptureComplete() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseStationFromIntent(intent)
    }

    private fun parseStationFromIntent(intent: Intent?) {
        if (intent?.action == MonitoringController.ACTION_OPEN_PRICE_CAPTURE) {
            pendingStationName = intent.getStringExtra(MonitoringController.EXTRA_STATION_NAME)
            pendingPlaceId = intent.getStringExtra(MonitoringController.EXTRA_STATION_PLACE_ID)
            pendingAddress = intent.getStringExtra(MonitoringController.EXTRA_STATION_ADDRESS)
            pendingLat = intent.getDoubleExtra(MonitoringController.EXTRA_STATION_LAT, 0.0).takeIf { it != 0.0 }
            pendingLng = intent.getDoubleExtra(MonitoringController.EXTRA_STATION_LNG, 0.0).takeIf { it != 0.0 }
        }
    }
}

object Screen {
    object Onboarding { val route = "onboarding" }
    object Dashboard { val route = "dashboard" }
    object History { val route = "history" }
    object Settings { val route = "settings" }
    object PriceCapture { val route = "price_capture" }
}

@Composable
fun AppNavGraph(
    initialRoute: String,
    pendingStationName: String?,
    pendingPlaceId: String?,
    pendingAddress: String?,
    pendingLat: Double?,
    pendingLng: Double?,
    onCaptureComplete: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = initialRoute) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateHistory = { navController.navigate(Screen.History.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateOnboarding = { navController.navigate(Screen.Onboarding.route) },
                onNavigatePriceCapture = { name, placeId, address, lat, lng ->
                    navController.navigate(Screen.PriceCapture.route)
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.PriceCapture.route) {
            PriceCaptureScreen(
                stationName = pendingStationName ?: "Gas Station",
                stationPlaceId = pendingPlaceId,
                stationAddress = pendingAddress,
                latitude = pendingLat ?: 0.0,
                longitude = pendingLng ?: 0.0,
                onComplete = {
                    onCaptureComplete()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.PriceCapture.route) { inclusive = true }
                    }
                },
                onDismiss = {
                    onCaptureComplete()
                    navController.popBackStack()
                }
            )
        }
    }
}
