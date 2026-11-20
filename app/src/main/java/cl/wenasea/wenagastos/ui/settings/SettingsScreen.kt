package cl.wenasea.wenagastos.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.capture.NotificationAccess
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.domain.numbers
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.asColor

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = VmFactories.settings(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var accessEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }

    // El permiso se activa en Ajustes del sistema: hay que releerlo al volver a la app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessEnabled = NotificationAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Todo se guarda solo en este teléfono",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "Captura automática")
                    Text(
                        text = if (accessEnabled) {
                            "Activa. Wenagastos lee los avisos de Santander, Falabella, " +
                                "MercadoPago y Google Wallet y los anota como movimientos."
                        } else {
                            "Desactivada. Para que los gastos se anoten solos hay que dar " +
                                "acceso a las notificaciones."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accessEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                context.startActivity(NotificationAccess.settingsIntent())
                            },
                        ) {
                            Text(if (accessEnabled) "Revisar permiso" else "Activar acceso")
                        }
                        TextButton(onClick = onOpenDiagnostics) {
                            Text("Ver últimos avisos (${state.capturedCount})")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "Cuentas (${state.accounts.size})")
                    Text(
                        text = "Toca una cuenta para asociar los 4 dígitos que aparecen en sus " +
                            "avisos: los de la tarjeta en una compra y los de la cuenta en una " +
                            "transferencia. Puede tener más de uno.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    state.accounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingAccount = account }
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryAvatar(
                                emoji = account.institution.emoji,
                                color = account.colorHex.asColor(),
                                size = 34,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = buildString {
                                        append(account.institution.label)
                                        append(" · ")
                                        append(account.kind.label)
                                        append(" · ")
                                        append(
                                            account.numbers()
                                                .joinToString(" · ") { "•••• $it" }
                                                .ifEmpty { "sin números" },
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "Categorías (${state.categories.size})")
                    state.categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryAvatar(
                                emoji = category.emoji,
                                color = category.colorHex.asColor(),
                                size = 30,
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = category.kind.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "Privacidad")
                    Text(
                        text = "Wenagastos no tiene permiso de Internet: los datos no pueden salir " +
                            "del teléfono. Lo único que lee son los avisos de las apps del banco, " +
                            "y solo guarda el texto del aviso cuando reconoce un movimiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    editingAccount?.let { account ->
        AccountNumbersDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onSave = { numbers ->
                viewModel.setNumbers(account, numbers)
                editingAccount = null
            },
        )
    }
}

/**
 * Diálogo para administrar los números de 4 dígitos de una cuenta: los de la tarjeta en las
 * compras y los de la cuenta en las transferencias. Se pueden tener varios.
 */
@Composable
private fun AccountNumbersDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val numbers = remember(account.id) {
        mutableStateListOf<String>().apply { addAll(account.numbers()) }
    }
    var draft by remember(account.id) { mutableStateOf("") }

    fun addDraft() {
        val digits = draft.filter(Char::isDigit).take(4)
        if (digits.length == 4 && digits !in numbers) numbers.add(digits)
        draft = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Números de ${account.name}") },
        text = {
            Column {
                Text(
                    text = "Agrega los 4 dígitos que el banco muestra en los avisos de esta " +
                        "cuenta: los de la tarjeta en una compra y los de la cuenta en una " +
                        "transferencia (ej. \"tarjeta terminada en 4321\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (numbers.isEmpty()) {
                    Text(
                        text = "Todavía no tiene números: sus avisos quedarán en Por revisar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                numbers.forEach { number ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "•••• $number",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { numbers.remove(number) }) {
                            Text("Quitar")
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(numbers.toList()) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
