package cl.wenasea.wenagastos.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.wenasea.wenagastos.ui.capture.CaptureDiagnosticsScreen
import cl.wenasea.wenagastos.ui.editor.TransactionEditorScreen
import cl.wenasea.wenagastos.ui.home.DashboardScreen
import cl.wenasea.wenagastos.ui.movements.MovementsScreen
import cl.wenasea.wenagastos.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val MOVEMENTS = "movements"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture"
    const val EDITOR = "editor?txId={txId}"

    fun editor(txId: Long): String = "editor?txId=$txId"
}

private data class Tab(val route: String, val label: String, val emoji: String)

private val tabs = listOf(
    Tab(Routes.HOME, "Inicio", "🏠"),
    Tab(Routes.MOVEMENTS, "Movimientos", "🧾"),
    Tab(Routes.SETTINGS, "Ajustes", "⚙️"),
)

@Composable
fun WenagastosNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute == Routes.HOME ||
        currentRoute == Routes.MOVEMENTS ||
        currentRoute == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.emoji, fontSize = 18.sp) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showChrome) {
                FloatingActionButton(onClick = { navController.navigate(Routes.editor(-1L)) }) {
                    Text("+", fontSize = 28.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                DashboardScreen(
                    onSeeAllMovements = { navController.navigate(Routes.MOVEMENTS) },
                    onOpenTransaction = { id -> navController.navigate(Routes.editor(id)) },
                )
            }
            composable(Routes.MOVEMENTS) {
                MovementsScreen(
                    onOpenTransaction = { id -> navController.navigate(Routes.editor(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenDiagnostics = { navController.navigate(Routes.CAPTURE) })
            }
            composable(Routes.CAPTURE) {
                CaptureDiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("txId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val txId = entry.arguments?.getLong("txId") ?: -1L
                TransactionEditorScreen(
                    txId = txId.coerceAtLeast(0L),
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
