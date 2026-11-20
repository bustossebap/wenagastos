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
import cl.wenasea.wenagastos.domain.numbers

/** Qué cuenta se está creando ([account] `null`) o editando. */
internal data class AccountEditTarget(val account: AccountEntity?)

/**
 * Alta y edición de una cuenta: nombre, institución, tipo y los números que aparecen en los
 * avisos. Sobre una cuenta existente también se puede archivar (si tiene movimientos) o borrar
 * (solo si no tiene, porque la llave foránea se llevaría su historia).
 */
@Composable
internal fun AccountEditDialog(
    account: AccountEntity?,
    movementCount: Int,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, Institution, AccountKind, List<String>) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var institution by remember(account?.id) {
        mutableStateOf(account?.institution ?: Institution.SANTANDER)
    }
    var kind by remember(account?.id) { mutableStateOf(account?.kind ?: AccountKind.CHECKING) }
    val numbers = remember(account?.id) {
        mutableStateListOf<String>().apply { addAll(account?.numbers().orEmpty()) }
    }
    var draft by remember(account?.id) { mutableStateOf("") }
    var confirmingDelete by remember(account?.id) { mutableStateOf(false) }

    fun addDraft() {
        val digits = draft.filter(Char::isDigit).take(4)
        if (digits.length == 4 && digits !in numbers) numbers.add(digits)
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

                    Text(
                        text = "Números de los avisos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "Los 4 dígitos que el banco muestra en los avisos de esta cuenta: " +
                            "los de la tarjeta en una compra y los de la cuenta en una " +
                            "transferencia (ej. \"tarjeta terminada en 4321\").",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (numbers.isEmpty()) {
                        Text(
                            text = "Sin números: sus avisos quedarán en Por revisar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    numbers.forEach { number ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "•••• $number",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { numbers.remove(number) }) { Text("Quitar") }
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
                            label = { Text("Últimos 4 dígitos") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { addDraft() }, enabled = draft.length == 4) {
                            Text("Agregar")
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
                TextButton(onClick = { onSave(name, institution, kind, numbers.toList()) }) {
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
