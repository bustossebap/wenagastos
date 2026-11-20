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
import androidx.compose.material3.CardDefaults
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
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.CaptureOffCard
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.MonthSwitcher
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.ShareBar
import cl.wenasea.wenagastos.ui.common.TransactionRow
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.asColor
import cl.wenasea.wenagastos.ui.ledger.LedgerUiState
import cl.wenasea.wenagastos.ui.ledger.LedgerViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onSeeAllMovements: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
) {
    val container = LocalContainer.current
    val viewModel: LedgerViewModel = viewModel(factory = VmFactories.ledger(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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

        item {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        }

        item { MonthSummaryCard(state) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "En qué se fue la plata")
                    if (state.slices.isEmpty()) {
                        Text(
                            text = "Aún no hay gastos este mes. Agrega uno con el botón +.",
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
                actionLabel = if (state.transactions.isEmpty()) null else "Ver todos",
                onAction = onSeeAllMovements,
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
            items(state.transactions.take(6), key = { it.id }) { transaction ->
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
    }
}

@Composable
private fun MonthSummaryCard(state: LedgerUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Gastos del mes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = Money.format(state.expenses),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MiniStat(
                    label = "Ingresos",
                    value = Money.format(state.income),
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Balance",
                    value = Money.format(state.balance),
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Por revisar",
                    value = state.pendingCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

internal fun LedgerUiState.emojiFor(transaction: TransactionEntity): String =
    transaction.categoryId?.let { categories[it]?.emoji } ?: "❓"

internal fun LedgerUiState.colorFor(transaction: TransactionEntity): Color =
    transaction.categoryId?.let { categories[it]?.colorHex?.asColor() } ?: Color.Gray

internal fun LedgerUiState.subtitleFor(transaction: TransactionEntity): String {
    val category = transaction.categoryId?.let { categories[it]?.name } ?: "Sin categoría"
    val account = accounts[transaction.accountId]?.name ?: "Cuenta"
    return "$category · $account"
}
