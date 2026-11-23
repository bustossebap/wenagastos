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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.domain.accountNumber
import cl.wenasea.wenagastos.domain.cardNumbers
import cl.wenasea.wenagastos.ui.common.BankLogo
import cl.wenasea.wenagastos.ui.common.LedgerRule
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.VmFactories

@Composable
fun SettingsScreen(
    onOpenRules: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val viewModel: SettingsViewModel = viewModel(factory = VmFactories.settings(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<AccountEditTarget?>(null) }

    // Guardar, archivar y borrar avisan por `saved`: acá se cierra el diálogo.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            editTarget = null
            viewModel.consumeSaved()
        }
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
            DataCard(
                state = state,
                onExportCsv = viewModel::exportCsv,
                onExportJson = viewModel::exportJson,
                onCreateBackup = viewModel::createBackup,
                onRestoreBackup = viewModel::restoreBackup,
                onImportStatement = viewModel::importStatement,
                onUndoImport = viewModel::undoLastImport,
            )
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
                        text = "Cada cuenta tiene el número que aparece en las transferencias y " +
                            "los de sus tarjetas de débito: la plata de la tarjeta es la de la " +
                            "cuenta, así que no se anota aparte. Una tarjeta de crédito sí es " +
                            "una cuenta propia, porque se debe aparte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    state.accounts.forEachIndexed { index, account ->
                        val movements = state.movementCount(account)
                        if (index > 0) LedgerRule(modifier = Modifier.padding(top = 12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editTarget = AccountEditTarget(account) }
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BankLogo(
                                institution = account.institution,
                                colorHex = account.colorHex,
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
                                        append(account.numbersLabel())
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
            LockCard(lock = container.appLock)
        }

        item {
            ThemeCard()
        }
    }

    editTarget?.let { target ->
        val editing = target.account
        AccountEditDialog(
            account = editing,
            movementCount = editing?.let(state::movementCount) ?: 0,
            mergeCandidates = editing?.let { account -> mergeCandidates(state.accounts, account) }
                .orEmpty(),
            error = state.error,
            onDismiss = {
                editTarget = null
                viewModel.clearError()
            },
            onSave = { name, institution, kind, accountNumber, cards, credit ->
                viewModel.saveAccount(
                    editing,
                    name,
                    institution,
                    kind,
                    accountNumber,
                    cards,
                    credit,
                )
            },
            onMerge = { candidate ->
                editing?.let { viewModel.previewMerge(it, candidate) }
            },
            onSetActive = { active ->
                editing?.let { viewModel.setActive(it, active) }
            },
            onDelete = { editing?.let(viewModel::removeAccount) },
        )
    }

    // Unir dos cuentas mueve historia y borra traspasos internos: se confirma con el resumen.
    state.mergePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = viewModel::cancelMerge,
            title = { Text("Unir cuentas") },
            text = { Text(plan.describe()) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmMerge) { Text("Unir") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMerge) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Con quién se puede unir [account]: misma institución y misma familia de saldo. Una tarjeta de
 * crédito se debe aparte, así que no se mezcla con una cuenta corriente ni con una de débito.
 */
private fun mergeCandidates(
    accounts: List<AccountEntity>,
    account: AccountEntity,
): List<AccountEntity> = accounts.filter { candidate ->
    candidate.id != account.id &&
        candidate.isActive &&
        candidate.institution == account.institution &&
        (candidate.kind == AccountKind.CREDIT) == (account.kind == AccountKind.CREDIT)
}

/** «•••• 1483 + débito •••• 4321»: la cuenta primero y después las tarjetas que gastan de ella. */
private fun AccountEntity.numbersLabel(): String {
    val account = accountNumber()
    val cards = cardNumbers()
    return when {
        kind == AccountKind.CREDIT ->
            cards.joinToString(" · ") { "•••• $it" }.ifEmpty { "sin números" }
        account == null && cards.isEmpty() -> "sin números"
        account == null -> cards.joinToString(" · ") { "•••• $it" }
        cards.isEmpty() -> "•••• $account"
        else -> "•••• $account + débito " + cards.joinToString(" · ") { "•••• $it" }
    }
}

