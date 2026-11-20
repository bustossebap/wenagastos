package cl.wenasea.wenagastos.ui.editor

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Checkbox
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
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.domain.transferColumns
import cl.wenasea.wenagastos.domain.transferEnds
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
    // Cuentas del traspaso: el origen y el destino se eligen aparte del sentido del movimiento.
    var originId by rememberSaveable { mutableLongStateOf(-1L) }
    var destinationId by rememberSaveable { mutableLongStateOf(-1L) }
    var createCounterpart by rememberSaveable { mutableStateOf(true) }
    // Fase 3: por defecto NO se aprende nada; el usuario marca la casilla a propósito.
    var rememberRule by rememberSaveable { mutableStateOf(false) }

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
            if (stored.isTransfer) {
                val ends = stored.transferEnds()
                originId = ends.originId ?: -1L
                destinationId = ends.destinationId ?: -1L
            }
            prefilled = true
        }
    }

    LaunchedEffect(state.accounts, isTransfer) {
        if (accountId <= 0 && state.accounts.isNotEmpty()) {
            accountId = state.accounts.first().id
        }
        if (isTransfer && state.accounts.isNotEmpty()) {
            // Si venía de un movimiento normal, su cuenta es el origen del traspaso.
            if (originId <= 0) originId = accountId.takeIf { it > 0 } ?: state.accounts.first().id
            if (destinationId <= 0) {
                destinationId = state.accounts.firstOrNull { it.id != originId }?.id ?: -1L
            }
        }
    }

    // Consulta si la otra pata ya está anotada cada vez que cambia algo que la identifica.
    LaunchedEffect(isTransfer, originId, destinationId, direction, amountText, occurredAt) {
        if (isTransfer) {
            viewModel.checkCounterpart(
                amountText = amountText,
                direction = direction,
                accountId = originId.takeIf { it > 0 },
                transferAccountId = destinationId.takeIf { it > 0 },
                occurredAtMillis = if (occurredAt > 0L) occurredAt else Dates.todayMillis(),
            )
        }
    }

    // La casilla viene marcada solo si falta la otra pata: si ya está, se enlaza y no se duplica.
    LaunchedEffect(state.counterpartExists) {
        createCounterpart = !state.counterpartExists
    }

    // ¿Este comercio ya tiene una categoría recordada? (un traspaso no aprende reglas)
    LaunchedEffect(merchant, direction, isTransfer) {
        if (isTransfer) {
            rememberRule = false
        }
        viewModel.checkRule(if (isTransfer) "" else merchant, direction)
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
    // La regla guardada ya apunta a la categoría elegida: no hay nada nuevo que aprender.
    val alreadyRemembered =
        state.rememberedCategoryId != null && state.rememberedCategoryId == effectiveCategoryId

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

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = direction == Direction.DEBIT,
                onClick = { direction = Direction.DEBIT },
                label = { Text(if (isTransfer) "Sale de mi cuenta" else "Gasto") },
            )
            FilterChip(
                selected = direction == Direction.CREDIT,
                onClick = { direction = Direction.CREDIT },
                label = { Text(if (isTransfer) "Entra a mi cuenta" else "Ingreso") },
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

        if (isTransfer) {
            Text(
                text = "Origen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.accounts, key = { it.id }) { account ->
                    FilterChip(
                        selected = originId == account.id,
                        onClick = {
                            originId = account.id
                            if (destinationId == account.id) destinationId = -1L
                        },
                        label = { Text(account.name) },
                    )
                }
            }
            Text(
                text = "Destino",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = state.accounts.filter { it.id != originId },
                    key = { it.id },
                ) { account ->
                    FilterChip(
                        selected = destinationId == account.id,
                        onClick = { destinationId = account.id },
                        label = { Text(account.name) },
                    )
                }
            }
        } else {
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
        }

        if (isTransfer && originId > 0 && destinationId > 0) {
            val counterpartAmount = Money.parse(amountText)
            val destinationName = state.accounts.firstOrNull { it.id == destinationId }?.name
                ?: "la cuenta de destino"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Registrar la otra pata",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (state.counterpartExists) {
                            "Ya está anotada en $destinationName: se enlazan las dos."
                        } else if (counterpartAmount != null) {
                            "Anota ${Money.format(counterpartAmount)} en $destinationName con " +
                                "el sentido contrario."
                        } else {
                            "Anota el mismo monto en $destinationName con el sentido contrario."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = createCounterpart && !state.counterpartExists,
                    onCheckedChange = { createCounterpart = it },
                    enabled = !state.counterpartExists,
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

        if (!isTransfer) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recordar para este comercio",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = when {
                            effectiveCategoryId == null ->
                                "Elige una categoría para poder recordarla."
                            alreadyRemembered ->
                                "Ya está recordado: este comercio siempre se anota aquí."
                            state.hasRule ->
                                "Se recuerda otra categoría; marca para cambiarla."
                            else ->
                                "El próximo aviso de este comercio se anota en esta categoría."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = rememberRule && effectiveCategoryId != null,
                    onCheckedChange = { rememberRule = it },
                    enabled = effectiveCategoryId != null && !alreadyRemembered,
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
                // Un traspaso guarda la cuenta propia en `accountId` y la otra punta en
                // `transferAccountId`, según si la plata sale o entra.
                val columns = if (isTransfer && originId > 0 && destinationId > 0) {
                    transferColumns(direction, originId, destinationId)
                } else {
                    null
                }
                viewModel.save(
                    amountText = amountText,
                    merchant = merchant,
                    accountId = columns?.accountId ?: accountId.takeIf { it > 0 },
                    categoryId = effectiveCategoryId,
                    direction = direction,
                    isTransfer = isTransfer,
                    transferAccountId = columns?.transferAccountId,
                    createCounterpart = createCounterpart && !state.counterpartExists,
                    rememberRule = rememberRule && !alreadyRemembered,
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
