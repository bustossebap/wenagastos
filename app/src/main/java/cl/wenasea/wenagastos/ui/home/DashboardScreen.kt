package cl.wenasea.wenagastos.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.domain.CategoryTotal
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.balances.BalancesSummaryCard
import cl.wenasea.wenagastos.ui.common.AddMovementButton
import cl.wenasea.wenagastos.ui.common.BackupDueCard
import cl.wenasea.wenagastos.ui.common.BudgetProgress
import cl.wenasea.wenagastos.ui.common.CaptureOffCard
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.HeroBlock
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.MonthSwitcher
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.ShareBar
import cl.wenasea.wenagastos.ui.common.TransactionRow
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.asColor
import cl.wenasea.wenagastos.ui.common.budgetColor
import cl.wenasea.wenagastos.ui.common.budgetPercentLabel
import cl.wenasea.wenagastos.ui.common.budgetRemainingLabel
import cl.wenasea.wenagastos.ui.common.categoryColor
import cl.wenasea.wenagastos.ui.common.categoryEmoji
import cl.wenasea.wenagastos.ui.common.transactionSubtitle
import cl.wenasea.wenagastos.ui.ledger.LedgerUiState
import cl.wenasea.wenagastos.ui.ledger.LedgerViewModel
import java.time.YearMonth

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onSeeAllMovements: (YearMonth) -> Unit = {},
    onAddMovement: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
    onSeeReports: () -> Unit = {},
    onOpenBalances: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val container = LocalContainer.current
    val viewModel: LedgerViewModel = viewModel(factory = VmFactories.ledger(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // El encabezado de la lista dice de qué mes habla y cuánto se está viendo. Antes decía «Ver
    // todos» y abría Movimientos en el mes de hoy aunque arriba estuviera otro mes: el botón
    // parecía no hacer nada.
    val total = state.transactions.size
    val shown = total.coerceAtMost(HOME_MOVEMENTS_SHOWN)
    val movementsSubtitle = when {
        total == 0 -> Dates.monthLabel(state.month)
        total == 1 -> "1 movimiento · ${Dates.monthLabel(state.month)}"
        total <= HOME_MOVEMENTS_SHOWN -> "$total movimientos · ${Dates.monthLabel(state.month)}"
        else -> "$shown de $total movimientos · ${Dates.monthLabel(state.month)}"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Wenagastos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Tus gastos, solo en este teléfono",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { CaptureOffCard() }

        item { BackupDueCard(onOpenSettings = onOpenSettings) }

        item {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        }

        item {
            HeroBlock(
                label = "Gastos del mes",
                amount = Money.format(state.expenses),
                stats = listOf(
                    "Ingresos" to Money.format(state.income),
                    "Balance" to Money.format(state.balance),
                    "Por revisar" to state.pendingCount.toString(),
                ),
            )
        }

        item { BalancesSummaryCard(onOpen = onOpenBalances) }

        if (state.budgetAlerts.isNotEmpty()) {
            item { BudgetAlertsCard(alerts = state.budgetAlerts, onSeeReports = onSeeReports) }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "En qué se fue la plata")
                    if (state.slices.isEmpty()) {
                        Text(
                            text = "Aún no hay gastos este mes. Agrega uno con «Agregar " +
                                "movimiento», al final de la lista.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        state.slices.take(5).forEach { slice ->
                            val color = slice.category?.colorHex?.asColor() ?: Color.Gray
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CategoryAvatar(
                                    emoji = slice.category?.emoji ?: "❓",
                                    color = color,
                                    size = 30,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = slice.category?.name ?: "Sin categoría",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = Money.format(slice.total),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    ShareBar(share = slice.share, color = color)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Últimos movimientos",
                subtitle = movementsSubtitle,
                actionLabel = if (state.transactions.isEmpty()) null else "Ver movimientos",
                onAction = { onSeeAllMovements(state.month) },
            )
        }

        if (state.transactions.isEmpty()) {
            item {
                Text(
                    text = "Sin movimientos en ${Dates.monthLabel(state.month)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.transactions.take(HOME_MOVEMENTS_SHOWN), key = { it.id }) { transaction ->
                TransactionRow(
                    emoji = state.emojiFor(transaction),
                    emojiColor = state.colorFor(transaction),
                    title = transaction.merchantRaw,
                    subtitle = state.subtitleFor(transaction),
                    direction = transaction.direction,
                    amount = transaction.amount,
                    onClick = { onOpenTransaction(transaction.id) },
                )
            }
        }

        item {
            AddMovementButton(
                onClick = onAddMovement,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Cuántos movimientos se ven en Inicio antes de ir a la lista completa. */
private const val HOME_MOVEMENTS_SHOWN = 6

internal fun LedgerUiState.emojiFor(transaction: TransactionEntity): String =
    categoryEmoji(transaction, categories)

internal fun LedgerUiState.colorFor(transaction: TransactionEntity): Color =
    categoryColor(transaction, categories)

internal fun LedgerUiState.subtitleFor(transaction: TransactionEntity): String =
    transactionSubtitle(transaction, categories, accounts)

/**
 * Aviso de presupuestos en el inicio: solo aparece cuando alguna categoría del mes llegó al 80 %
 * del tope. Los números son los mismos que en Reportes, así que editar o borrar un movimiento
 * corrige el aviso al instante.
 */
@Composable
private fun BudgetAlertsCard(alerts: List<CategoryTotal>, onSeeReports: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                title = "Presupuestos",
                actionLabel = "Ver reportes",
                onAction = onSeeReports,
            )
            alerts.forEach { total ->
                val status = total.budget ?: return@forEach
                val levelColor = budgetColor(status.level)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategoryAvatar(
                        emoji = total.category?.emoji ?: "❓",
                        color = total.category?.colorHex?.asColor() ?: Color.Gray,
                        size = 30,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = total.category?.name ?: "Sin categoría",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = budgetPercentLabel(status),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = levelColor,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        BudgetProgress(status = status)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = budgetRemainingLabel(status),
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor,
                        )
                    }
                }
            }
        }
    }
}

