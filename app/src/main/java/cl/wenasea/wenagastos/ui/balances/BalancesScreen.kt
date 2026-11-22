package cl.wenasea.wenagastos.ui.balances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.domain.AccountBalance
import cl.wenasea.wenagastos.domain.CreditUsage
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.InstitutionBalance
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.EmptyState
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.asColor

/**
 * Saldos por banco y por cuenta (Fase 7).
 *
 * Arriba el patrimonio —la suma de lo que se sabe—, después una tarjeta por banco con sus cuentas.
 * Cada cuenta muestra el saldo calculado y de dónde sale (el corte y lo que se movió después), y
 * permite escribir el saldo que el banco dice hoy. Lo que no tiene corte dice *Sin saldo*: no es
 * cero, es que no se sabe, y contarlo como cero sería mentir.
 */
@Composable
fun BalancesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val viewModel: BalancesViewModel = viewModel(factory = VmFactories.balances(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountBalance?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text("‹ Inicio", fontSize = 14.sp)
                }
                Text(
                    text = "Saldos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "El saldo se calcula con el último saldo que escribiste más los " +
                        "movimientos posteriores. No se suma todo el historial: los avisos " +
                        "empiezan el día que instalaste la app y lo que nunca llegó no está.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { TotalCard(state = state) }

        if (state.accountCount == 0) {
            item {
                EmptyState(
                    title = "Sin cuentas activas",
                    hint = "Crea una cuenta en Ajustes para empezar a anotar saldos.",
                )
            }
        }

        items(state.institutions, key = { it.institution.name }) { group ->
            InstitutionCard(
                group = group,
                monthSpend = state.monthSpend,
                credit = state.credit,
                onEdit = { editing = it },
            )
        }

        item {
            Text(
                text = "Un traspaso entre cuentas propias mueve el saldo de cada una pero no el " +
                    "total, y no cuenta como gasto del mes: esa plata no se gastó, cambió de cuenta.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    editing?.let { target ->
        BalanceDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { text ->
                val saved = viewModel.saveBalance(target.account.id, text)
                if (saved) editing = null
                saved
            },
            onClear = {
                viewModel.clearBalance(target.account.id)
                editing = null
            },
        )
    }
}

@Composable
private fun TotalCard(state: BalancesUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Patrimonio conocido",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = if (state.hasBalances) Money.format(state.total) else "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = when {
                    state.accountCount == 0 -> "Sin cuentas todavía."
                    !state.hasBalances -> "Ninguna cuenta tiene saldo escrito. Toca una cuenta " +
                        "y escribe lo que el banco dice hoy."
                    state.unknownCount == 0 ->
                        "${state.knownCount} de ${state.accountCount} cuentas con saldo."
                    else -> "${state.knownCount} de ${state.accountCount} cuentas con saldo; " +
                        "las otras ${state.unknownCount} no suman hasta que escribas uno."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun InstitutionCard(
    group: InstitutionBalance,
    monthSpend: Map<Long, Long>,
    credit: Map<Long, CreditUsage>,
    onEdit: (AccountBalance) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CategoryAvatar(
                    emoji = group.institution.emoji,
                    color = group.institution.colorHex.asColor(),
                    size = 30,
                )
                Text(
                    text = group.institution.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (group.known > 0) Money.format(group.total) else "Sin saldo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (group.known > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            group.accounts.forEach { balance ->
                AccountRow(
                    balance = balance,
                    monthSpend = monthSpend[balance.account.id] ?: 0L,
                    usage = credit[balance.account.id],
                    onEdit = { onEdit(balance) },
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    balance: AccountBalance,
    monthSpend: Long,
    usage: CreditUsage?,
    onEdit: () -> Unit,
) {
    val debt = balance.account.kind == AccountKind.CREDIT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = balance.account.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = balance.account.kind.label + " · " + sourceLabel(balance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (monthSpend > 0) {
                    "Gastado este mes: ${Money.format(monthSpend)}"
                } else {
                    "Sin gastos este mes"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (usage != null) CreditLines(usage = usage, limit = balance.account.creditLimit)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = balance.balance?.let(Money::format) ?: "Sin saldo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    balance.balance == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    balance.balance!! < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (debt) {
                Text(
                    text = "Deuda",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("Saldo", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Lo de una tarjeta de crédito (Fase 7.1): cuánto cupo queda y cómo va el ciclo que corre. Son dos
 * números de distinta confianza —el disponible sale de la deuda que dijo el banco, las compras del
 * ciclo salen de los avisos que llegaron— y por eso van en líneas separadas.
 */
@Composable
private fun CreditLines(usage: CreditUsage, limit: Long?) {
    if (limit != null) {
        val available = usage.available
        Text(
            text = if (available != null) {
                "Cupo ${Money.format(limit)} · disponible ${Money.format(available)}"
            } else {
                "Cupo ${Money.format(limit)} · falta el saldo para saber cuánto queda"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (available != null && available < 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    val cycle = usage.cycle ?: return
    Text(
        text = "Ciclo ${Dates.shortDateLabel(Dates.toLocalDate(cycle.start))} → " +
            "${Dates.shortDateLabel(cycle.closing)}: ${Money.format(usage.spent)} en compras",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    cycle.due?.let { due ->
        Text(
            text = "El pago de esa factura vence el ${Dates.shortDateLabel(due)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "saldo al 21-11-2026 (a mano), 3 movimientos después" o "sin saldo escrito". */
private fun sourceLabel(balance: AccountBalance): String {
    val snapshot = balance.snapshot ?: return "sin saldo escrito"
    val origin = snapshot.source.label.lowercase()
    val movementLabel = when (balance.movements) {
        0 -> "sin movimientos después"
        1 -> "1 movimiento después"
        else -> "${balance.movements} movimientos después"
    }
    return "saldo al ${Dates.fullDateLabel(snapshot.asOf)} ($origin), $movementLabel"
}

@Composable
private fun BalanceDialog(
    target: AccountBalance,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
    onClear: () -> Unit,
) {
    var text by remember(target.account.id) {
        mutableStateOf(target.balance?.toString().orEmpty())
    }
    var problem by remember(target.account.id) { mutableStateOf<String?>(null) }
    val debt = target.account.kind == AccountKind.CREDIT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saldo de ${target.account.name}") },
        text = {
            Column {
                Text(
                    text = if (debt) {
                        "Escribe lo que debes hoy. Si la tarjeta está al día, el saldo es 0."
                    } else {
                        "Escribe lo que el banco dice que hay hoy. Lo que ya está anotado " +
                            "antes de este momento queda dentro de ese saldo y no se suma dos veces."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (debt) "Deuda de hoy" else "Saldo de hoy") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                problem?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = "Un saldo negativo es una deuda: escribe -45000 si la tarjeta está " +
                        "usada o la cuenta en rojo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    problem = if (onSave(text)) {
                        null
                    } else {
                        "Escribe el saldo: por ejemplo 1.250.000 o -45.000"
                    }
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (target.known) {
                    TextButton(onClick = onClear) {
                        Text("Quitar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
