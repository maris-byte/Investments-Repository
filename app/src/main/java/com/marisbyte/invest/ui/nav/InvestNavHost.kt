package com.marisbyte.invest.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marisbyte.invest.InvestApp
import com.marisbyte.invest.assistant.intent.AppScreen
import com.marisbyte.invest.ui.screens.assistant.AssistantScreen
import com.marisbyte.invest.ui.screens.dashboard.DashboardScreen
import com.marisbyte.invest.ui.screens.detail.AssetDetailScreen
import com.marisbyte.invest.ui.screens.markets.MarketsScreen
import com.marisbyte.invest.ui.screens.portfolio.PortfolioScreen
import com.marisbyte.invest.ui.screens.settings.SettingsScreen

sealed class Destination(val route: String) {
    data object Dashboard : Destination("dashboard")
    data object Markets : Destination("markets")
    data object Portfolio : Destination("portfolio")
    data object Assistant : Destination("assistant")
    data object Settings : Destination("settings")
    data object AssetDetail : Destination("asset/{assetId}") {
        fun createRoute(assetId: String): String = "asset/$assetId"
    }
}

private data class TabItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    TabItem(Destination.Dashboard, "Übersicht", Icons.Default.Insights),
    TabItem(Destination.Markets, "Märkte", Icons.Default.ShowChart),
    TabItem(Destination.Portfolio, "Depot", Icons.Default.AccountBalanceWallet),
    TabItem(Destination.Assistant, "Alfred", Icons.Default.Mic),
    TabItem(Destination.Settings, "Mehr", Icons.Default.Settings)
)

@Composable
fun InvestNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TABS.any { it.destination.route == currentRoute }

    AlfredNavigationBridge(navController)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.destination.route,
                            onClick = {
                                navController.navigate(tab.destination.route) {
                                    // Kein Stapel aus Tab-Wechseln: immer zurueck zum Start.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onAssetClick = { navController.navigate(Destination.AssetDetail.createRoute(it)) }
                )
            }
            composable(Destination.Markets.route) {
                MarketsScreen(
                    onAssetClick = { navController.navigate(Destination.AssetDetail.createRoute(it)) }
                )
            }
            composable(Destination.Portfolio.route) {
                PortfolioScreen(
                    onAssetClick = { navController.navigate(Destination.AssetDetail.createRoute(it)) }
                )
            }
            composable(Destination.Assistant.route) {
                AssistantScreen()
            }
            composable(Destination.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Destination.AssetDetail.route,
                arguments = listOf(navArgument("assetId") { type = NavType.StringType })
            ) {
                AssetDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Sagt Alfred "öffne das Depot", wechselt die App den Bildschirm. Die Sitzung meldet
 * das Ziel nur; das Umschalten passiert hier, wo der Navigationsbaum bekannt ist.
 */
@Composable
private fun AlfredNavigationBridge(navController: NavHostController) {
    val context = LocalContext.current
    val session = remember(context) {
        (context.applicationContext as? InvestApp)?.container?.alfredSession
    } ?: return

    val alfredState by session.state.collectAsStateWithLifecycle()
    LaunchedEffect(alfredState.navigateTo) {
        val target = alfredState.navigateTo ?: return@LaunchedEffect
        navController.navigate(routeFor(target)) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        session.clearNavigation()
    }
}

private fun routeFor(screen: AppScreen): String = when (screen) {
    AppScreen.DASHBOARD -> Destination.Dashboard.route
    AppScreen.MARKETS -> Destination.Markets.route
    AppScreen.PORTFOLIO -> Destination.Portfolio.route
    AppScreen.SETTINGS -> Destination.Settings.route
    AppScreen.TASKS -> Destination.Assistant.route
}
