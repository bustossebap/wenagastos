package cl.wenasea.wenagastos.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.CategoryNature
import cl.wenasea.wenagastos.domain.CategoryEdit
import cl.wenasea.wenagastos.domain.CategoryFields
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.LedgerRule
import cl.wenasea.wenagastos.ui.common.WenaChip
import cl.wenasea.wenagastos.ui.common.asColor

/** Los colores que ofrece el editor: la paleta de la app, no un selector infinito. */
private val categoryColors = listOf(
    "#2E7D32", "#00695C", "#00838F", "#1565C0", "#283593", "#5E35B1",
    "#6A1B9A", "#AD1457", "#C62828", "#EF6C00", "#8D6E63", "#455A64",
)

/** Los emojis que más se usan, para no tener que abrir el teclado de emojis. */
private val categoryEmojis = listOf(
    "🛒", "🍽️", "🚌", "⛽", "🛵", "🏠", "🔑", "💊", "📺", "👕",
    "🎧", "🎬", "🎁", "🧴", "🚕", "🎲", "📶", "🏢", "🐷", "💼",
)


/**
 * La lista de categorías, con la puerta al alta y a la edición. Vive en Reportes → Presupuestos
 * porque ahí es donde se decide cuánto va a cada una: tenerla en Ajustes obligaba a saltar de
 * pantalla para cambiar un nombre.
 *
 * Las de tipo transferencia no se listan: el catálogo ya no las usa (Fase 9.1) y una vieja que
 * haya quedado no se puede editar para convertirla en gasto.
 */
@Composable
internal fun CategoryManagerDialog(
    categories: List<CategoryEntity>,
    onEdit: (CategoryEntity) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val expenses = categories.filter { it.kind == CategoryKind.EXPENSE }
    val incomes = categories.filter { it.kind == CategoryKind.INCOME }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorías (${expenses.size + incomes.size})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Toca una categoría para cambiarle el nombre, el emoji, el tipo de " +
                        "gasto o su presupuesto mensual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CategoryGroup(title = "Gastos", categories = expenses, onEdit = onEdit)
                CategoryGroup(title = "Ingresos", categories = incomes, onEdit = onEdit)
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun CategoryGroup(
    title: String,
    categories: List<CategoryEntity>,
    onEdit: (CategoryEntity) -> Unit,
) {
    if (categories.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
    categories.forEachIndexed { index, category ->
        if (index > 0) LedgerRule(modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onEdit(category) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryAvatar(
                emoji = category.emoji,
                color = category.colorHex.asColor(),
                size = 28,
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = category.tagLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Fijo · $120.000», «Variable» o «Ingreso · $50.000»: lo que hay que saber sin abrirla. */
internal fun CategoryEntity.tagLabel(): String {
    val budget = monthlyBudget?.let(Money::format)
    val type = if (kind == CategoryKind.EXPENSE) nature.label else kind.label
    return if (budget == null) type else "$type · $budget"
}

/**
 * Alta y edición de una categoría: nombre, emoji, color, tipo (gasto o ingreso), tipo de gasto
 * (fijo, variable o ahorro) y presupuesto mensual. Los dos tipos son cosas distintas: uno decide
 * en qué lista aparece y el otro, dentro de los gastos, cómo se agrupa.
 *
 * El error se calcula acá con [CategoryEdit] y se muestra dentro del diálogo: así no se cierra en
 * vano con un nombre repetido o un presupuesto vacío de dígitos.
 */
@Composable
internal fun CategoryEditDialog(
    category: CategoryEntity?,
    others: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (CategoryFields) -> Unit,
    onArchive: () -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var emoji by remember(category?.id) { mutableStateOf(category?.emoji.orEmpty()) }
    var colorHex by remember(category?.id) {
        mutableStateOf(category?.colorHex ?: categoryColors.first())
    }
    var kind by remember(category?.id) { mutableStateOf(category?.kind ?: CategoryKind.EXPENSE) }
    var nature by remember(category?.id) {
        mutableStateOf(category?.nature ?: CategoryNature.VARIABLE)
    }
    var budget by remember(category?.id) {
        mutableStateOf(category?.monthlyBudget?.toString().orEmpty())
    }
    var error by remember(category?.id) { mutableStateOf<String?>(null) }
    var confirmingArchive by remember(category?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Nueva categoría" else "Editar ${category.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (confirmingArchive) {
                    Text(
                        text = "«${category?.name}» deja de ofrecerse al anotar un movimiento. " +
                            "Lo ya anotado queda como está y con su categoría.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onArchive) {
                            Text("Archivar", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { confirmingArchive = false }) { Text("Volver") }
                    }
                    return@Column
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(CategoryEdit.MAX_NAME)
                        error = null
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Emoji",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    categoryEmojis.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (option == emoji) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable { emoji = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = option, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.trim().take(4) },
                    label = { Text("U otro") },
                    singleLine = true,
                    modifier = Modifier
                        .width(120.dp)
                        .padding(top = 4.dp),
                )


                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categoryColors.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(option.asColor())
                                .border(
                                    width = if (option == colorHex) 3.dp else 0.dp,
                                    color = if (option == colorHex) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { colorHex = option },
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
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(CategoryKind.EXPENSE, CategoryKind.INCOME).forEach { option ->
                        WenaChip(
                            text = option.label,
                            selected = option == kind,
                            onClick = { kind = option },
                        )
                    }
                }

                if (kind == CategoryKind.EXPENSE) {
                    Text(
                        text = "Tipo de gasto",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryNature.entries.forEach { option ->
                            WenaChip(
                                text = option.label,
                                selected = option == nature,
                                onClick = { nature = option },
                            )
                        }
                    }
                    Text(
                        text = "El editor de movimientos usa esto para ofrecer solo las " +
                            "categorías del tipo elegido.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                OutlinedTextField(
                    value = budget,
                    onValueChange = { input ->
                        budget = input.filter { it.isDigit() }.take(12)
                        error = null
                    },
                    label = { Text("Presupuesto mensual (opcional)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = CategoryEdit.parse(
                        name = name,
                        emoji = emoji,
                        colorHex = colorHex,
                        kind = kind,
                        nature = nature,
                        budget = budget,
                        others = others,
                    )
                    error = parsed.error
                    parsed.fields?.let(onSave)
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row {
                if (category != null) {
                    TextButton(onClick = { confirmingArchive = true }) {
                        Text("Archivar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

