package cl.wenasea.wenagastos.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.BudgetStatus
import cl.wenasea.wenagastos.domain.CategoryTotal
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Installments
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.domain.Reports
import cl.wenasea.wenagastos.ui.common.BudgetProgress
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.HeroBlock
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.MonthSwitcher
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.ShareBar
import cl.wenasea.wenagastos.ui.common.TransactionRow
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.asColor
import cl.wenasea.wenagastos.ui.common.budgetColor
import cl.wenasea.wenagastos.ui.common.budgetOfLabel
import cl.wenasea.wenagastos.ui.common.budgetPercentLabel
import cl.wenasea.wenagastos.ui.common.budgetRemainingLabel
import cl.wenasea.wenagastos.ui.theme.Wena
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * Reportes del mes: cómo se reparte el gasto por categoría, cómo viene el último semestre y
 * cuánto queda de cada presupuesto. Todos los números salen de `Reports`, la misma regla que
 * usa el inicio (sin *Por revisar* ni traspasos).
 */
@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    onOpenTransaction: (Long) -> Unit = {},
) {
    val container = LocalContainer.current
    val viewModel: ReportsViewModel = viewModel(factory = VmFactories.reports(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var expandedCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    var showUncategorized by rememberSaveable { mutableStateOf(false) }
    var editingCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    // El administrador de categorías vive acá: «Editar» abre la lista y de ahí se sale a una
    // categoría concreta (para cambiarla) o al alta de una nueva.
    var showCategoryList by rememberSaveable { mutableStateOf(false) }
    var categoryTarget by remember { mutableStateOf<CategoryTarget?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Reportes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Cómo se movió tu plata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        }

        item {
            HeroBlock(
                label = "Gastos del mes",
                amount = Money.format(state.expenses),
                stats = listOf(
                    "Ingresos" to Money.format(state.income),
                    "Balance" to Money.format(state.balance),
                    "Promedio diario" to Money.format(state.dailyAverage),
                ),
            )
        }

        item {
            CategoriesCard(
                state = state,
                expandedCategory = expandedCategory,
                showUncategorized = showUncategorized,
                onToggleCategory = { id ->
                    expandedCategory = if (expandedCategory == id) null else id
                    showUncategorized = false
                },
                onToggleUncategorized = {
                    showUncategorized = !showUncategorized
                    expandedCategory = null
                },
                onOpenTransaction = onOpenTransaction,
            )
        }

        item { TrendCard(state = state, onSelectMonth = viewModel::selectMonth) }

        item { InstallmentsCard(plans = state.installments) }

        item {
            BudgetsCard(
                state = state,
                onEdit = { editingCategoryId = it },
                onManageCategories = { showCategoryList = true },
            )
        }
    }

    val editing = editingCategoryId?.let { state.categoriesById[it] }
    if (editing != null) {
        BudgetDialog(
            category = editing,
            onDismiss = { editingCategoryId = null },
            onApply = { amount ->
                viewModel.setBudget(editing.id, amount)
                editingCategoryId = null
            },
        )
    }

    if (showCategoryList) {
        CategoryManagerDialog(
            categories = state.categoriesById.values.toList(),
            onEdit = { category ->
                showCategoryList = false
                categoryTarget = CategoryTarget(category)
            },
            onAdd = {
                showCategoryList = false
                categoryTarget = CategoryTarget(null)
            },
            onDismiss = { showCategoryList = false },
        )
    }

    categoryTarget?.let { target ->
        val edited = target.category
        CategoryEditDialog(
            category = edited,
            others = state.categoriesById.values.filter { it.id != edited?.id },
            onDismiss = { categoryTarget = null },
            onSave = { fields ->
                viewModel.saveCategory(edited, fields)
                categoryTarget = null
            },
            onArchive = {
                edited?.let(viewModel::archiveCategory)
                categoryTarget = null
            },
        )
    }
}

/**
 * Qué categoría se está editando: `category = null` es el alta. El `null` del estado es «no hay
 * diálogo», que es distinto de «diálogo con categoría nueva».
 */
private data class CategoryTarget(val category: CategoryEntity?)


/**
 * Barra de partes + leyenda + detalle: tocar una categoría abre sus movimientos del mes. La barra
 * enseña el reparto de un vistazo, la leyenda pone los porcentajes y el detalle deja bajar al
 * movimiento.
 */
@Composable
private fun CategoriesCard(
    state: ReportsUiState,
    expandedCategory: Long?,
    showUncategorized: Boolean,
    onToggleCategory: (Long) -> Unit,
    onToggleUncategorized: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "En qué se fue la plata")
            if (state.categories.isEmpty()) {
                Text(
                    text = "Aún no hay gastos en ${Dates.monthLabel(state.month)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }

            // Cinco tramos y el resto sumado: con quince categorías la lectura de partes deja de
            // informar. El detalle completo sigue abajo, categoría por categoría.
            val top = Reports.topCategories(state.categories)
            val otras = MaterialTheme.colorScheme.outline
            Spacer(Modifier.height(12.dp))
            CategoryStack(
                slices = buildList {
                    top.head.forEach { total ->
                        add((total.category?.colorHex?.asColor() ?: Color.Gray) to total.share)
                    }
                    if (top.restCount > 0) add(otras to top.restShare)
                },
            )
            Spacer(Modifier.height(10.dp))
            top.head.forEach { total -> CategoryLegendRow(total) }
            if (top.restCount > 0) {
                OtherCategoriesRow(
                    label = otherCategoriesLabel(top.restCount),
                    share = top.restShare,
                )
            }

            Spacer(Modifier.height(12.dp))
            state.categories.forEach { total ->
                val categoryId = total.category?.id
                val open = if (categoryId == null) showUncategorized else expandedCategory == categoryId
                CategoryDetailRow(
                    total = total,
                    open = open,
                    onClick = {
                        if (categoryId == null) onToggleUncategorized() else onToggleCategory(categoryId)
                    },
                )
                if (open) {
                    CategoryMovements(
                        state = state,
                        total = total,
                        onOpenTransaction = onOpenTransaction,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(total: CategoryTotal) {
    CategoryLegendRow(
        label = total.category?.name ?: "Sin categoría",
        color = total.category?.colorHex?.asColor() ?: Color.Gray,
        share = total.share,
    )
}

/**
 * Una fila de la leyenda: tecla cuadrada del color, nombre y el porcentaje a la derecha. La misma
 * forma sirve para una categoría y para el agregado de las que no caben en el gráfico.
 */
@Composable
private fun CategoryLegendRow(
    label: String,
    color: Color,
    share: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(share * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Otras 7 categorías" / "Otra categoría": el agregado que no cabe en el gráfico. */
private fun otherCategoriesLabel(count: Int): String =
    if (count == 1) "Otra categoría" else "Otras $count categorías"

/**
 * La nota al pie de la leyenda. No lleva tecla: la tecla es de una categoría, y esto son las que
 * quedaron fuera del gráfico, sin un color que las represente.
 */
@Composable
private fun OtherCategoriesRow(
    label: String,
    share: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(share * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryDetailRow(
    total: CategoryTotal,
    open: Boolean,
    onClick: () -> Unit,
) {
    val color = total.category?.colorHex?.asColor() ?: Color.Gray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryAvatar(
            emoji = total.category?.emoji ?: "❓",
            color = color,
            size = 32,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = total.category?.name ?: "Sin categoría",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Gastado ${Money.format(total.spent)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (open) "▴" else "▾",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Los movimientos que armaron esa categoría en el mes, del más nuevo al más viejo. */
@Composable
private fun CategoryMovements(
    state: ReportsUiState,
    total: CategoryTotal,
    onOpenTransaction: (Long) -> Unit,
) {
    val categoryId = total.category?.id
    val movements = remember(state.transactions, categoryId) {
        Reports.counted(state.transactions)
            .filter { it.categoryId == categoryId && it.direction == Direction.DEBIT }
            .sortedByDescending { it.occurredAt }
    }
    Column(modifier = Modifier.padding(start = 42.dp, bottom = 4.dp)) {
        if (movements.isEmpty()) {
            Text(
                text = "Sin movimientos confirmados este mes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        movements.take(DETAIL_LIMIT).forEach { transaction ->
            TransactionRow(
                emoji = total.category?.emoji ?: "❓",
                emojiColor = total.category?.colorHex?.asColor() ?: Color.Gray,
                title = transaction.merchantRaw,
                subtitle = state.accounts[transaction.accountId]?.name
                    ?.let { "${Dates.dayLabel(transaction.occurredAt)} · $it" }
                    ?: Dates.dayLabel(transaction.occurredAt),
                direction = transaction.direction,
                amount = transaction.amount,
                onClick = { onOpenTransaction(transaction.id) },
            )
        }
        if (movements.size > DETAIL_LIMIT) {
            Text(
                text = "y ${movements.size - DETAIL_LIMIT} más",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cuántos movimientos se muestran al abrir una categoría. */
private const val DETAIL_LIMIT = 6


/** Barras de los últimos 6 meses; tocar un mes lo deja seleccionado en el reporte. */
@Composable
private fun TrendCard(state: ReportsUiState, onSelectMonth: (YearMonth) -> Unit) {
    val paleta = Wena.paleta
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "Últimos 6 meses")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartLegendKey(paleta.cargo, "Gastos")
                ChartLegendKey(paleta.abono, "Ingresos")
            }
            Spacer(Modifier.height(12.dp))
            MonthlyBars(
                points = state.points,
                selected = state.month,
                onSelect = onSelectMonth,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Promedio diario en ${Dates.monthLabel(state.month)}: " +
                    Money.format(state.dailyAverage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * «Cuotas por pagar»: lo que ya está comprado y todavía se sigue pagando.
 *
 * No es el gasto del mes —cada cuota ya está anotada en su mes— sino la parte del sueldo que viene
 * comprometida, así que no cambia al cambiar de mes. La barra de cada compra dice cuánto va pagado;
 * el monto grande de la derecha es lo que falta.
 */
@Composable
private fun InstallmentsCard(plans: List<Installments.Plan>) {
    if (plans.isEmpty()) return
    val pending = plans.sumOf { it.pending }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                title = "Cuotas por pagar",
                subtitle = if (plans.size == 1) "1 compra en curso" else "${plans.size} compras en curso",
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = Money.format(pending),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Lo que te falta pagar en las cuotas que vienen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            plans.forEach { plan -> InstallmentRow(plan) }
        }
    }
}

/** Una compra en cuotas: el comercio, en qué cuota va y cuánto falta. */
@Composable
private fun InstallmentRow(plan: Installments.Plan) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = plan.merchant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(plan.pending),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "Cuota ${plan.paid} de ${plan.total} · ${Money.format(plan.perCharge)} al mes" +
                (if (plan.remaining == 1) " · falta 1" else " · faltan ${plan.remaining}"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        ShareBar(
            share = plan.paid / plan.total.toFloat(),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** La marca de la leyenda es una tecla: un cuadradito de 8 dp, no un punto. */
@Composable
private fun ChartLegendKey(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Una fila de presupuesto ya resuelta contra el gasto del mes. */
private data class BudgetRow(
    val category: CategoryEntity,
    val spent: Long,
    val status: BudgetStatus?,
)


/** Presupuestos: el total, cada categoría con tope o con gasto este mes y la puerta a editarlas. */
@Composable
private fun BudgetsCard(
    state: ReportsUiState,
    onEdit: (Long) -> Unit,
    onManageCategories: () -> Unit,
) {
    val rows = remember(state.categories, state.categoriesById) { budgetRows(state) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                title = "Presupuestos",
                actionLabel = "Editar",
                onAction = onManageCategories,
            )
            if (rows.isEmpty()) {
                Text(
                    text = "Crea una categoría de gasto con «Editar» para poder ponerle un " +
                        "presupuesto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }

            if (state.budgetTotal > 0L) {
                val totalStatus = Reports.budgetStatus(state.budgetSpent, state.budgetTotal)
                val totalColor = if (totalStatus != null) {
                    budgetColor(totalStatus.level)
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Presupuestado ${Money.format(state.budgetTotal)} · " +
                        "Gastado ${Money.format(state.budgetSpent)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                ShareBar(
                    share = totalStatus?.fraction?.coerceIn(0f, 1f) ?: 0f,
                    color = totalColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = budgetTotalLabel(state.budgetTotal - state.budgetSpent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rows.forEach { row ->
                BudgetRowView(row = row, onEdit = onEdit)
            }
        }
    }
}

private fun budgetTotalLabel(remaining: Long): String = if (remaining >= 0L) {
    "Quedan ${Money.format(remaining)} entre las categorías con tope."
} else {
    "Vas ${Money.format(-remaining)} sobre lo presupuestado."
}

/** Las categorías de gasto con tope o con gasto este mes, la más avanzada primero. */
private fun budgetRows(state: ReportsUiState): List<BudgetRow> {
    val spentByCategory = state.categories
        .mapNotNull { total -> total.category?.let { it.id to total.spent } }
        .toMap()
    val all = state.categoriesById.values
        .filter { it.kind == CategoryKind.EXPENSE && !it.isArchived }
        .map { category ->
            val spent = spentByCategory[category.id] ?: 0L
            BudgetRow(
                category = category,
                spent = spent,
                status = Reports.budgetStatus(spent, category.monthlyBudget),
            )
        }
    // Si el mes no tiene gastos ni presupuestos, se muestran todas las categorías de gasto:
    // así igual se le puede poner un tope a una categoría antes de gastar en ella.
    val withMovement = all.filter { it.status != null || it.spent > 0L }
    return (withMovement.ifEmpty { all })
        .sortedWith(
            compareByDescending<BudgetRow> { it.status?.fraction ?: -1f }
                .thenByDescending { it.spent }
                .thenBy { it.category.name },
        )
}


@Composable
private fun BudgetRowView(row: BudgetRow, onEdit: (Long) -> Unit) {
    val status = row.status
    val levelColor = status?.let { budgetColor(it.level) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onEdit(row.category.id) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryAvatar(
                emoji = row.category.emoji,
                color = row.category.colorHex.asColor(),
                size = 32,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = status?.let { budgetPercentLabel(it) } ?: "Poner tope",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = levelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = status?.let { "${row.category.nature.label} · ${budgetOfLabel(it)}" }
                        ?: "${row.category.nature.label} · Gastado ${Money.format(row.spent)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status != null) {
            Spacer(Modifier.height(6.dp))
            BudgetProgress(status = status)
            Spacer(Modifier.height(4.dp))
            Text(
                text = budgetRemainingLabel(status),
                style = MaterialTheme.typography.labelSmall,
                color = levelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}



/**
 * Poner o cambiar el tope mensual. "Quitar" borra el presupuesto; la categoría queda sin tope y
 * el gasto registrado no se toca.
 */
@Composable
private fun BudgetDialog(
    category: CategoryEntity,
    onDismiss: () -> Unit,
    onApply: (Long?) -> Unit,
) {
    val current = category.monthlyBudget
    var text by remember { mutableStateOf(current?.toString().orEmpty()) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presupuesto de ${category.name}") },
        text = {
            Column {
                Text(
                    text = "Cuánto quieres gastar como máximo al mes. Se reinicia solo cada mes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }
                        showError = false
                    },
                    label = { Text("Monto mensual") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Escribe un monto mayor a 0") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (current != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Hoy: ${Money.format(current)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = Money.parse(text)
                    if (amount == null) showError = true else onApply(amount)
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onApply(null) }) {
                        Text("Quitar")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        },
    )
}
