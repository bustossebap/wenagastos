package cl.wenasea.wenagastos.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Ledger
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.AddMovementButton
import cl.wenasea.wenagastos.ui.common.BankLogo
import cl.wenasea.wenagastos.ui.common.EmptyState
import cl.wenasea.wenagastos.ui.common.LedgerRule
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.MonthSwitcher
import cl.wenasea.wenagastos.ui.common.TransactionRow
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.home.colorFor
import cl.wenasea.wenagastos.ui.home.emojiFor
import cl.wenasea.wenagastos.ui.home.subtitleFor
import cl.wenasea.wenagastos.ui.ledger.LedgerViewModel
import java.time.YearMonth

@Composable
fun MovementsScreen(
    modifier: Modifier = Modifier,
    /** Mes con el que abre: llega desde «Ver movimientos» en Inicio. */
    initialMonth: YearMonth? = null,
    /** Cuenta con la que abre: llega desde «Movimientos» en el detalle de Saldos. */
    initialAccountId: Long? = null,
    onOpenTransaction: (Long) -> Unit = {},
    onAddMovement: () -> Unit = {},
) {
    val container = LocalContainer.current
    val viewModel: LedgerViewModel =
        viewModel(factory = VmFactories.ledger(container, initialMonth, initialAccountId))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val grouped = remember(state.transactions) {
        state.transactions.groupBy { Dates.toLocalDate(it.occurredAt) }
    }
    // Las cuentas del filtro, en el orden del cálculo: las archivadas no se ofrecen.
    val filterAccounts = remember(state.accounts) { Ledger.selectable(state.accounts.values) }
    // El subtotal del encabezado sólo aparece si hay algo que sumar: "$0" en cada día que sólo
    // tuvo abonos se lee como un error.
    val expensesLabel = when {
        state.expenses > 0 -> "Gastos ${Money.format(state.expenses)}"
        state.income > 0 -> "Ingresos ${Money.format(state.income)}"
        else -> ""
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Movimientos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.accountName?.let { "Lo que pasó en $it" }
                        ?: "Todo lo que entró y salió este mes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        }

        // Fase 9.2: el mismo mes, visto desde una cuenta. Los chips son cuentas y no categorías:
        // la pregunta es «¿qué se movió en esta cuenta?», no «en qué se fue la plata».
        if (filterAccounts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccountChip(
                        label = "Todas",
                        account = null,
                        selected = state.accountId == null,
                        onClick = { viewModel.selectAccount(null) },
                    )
                    filterAccounts.forEach { account ->
                        AccountChip(
                            label = account.name,
                            account = account,
                            selected = state.accountId == account.id,
                            onClick = { viewModel.selectAccount(account.id) },
                        )
                    }
                }
            }
        }

        item {
            Column {
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
                        text = expensesLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LedgerRule()
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyState(
                    title = "Sin movimientos",
                    hint = if (state.accountId != null) {
                        "Esta cuenta no tuvo movimientos en ${Dates.monthLabel(state.month)}."
                    } else {
                        "Cuando agregues un gasto o llegue una notificación del banco, " +
                            "aparecerá acá."
                    },
                )
            }
        } else {
            grouped.forEach { (date, dayTransactions) ->
                item(key = "day-$date") {
                    Column(modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            if (dayExpenses > 0) {
                                Text(
                                    text = Money.format(dayExpenses),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LedgerRule(modifier = Modifier.padding(top = 6.dp))
                    }
                }
                itemsIndexed(
                    items = dayTransactions,
                    key = { _, transaction -> transaction.id },
                ) { index, transaction ->
                    if (index > 0) LedgerRule()
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

        item {
            AddMovementButton(
                onClick = onAddMovement,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

/**
 * Un chip del filtro: la cuenta con su logo, o *Todas* sin logo. Se dibuja acá y no con
 * `WenaChip` porque este lleva el logo del banco al canto y el otro es solo texto; el resto del
 * estilo —radio de 5 dp, borde de 1 dp, `primary` cuando está elegido— es el mismo.
 */
@Composable
private fun AccountChip(
    label: String,
    account: AccountEntity?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (account != null) {
            BankLogo(
                institution = account.institution,
                colorHex = account.colorHex,
                size = 18,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

