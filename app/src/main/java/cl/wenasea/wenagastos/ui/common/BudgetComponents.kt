package cl.wenasea.wenagastos.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.wenasea.wenagastos.domain.BudgetLevel
import cl.wenasea.wenagastos.domain.BudgetStatus
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.theme.Wena
import kotlin.math.roundToInt

/**
 * Verde, ámbar o rojo según qué tan cerca está el presupuesto. Sale de `Wena.paleta` y no de los
 * roles M3: `primary` es la marca y `tertiary` es el abono, así que "cerca del tope" salía verde.
 * Las tres variantes pasan AA como texto sobre la tarjeta en su modo.
 */
@Composable
fun budgetColor(level: BudgetLevel): Color {
    val paleta = Wena.paleta
    return when (level) {
        BudgetLevel.OK -> paleta.presupuestoOk
        BudgetLevel.NEAR -> paleta.presupuestoCerca
        BudgetLevel.OVER -> paleta.presupuestoExcedido
    }
}

/** Barra del presupuesto: se llena hasta el 100 % y cambia de color al acercarse al límite. */
@Composable
fun BudgetProgress(status: BudgetStatus, modifier: Modifier = Modifier) {
    ShareBar(
        share = status.fraction.coerceIn(0f, 1f),
        color = budgetColor(status.level),
        modifier = modifier,
    )
}

/** "$90.000 de $120.000" */
fun budgetOfLabel(status: BudgetStatus): String =
    "${Money.format(status.spent)} de ${Money.format(status.budget)}"

/** "75%" — el avance, sin decimales. */
fun budgetPercentLabel(status: BudgetStatus): String =
    "${(status.fraction * 100).roundToInt()}%"

/** "Te quedan $30.000" o "Te pasaste $15.000". */
fun budgetRemainingLabel(status: BudgetStatus): String = if (status.remaining >= 0) {
    "Te quedan ${Money.format(status.remaining)}"
} else {
    "Te pasaste ${Money.format(-status.remaining)}"
}
