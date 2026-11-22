package cl.wenasea.wenagastos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.ui.common.CategoryAvatar
import cl.wenasea.wenagastos.ui.common.EmptyState
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.VmFactories
import cl.wenasea.wenagastos.ui.common.WenaChip
import cl.wenasea.wenagastos.ui.common.asColor

/**
 * Reglas aprendidas: la categoría que se anota sola para cada comercio.
 *
 * Aquí se ven, se corrigen y se olvidan; el motor las aplica antes de la semilla del catálogo.
 */
@Composable
fun MerchantRulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val viewModel: MerchantRulesViewModel =
        viewModel(factory = VmFactories.merchantRules(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expandedId by remember { mutableLongStateOf(-1L) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("‹ Ajustes", fontSize = 14.sp)
                    }
                }
                Text(
                    text = "Reglas por comercio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Cuando corriges la categoría de un movimiento puedes pedir que se " +
                        "recuerde. La próxima vez ese comercio se anota solo, y la regla gana " +
                        "sobre la lista fija de categorías.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.rules.isEmpty()) {
            item {
                EmptyState(
                    title = "Todavía no hay reglas",
                    hint = "En el editor de un movimiento marca «Recordar para este comercio». " +
                        "Desde aquí podrás cambiarlas u olvidarlas.",
                )
            }
        }

        items(state.rules, key = { it.id }) { rule ->
            MerchantRuleCard(
                rule = rule,
                category = state.categoryOf(rule),
                categories = state.categoriesFor(rule),
                expanded = expandedId == rule.id,
                onToggle = { expandedId = if (expandedId == rule.id) -1L else rule.id },
                onPick = { categoryId ->
                    viewModel.setCategory(rule, categoryId)
                    expandedId = -1L
                },
                onForget = {
                    viewModel.forget(rule)
                    if (expandedId == rule.id) expandedId = -1L
                },
            )
        }
    }
}

@Composable
private fun MerchantRuleCard(
    rule: MerchantRuleEntity,
    category: CategoryEntity?,
    categories: List<CategoryEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (Long) -> Unit,
    onForget: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (category != null) {
                    CategoryAvatar(
                        emoji = category.emoji,
                        color = category.colorHex.asColor(),
                        size = 32,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.merchantLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = buildString {
                            append(if (rule.direction == Direction.DEBIT) "Gasto" else "Ingreso")
                            append(" · ")
                            append(category?.name ?: "categoría eliminada")
                            append(" · ")
                            append(if (rule.uses == 1) "1 uso" else "${rule.uses} usos")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (expanded) "Cerrar" else "Cambiar categoría", fontSize = 13.sp)
                }
                TextButton(onClick = onForget, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(
                        text = "Olvidar",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (expanded) {
                if (categories.isEmpty()) {
                    Text(
                        text = "No hay categorías de ese tipo para elegir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { option ->
                            WenaChip(
                                text = "${option.emoji} ${option.name}",
                                selected = option.id == category?.id,
                                onClick = { onPick(option.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
