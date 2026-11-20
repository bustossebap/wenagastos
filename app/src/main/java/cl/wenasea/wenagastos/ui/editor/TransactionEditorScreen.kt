package cl.wenasea.wenagastos.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.VmFactories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    txId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val viewModel: EditorViewModel = viewModel(factory = VmFactories.editor(container, txId))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var amountText by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableLongStateOf(-1L) }
    var categoryId by rememberSaveable { mutableLongStateOf(-1L) }
    var direction by rememberSaveable { mutableStateOf(Direction.DEBIT) }
    var isTransfer by rememberSaveable { mutableStateOf(false) }
    var occurredAt by rememberSaveable { mutableLongStateOf(0L) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.transaction) {
        val stored = state.transaction ?: return@LaunchedEffect
        if (!prefilled) {
            amountText = stored.amount.toString()
            merchant = stored.merchantRaw
            accountId = stored.accountId
            categoryId = stored.categoryId ?: -1L
            direction = stored.direction
            isTransfer = stored.isTransfer
            occurredAt = stored.occurredAt
            prefilled = true
        }
    }

    LaunchedEffect(state.accounts) {
        if (accountId <= 0 && state.accounts.isNotEmpty()) {
            accountId = state.accounts.first().id
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    val visibleCategories = state.categories.filter { category ->
        if (isTransfer) {
            // Un traspaso no es gasto ni ingreso: solo se ofrecen las categorías de traspaso.
            category.kind == CategoryKind.TRANSFER
        } else {
            category.kind ==
                if (direction == Direction.DEBIT) CategoryKind.EXPENSE else CategoryKind.INCOME
        }
    }
    val effectiveCategoryId = categoryId.takeIf { id ->
        id > 0 && visibleCategories.any { it.id == id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("Cancelar") }
            Text(
                text = if (state.isEditing) "Editar movimiento" else "Nuevo movimiento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        val pendingNote = state.transaction
            ?.takeIf { it.status == TransactionStatus.PENDING }
            ?.notes
        if (pendingNote != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Por revisar: $pendingNote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = direction == Direction.DEBIT,
                onClick = { direction = Direction.DEBIT },
                label = { Text("Gasto") },
            )
            FilterChip(
                selected = direction == Direction.CREDIT,
                onClick = { direction = Direction.CREDIT },
                label = { Text("Ingreso") },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Traspaso entre mis cuentas",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Mueve plata de una cuenta a otra: no suma en gastos ni en ingresos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isTransfer,
                onCheckedChange = { checked ->
                    isTransfer = checked
                    if (checked) {
                        // Al marcarlo, la categoría de traspaso es la única que tiene sentido.
                        state.categories.firstOrNull { it.kind == CategoryKind.TRANSFER }
                            ?.let { categoryId = it.id }
                    }
                },
            )
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                amountText = input.filter { it.isDigit() }.take(12)
            },
            label = { Text("Monto") },
            prefix = { Text("$") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it.take(60) },
            label = { Text("¿Dónde / en qué?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Fecha",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showDatePicker = true }) {
                Text(
                    text = Dates.fullDateLabel(
                        if (occurredAt > 0L) occurredAt else Dates.todayMillis(),
                    ),
                )
            }
        }

        if (showDatePicker) {
            val initialDate = if (occurredAt > 0L) occurredAt else Dates.todayMillis()
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = Dates.toPickerMillis(initialDate),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { picked ->
                                occurredAt = Dates.fromPickerMillis(picked)
                            }
                            showDatePicker = false
                        },
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancelar")
                    }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }

        Text(
            text = "Cuenta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.accounts, key = { it.id }) { account ->
                FilterChip(
                    selected = accountId == account.id,
                    onClick = { accountId = account.id },
                    label = { Text(account.name) },
                )
            }
        }

        Text(
            text = "Categoría",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleCategories, key = { it.id }) { category ->
                FilterChip(
                    selected = effectiveCategoryId == category.id,
                    onClick = {
                        categoryId = if (categoryId == category.id) -1L else category.id
                    },
                    label = { Text("${category.emoji} ${category.name}") },
                )
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.save(
                    amountText = amountText,
                    merchant = merchant,
                    accountId = accountId.takeIf { it > 0 },
                    categoryId = effectiveCategoryId,
                    direction = direction,
                    isTransfer = isTransfer,
                    occurredAtMillis = if (occurredAt > 0L) occurredAt else Dates.todayMillis(),
                )
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isEditing) "Guardar cambios" else "Guardar")
        }

        if (state.isEditing) {
            TextButton(
                onClick = { viewModel.delete(onDone) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Eliminar movimiento", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
