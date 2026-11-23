package cl.wenasea.wenagastos.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.wenasea.wenagastos.ui.balances.BalancesScreen
import cl.wenasea.wenagastos.ui.capture.CaptureDiagnosticsScreen
import cl.wenasea.wenagastos.ui.editor.TransactionEditorScreen
import cl.wenasea.wenagastos.ui.home.DashboardScreen
import cl.wenasea.wenagastos.ui.movements.MovementsScreen
import cl.wenasea.wenagastos.ui.reports.ReportsScreen
import cl.wenasea.wenagastos.ui.settings.MerchantRulesScreen
import cl.wenasea.wenagastos.ui.settings.SettingsScreen
import cl.wenasea.wenagastos.ui.theme.Wena
import java.time.YearMonth

object Routes {
    const val HOME = "home"

    /**
     * Patrón con el mes opcional: la ruta sin argumento (`movements`) también calza, así el tab
     * funciona igual que el enlace con mes de «Ver movimientos».
     */
    const val MOVEMENTS = "movements?month={month}"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val RULES = "rules"
    const val CAPTURE = "capture"
    const val BALANCES = "balances"
    const val EDITOR = "editor?txId={txId}"

    fun editor(txId: Long): String = "editor?txId=$txId"

    /** Movimientos en un mes concreto; sin mes, el ViewModel abre en el de hoy. */
    fun movements(month: YearMonth? = null): String =
        if (month == null) "movements" else "movements?month=$month"

    /**
     * Lee el argumento `month` de la ruta. Un texto que no es un mes no tumba la pantalla: se
     * ignora y Movimientos abre en el mes de hoy, que es el peor error aceptable.
     */
    fun monthFrom(raw: String?): YearMonth? =
        raw?.let { text -> runCatching { YearMonth.parse(text) }.getOrNull() }
}

private data class Tab(val route: String, val label: String, val emoji: String)

private val tabs = listOf(
    Tab(Routes.HOME, "Inicio", "🏠"),
    Tab(Routes.MOVEMENTS, "Movimientos", "🧾"),
    Tab(Routes.REPORTS, "Reportes", "📊"),
    Tab(Routes.SETTINGS, "Ajustes", "⚙️"),
)

/** Las cuatro rutas que muestran la barra de abajo y el botón de agregar. */
private val tabRoutes = tabs.map { it.route }

@Composable
fun WenagastosNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute in tabRoutes

    /**
     * El único camino para cambiar de tab, con una excepción que antes no estaba: **Inicio no se
     * restaura**.
     *
     * Con `restoreState` para los cuatro tabs, tocar 🏠 aterrizaba en Movimientos: `saveState`
     * guardaba el stack que se despejaba y, como Inicio es el destino inicial y no tiene estado
     * propio que devolver, Navigation restauraba el último stack guardado —el de Movimientos—. Para
     * Inicio basta con despejar la pila hasta él; los otros tres conservan su scroll y su mes.
     *
     * Tocar el tab en el que ya se está no hace nada: antes apilaba una copia de la pantalla y el
     * botón «atrás» tenía que deshacerla.
     */
    fun openTab(route: String) {
        if (route == currentRoute) return
        if (route == Routes.HOME) {
            navController.popBackStack(Routes.HOME, inclusive = false)
            return
        }
        // El tab de Movimientos no lleva mes: al patrón (`movements?month={month}`) se le pasa la
        // ruta sin argumentos, para no meter las llaves en la URL. Sin mes, el ViewModel abre en
        // el de hoy.
        val target = if (route == Routes.MOVEMENTS) Routes.movements() else route
        navController.navigate(target) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showChrome) {
                WenaNavBar(currentRoute = currentRoute, onSelect = { route -> openTab(route) })
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
                    onSeeAllMovements = { month ->
                        navController.navigate(Routes.movements(month))
                    },
                    onAddMovement = { navController.navigate(Routes.editor(-1L)) },
                    onOpenTransaction = { id -> navController.navigate(Routes.editor(id)) },
                    onSeeReports = { openTab(Routes.REPORTS) },
                    onOpenBalances = { navController.navigate(Routes.BALANCES) },
                    onOpenSettings = { openTab(Routes.SETTINGS) },
                    onOpenDiagnostics = { navController.navigate(Routes.CAPTURE) },
                )
            }
            composable(
                route = Routes.MOVEMENTS,
                arguments = listOf(
                    navArgument("month") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                MovementsScreen(
                    initialMonth = Routes.monthFrom(entry.arguments?.getString("month")),
                    onOpenTransaction = { id -> navController.navigate(Routes.editor(id)) },
                    onAddMovement = { navController.navigate(Routes.editor(-1L)) },
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen(
                    onOpenTransaction = { id -> navController.navigate(Routes.editor(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenRules = { navController.navigate(Routes.RULES) },
                )
            }
            composable(Routes.RULES) {
                MerchantRulesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CAPTURE) {
                CaptureDiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BALANCES) {
                BalancesScreen(onBack = { navController.popBackStack() })
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

/**
 * Barra de navegación con la tecla pisada del manual en vez del pill de Material: el tab activo
 * se hunde en un bloque de papel con borde de 1 dp, y la etiqueta pasa de gris a tinta plena.
 * Sin sombras ni elevación — los planos se separan con una línea, como en el manual.
 */
@Composable
private fun WenaNavBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val paleta = Wena.paleta
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(paleta.fondo),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(paleta.linea),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                WenaTab(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onSelect(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WenaTab(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paleta = Wena.paleta
    val keyShape = RoundedCornerShape(5.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 30.dp)
                .then(
                    if (selected) {
                        Modifier
                            .clip(keyShape)
                            .background(paleta.papelHondo)
                            .border(1.dp, paleta.linea, keyShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = tab.emoji, fontSize = 17.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) paleta.texto else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
