package cl.wenasea.wenagastos.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.EmptyState
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.MonthSwitcher
import cl.wenasea.wenagastos.ui.common.TransactionRow
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.home.colorFor
import cl.wenasea.wenagastos.ui.home.emojiFor
import cl.wenasea.wenagastos.ui.home.subtitleFor
import cl.wenasea.wenagastos.ui.ledger.LedgerViewModel

@Composable
fun MovementsScreen(
    modifier: Modifier = Modifier,
    onOpenTransaction: (Long) -> Unit = {},
) {
    val container = LocalContainer.current
    val viewModel: LedgerViewModel = viewModel(factory = VmFactories.ledger(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val grouped = remember(state.transactions) {
        state.transactions.groupBy { Dates.toLocalDate(it.occurredAt) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "Movimientos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.transactions.size == 1) {
                        "1 movimiento"
                    } else {
                        "${state.transactions.size} movimientos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Gastos ${Money.format(state.expenses)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyState(
                    title = "Sin movimientos",
                    hint = "Cuando agregues un gasto o llegue una notificación del banco, aparecerá acá.",
                )
            }
        } else {
            grouped.forEach { (date, dayTransactions) ->
                item(key = "day-$date") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Dates.dayLabel(dayTransactions.first().occurredAt),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val dayExpenses = dayTransactions
                            .filter { it.direction == Direction.DEBIT && !it.isTransfer }
                            .sumOf { it.amount }
                        Text(
                            text = Money.format(dayExpenses),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(dayTransactions, key = { it.id }) { transaction ->
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
}
