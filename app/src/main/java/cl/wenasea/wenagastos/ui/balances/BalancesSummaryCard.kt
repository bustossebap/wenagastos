package cl.wenasea.wenagastos.ui.balances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.domain.AccountBalance
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.BankLogo
import cl.wenasea.wenagastos.ui.common.LedgerRule
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.VmFactories

/**
 * Tarjeta de saldos del inicio, en dos bloques que no se mezclan.
 *
 * Arriba, **la plata**: una fila por cuenta de efectivo (cta. cte., ahorro/billetera, efectivo) y
 * el patrimonio como la suma de esa plata. Abajo, **lo que se debe**: una fila por tarjeta de
 * crédito con su deuda y el cupo disponible sumado. La deuda de una tarjeta no se resta del
 * patrimonio —no es plata que se haya ido de la cuenta, es una compra que se paga después y tiene
 * su propio cupo—, así que el patrimonio es lo que se puede girar.
 *
 * Sin ningún saldo escrito igual se muestra: es la única puerta para empezar a anotarlos.
 */
@Composable
fun BalancesSummaryCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val viewModel: BalancesViewModel = viewModel(factory = VmFactories.balances(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Sin cuentas no hay nada que decir: la tarjeta aparecería vacía en Ajustes recién instalado.
    if (state.loaded && state.accountCount == 0) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                title = "Saldos",
                actionLabel = "Ver cuentas",
                onAction = onOpen,
            )

            if (state.hasBalances) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Patrimonio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Money.format(state.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = "Todavía no sabes cuánto tienes. En el detalle puedes escribir el " +
                        "saldo que muestra el banco: desde ahí se mueve solo con cada movimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            state.cash.forEach { row ->
                AccountBalanceLine(row = row)
            }

            val unknown = state.unknownCount
            if (state.hasBalances && unknown > 0) {
                Text(
                    text = if (unknown == 1) {
                        "1 cuenta sin saldo no suma."
                    } else {
                        "$unknown cuentas sin saldo no suman."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (state.hasCards) {
                LedgerRule(modifier = Modifier.padding(top = 14.dp))
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tarjetas de crédito",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = creditLabel(state),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.creditKnown == 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                state.cards.forEach { row ->
                    CardBalanceLine(row = row, available = state.credit[row.account.id]?.available)
                }
                if (state.creditKnown < state.cards.size) {
                    Text(
                        text = "El cupo disponible suma solo las tarjetas con cupo y deuda anotados.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** «Cupo $2.126.964», o el aviso de que todavía no se sabe. */
private fun creditLabel(state: BalancesUiState): String = if (state.creditKnown == 0) {
    "Cupo sin anotar"
} else {
    "Cupo ${Money.format(state.creditAvailable)}"
}

/**
 * Una cuenta de efectivo: logo del banco, nombre y saldo. La cuenta archivada ya no llega hasta
 * acá —el cálculo la filtra— y la que nunca tuvo corte dice «Sin saldo» en gris: un `$0` sería
 * mentira.
 */
@Composable
private fun AccountBalanceLine(row: AccountBalance) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BankLogo(
            institution = row.account.institution,
            colorHex = row.account.colorHex,
            size = 22,
        )
        Text(
            text = row.account.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.balance?.let(Money::format) ?: "Sin saldo",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                row.balance == null -> MaterialTheme.colorScheme.onSurfaceVariant
                row.balance!! < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Una tarjeta de crédito: la deuda en rojo —es plata que se debe— y, debajo, lo que queda de cupo.
 * Sin deuda escrita no se inventa un cupo completo: se dice qué falta.
 */
@Composable
private fun CardBalanceLine(row: AccountBalance, available: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BankLogo(
            institution = row.account.institution,
            colorHex = row.account.colorHex,
            size = 22,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.account.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    row.balance == null -> "Sin deuda anotada"
                    available == null -> "Cupo sin configurar"
                    else -> "Disponible ${Money.format(available)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = row.balance?.let { balance ->
                Money.format(if (balance < 0) -balance else balance)
            } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                row.balance == null -> MaterialTheme.colorScheme.onSurfaceVariant
                row.balance!! < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

