package cl.wenasea.wenagastos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.MerchantRuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MerchantRulesUiState(
    val rules: List<MerchantRuleEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
) {

    /** Categoría de la regla; `null` si la categoría se borró y la regla quedó huérfana. */
    fun categoryOf(rule: MerchantRuleEntity): CategoryEntity? =
        rule.categoryId?.let { id -> categories.firstOrNull { it.id == id } }

    /** Solo se ofrecen categorías del tipo que corresponde al sentido de la regla. */
    fun categoriesFor(rule: MerchantRuleEntity): List<CategoryEntity> {
        val kind = if (rule.direction == Direction.DEBIT) CategoryKind.EXPENSE else CategoryKind.INCOME
        return categories.filter { it.kind == kind }
    }
}

/** Reglas aprendidas: qué categoría se anota sola para cada comercio. */
class MerchantRulesViewModel(
    private val rules: MerchantRuleRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<MerchantRulesUiState> =
        combine(rules.observeAll(), categories.observeAll()) { rows, cats ->
            MerchantRulesUiState(rules = rows, categories = cats)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MerchantRulesUiState(),
        )

    fun setCategory(rule: MerchantRuleEntity, categoryId: Long) {
        viewModelScope.launch { rules.updateCategory(rule, categoryId) }
    }

    fun forget(rule: MerchantRuleEntity) {
        viewModelScope.launch { rules.forget(rule.id) }
    }
}
