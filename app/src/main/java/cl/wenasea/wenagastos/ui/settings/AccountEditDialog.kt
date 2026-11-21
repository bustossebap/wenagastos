package cl.wenasea.wenagastos.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.domain.accountNumber
import cl.wenasea.wenagastos.domain.cardNumbers
import cl.wenasea.wenagastos.domain.cardOnly

/** Qué cuenta se está creando ([account] `null`) o editando. */
internal data class AccountEditTarget(val account: AccountEntity?)

/**
 * Alta y edición de una cuenta: nombre, institución, tipo, el número de la cuenta (el de las
 * transferencias) y los de sus tarjetas (los de las compras). Sobre una cuenta existente también
 * se puede archivar (si tiene movimientos), borrar (solo si no tiene, porque la llave foránea se
 * llevaría su historia) o unirla con otra del mismo banco.
 */
@Composable
internal fun AccountEditDialog(
    account: AccountEntity?,
    movementCount: Int,
    mergeCandidates: List<AccountEntity>,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, Institution, AccountKind, String?, List<String>) -> Unit,
    onMerge: (AccountEntity) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var institution by remember(account?.id) {
        mutableStateOf(account?.institution ?: Institution.SANTANDER)
    }
    var kind by remember(account?.id) { mutableStateOf(account?.kind ?: AccountKind.CHECKING) }
    var accountNumber by remember(account?.id) {
        mutableStateOf(
            if (account?.kind?.cardOnly == true) {
                account.cardNumbers().firstOrNull().orEmpty()
            } else {
                account?.accountNumber().orEmpty()
            },
        )
    }
    // En una tarjeta el primer número va en su campo y el resto queda acá, para no perder ninguno
    // si el tipo cambia o el editor no muestra la lista.
    val cards = remember(account?.id) {
        mutableStateListOf<String>().apply {
            val existing = account ?: return@apply
            addAll(
                if (existing.kind.cardOnly) {
                    existing.cardNumbers().drop(1)
                } else {
                    existing.cardNumbers()
                },
            )
        }
    }
    var draft by remember(account?.id) { mutableStateOf("") }
    var confirmingDelete by remember(account?.id) { mutableStateOf(false) }

    fun addDraft() {
        val digits = draft.filter(Char::isDigit).take(4)
        if (digits.length == 4 && digits !in cards) cards.add(digits)
        draft = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (account == null) "Nueva cuenta" else "Editar ${account.name}")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (confirmingDelete) {
                    Text(
                        text = if (movementCount > 0) {
                            "${account?.name} tiene ${movementsLabel(movementCount)}. Para no " +
                                "perder la historia se archiva: deja de ofrecerse al anotar y " +
                                "sus avisos nuevos quedan en Por revisar."
                        } else {
                            "${account?.name} no tiene movimientos: se borra de forma definitiva."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onDelete) {
                            Text(
                                text = if (movementCount > 0) "Archivar" else "Borrar",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = { confirmingDelete = false }) { Text("Volver") }
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Institución",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Institution.entries.forEach { option ->
                            FilterChip(
                                selected = option == institution,
                                onClick = { institution = option },
                                label = { Text("${option.emoji} ${option.label}") },
                            )
                        }
                    }

                    Text(
                        text = "Tipo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AccountKind.entries.forEach { option ->
                            FilterChip(
                                selected = option == kind,
                                onClick = { kind = option },
                                label = { Text(option.label) },
                            )
                        }
                    }

                    val cardOnly = kind.cardOnly
                    Text(
                        text = if (cardOnly) "Número de la tarjeta" else "Número de la cuenta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = if (cardOnly) {
                            "Los 4 dígitos que el banco muestra en los avisos de esta tarjeta " +
                                "(ej. \"tarjeta terminada en 4321\")."
                        } else {
                            "Los 4 dígitos de la cuenta en una transferencia (ej. \"cuenta " +
                                "000095441483\" → 1483). Es uno solo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { input ->
                            accountNumber = input.filter(Char::isDigit).take(4)
                        },
                        label = { Text("Últimos 4 dígitos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )

                    if (!cardOnly) {
                        Text(
                            text = "Tarjetas de débito de esta cuenta",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = "La plata de la tarjeta es la de la cuenta, así que no es " +
                                "otra cuenta: acá van los 4 dígitos de cada tarjeta que aparece " +
                                "en un aviso de compra.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (cards.isEmpty()) {
                            Text(
                                text = "Sin tarjetas: las compras con débito quedarán en Por " +
                                    "revisar mientras el número no esté acá.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        cards.forEach { number ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "•••• $number",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { cards.remove(number) }) { Text("Quitar") }
                            }
                        }
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { input ->
                                    draft = input.filter(Char::isDigit).take(4)
                                },
                                label = { Text("Últimos 4 de la tarjeta") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { addDraft() }, enabled = draft.length == 4) {
                                Text("Agregar")
                            }
                        }
                    }

                    error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (account != null && mergeCandidates.isNotEmpty()) {
                        Text(
                            text = "Unir con otra cuenta",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = "En el banco la tarjeta de débito no es otra cuenta: gasta " +
                                "de la misma. Al unirlas, esta se queda con los movimientos y " +
                                "las tarjetas de la otra, y la otra desaparece.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        mergeCandidates.forEach { candidate ->
                            TextButton(onClick = { onMerge(candidate) }) {
                                Text("Unir con ${candidate.name}")
                            }
                        }
                    }

                    if (account != null) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { onSetActive(!account.isActive) }) {
                                Text(if (account.isActive) "Archivar" else "Reactivar")
                            }
                            TextButton(onClick = { confirmingDelete = true }) {
                                Text("Borrar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!confirmingDelete) {
                TextButton(
                    onClick = {
                        onSave(
                            name,
                            institution,
                            kind,
                            accountNumber.ifBlank { null },
                            cards.toList(),
                        )
                    },
                ) {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            if (!confirmingDelete) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

private fun movementsLabel(count: Int): String =
    if (count == 1) "1 movimiento" else "$count movimientos"
