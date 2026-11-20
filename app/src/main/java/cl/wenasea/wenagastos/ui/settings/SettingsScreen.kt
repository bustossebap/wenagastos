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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.capture.NotificationAccess
import cl.wenasea.wenagastos.domain.numbers
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.asColor

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = VmFactories.settings(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<AccountEditTarget?>(null) }
    var accessEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }

    // Guardar, archivar y borrar avisan por `saved`: acá se cierra el diálogo.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            editTarget = null
            viewModel.consumeSaved()
        }
    }

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
                    SectionHeader(title = "Reglas por comercio")
                    Text(
                        text = "Los comercios que ya te aprendiste: la categoría que elegiste se " +
                            "anota sola la próxima vez, incluso si la lista fija diría otra cosa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = onOpenRules) { Text("Ver reglas") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "Cuentas (${state.accounts.size})",
                        actionLabel = "Agregar",
                        onAction = { editTarget = AccountEditTarget(account = null) },
                    )
                    Text(
                        text = "Toca una cuenta para cambiar su nombre, su tipo o los 4 dígitos " +
                            "que aparecen en sus avisos: los de la tarjeta en una compra y los de " +
                            "la cuenta en una transferencia. Puede tener más de uno.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    state.accounts.forEach { account ->
                        val movements = state.movementCount(account)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editTarget = AccountEditTarget(account) }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (account.isActive) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    if (!account.isActive) {
                                        Text(
                                            text = "archivada",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
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
                                        append(" · ")
                                        append(if (movements == 1) "1 movimiento" else "$movements movimientos")
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

    editTarget?.let { target ->
        AccountEditDialog(
            account = target.account,
            movementCount = target.account?.let(state::movementCount) ?: 0,
            error = state.error,
            onDismiss = {
                editTarget = null
                viewModel.clearError()
            },
            onSave = { name, institution, kind, numbers ->
                viewModel.saveAccount(target.account, name, institution, kind, numbers)
            },
            onSetActive = { active ->
                target.account?.let { viewModel.setActive(it, active) }
            },
            onDelete = { target.account?.let(viewModel::removeAccount) },
        )
    }
}

