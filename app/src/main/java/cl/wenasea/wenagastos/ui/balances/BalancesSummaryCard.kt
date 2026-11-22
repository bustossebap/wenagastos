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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.VmFactories

/**
 * Tarjeta de saldos del inicio: el patrimonio y una fila por banco. Toca «Ver cuentas» para el
 * detalle y para escribir el saldo que el banco muestra hoy.
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

            if (!state.hasBalances) {
                Text(
                    text = "Todavía no sabes cuánto tienes. En el detalle puedes escribir el " +
                        "saldo que muestra el banco: desde ahí se mueve solo con cada movimiento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
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

                state.institutions.filter { it.known > 0 }.forEach { group ->
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = group.institution.emoji,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = group.institution.label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Money.format(group.total),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (state.unknownCount > 0) {
                    Text(
                        text = if (state.unknownCount == 1) {
                            "1 cuenta sin saldo no suma."
                        } else {
                            "${state.unknownCount} cuentas sin saldo no suman."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
