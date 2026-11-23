package cl.wenasea.wenagastos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.CategoryFields
import cl.wenasea.wenagastos.domain.CategoryTotal
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Installments
import cl.wenasea.wenagastos.domain.MonthPoint
import cl.wenasea.wenagastos.domain.Reports
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val month: YearMonth = Dates.currentMonth(),
    /** Los últimos meses para el gráfico de barras, del más viejo al elegido. */
    val points: List<MonthPoint> = emptyList(),
    /** Categorías de gasto del mes, de mayor a menor. */
    val categories: List<CategoryTotal> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val accounts: Map<Long, AccountEntity> = emptyMap(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap(),
    val expenses: Long = 0L,
    val income: Long = 0L,
    val dailyAverage: Long = 0L,
    /** Suma de los presupuestos configurados. */
    val budgetTotal: Long = 0L,
    /** Lo gastado dentro de las categorías que sí tienen presupuesto. */
    val budgetSpent: Long = 0L,
    /** Las compras en cuotas que todavía se están pagando, de la que más debe a la que menos. */
    val installments: List<Installments.Plan> = emptyList(),
    val loaded: Boolean = false,
) {
    val balance: Long get() = income - expenses
}

class ReportsViewModel(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val month = MutableStateFlow(Dates.currentMonth())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportsUiState> = month
        .flatMapLatest { yearMonth ->
            val months = window(yearMonth)
            combine(
                transactions.observeBetween(
                    Dates.monthStart(yearMonth),
                    Dates.monthEndExclusive(yearMonth),
                ),
                transactions.observeBetween(
                    Dates.monthStart(months.first()),
                    Dates.monthEndExclusive(yearMonth),
                ),
                categories.observeAll(),
                accounts.observeAll(),
                // Las cuotas no dependen del mes: una compra en 12 cuotas sigue viva aunque se esté
                // mirando un mes en que no se cobró.
                transactions.observeInstallments(),
            ) { monthTransactions, windowTransactions, allCategories, allAccounts, installments ->
                buildState(
                    yearMonth = yearMonth,
                    months = months,
                    monthTransactions = monthTransactions,
                    windowTransactions = windowTransactions,
                    allCategories = allCategories,
                    allAccounts = allAccounts,
                    installmentTransactions = installments,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(),
        )

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    fun selectMonth(yearMonth: YearMonth) {
        month.value = yearMonth
    }

    /** Presupuesto mensual de una categoría; `null` lo quita. */
    fun setBudget(categoryId: Long, amount: Long?) {
        viewModelScope.launch { categories.setMonthlyBudget(categoryId, amount) }
    }

    /**
     * Alta o edición de una categoría desde Reportes → Presupuestos.
     *
     * Una nueva entra al final de su grupo: el orden es el mayor `sortOrder` del mismo tipo más
     * cinco, así no se cuela entre las que el usuario ya tiene ordenadas. Si la categoría cambió
     * de tipo, el orden se recalcula en el grupo nuevo; si no, se conserva.
     */
    fun saveCategory(original: CategoryEntity?, fields: CategoryFields) {
        viewModelScope.launch {
            val order = if (original == null || original.kind != fields.kind) {
                nextSortOrder(fields.kind)
            } else {
                original.sortOrder
            }
            val base = original ?: CategoryEntity(
                name = fields.name,
                emoji = fields.emoji,
                colorHex = fields.colorHex,
                kind = fields.kind,
                sortOrder = order,
            )
            categories.update(
                base.copy(
                    name = fields.name,
                    emoji = fields.emoji,
                    colorHex = fields.colorHex,
                    kind = fields.kind,
                    monthlyBudget = fields.monthlyBudget,
                    nature = fields.nature,
                    sortOrder = order,
                ),
            )
        }
    }

    /**
     * Saca la categoría del catálogo. No se borra: los movimientos ya anotados le apuntan y
     * perderían su clasificación, así que la historia se queda y la categoría deja de ofrecerse.
     */
    fun archiveCategory(category: CategoryEntity) {
        viewModelScope.launch { categories.update(category.copy(isArchived = true)) }
    }

    private fun nextSortOrder(kind: CategoryKind): Int {
        val max = state.value.categoriesById.values
            .filter { it.kind == kind }
            .maxOfOrNull { it.sortOrder }
        return (max ?: 0) + 5
    }

    private fun buildState(
        yearMonth: YearMonth,
        months: List<YearMonth>,
        monthTransactions: List<TransactionEntity>,
        windowTransactions: List<TransactionEntity>,
        allCategories: List<CategoryEntity>,
        allAccounts: List<AccountEntity>,
        installmentTransactions: List<TransactionEntity>,
    ): ReportsUiState {
        val totals = Reports.byCategory(monthTransactions, allCategories)
        return ReportsUiState(
            month = yearMonth,
            points = Reports.series(windowTransactions, months),
            categories = totals,
            transactions = monthTransactions,
            accounts = allAccounts.associateBy { it.id },
            categoriesById = allCategories.associateBy { it.id },
            expenses = Reports.expenses(monthTransactions),
            income = Reports.income(monthTransactions),
            dailyAverage = Reports.dailyAverage(monthTransactions, yearMonth),
            budgetTotal = Reports.totalBudget(allCategories),
            budgetSpent = totals.filter { it.budget != null }.sumOf { it.spent },
            installments = Installments.plans(installmentTransactions),
            loaded = true,
        )
    }

    /** Los 6 meses que terminan en [yearMonth], del más viejo al más nuevo. */
    private fun window(yearMonth: YearMonth): List<YearMonth> =
        (WINDOW_MONTHS - 1 downTo 0).map { yearMonth.minusMonths(it.toLong()) }

    private companion object {
        /** Cuántos meses muestra el gráfico de barras. */
        const val WINDOW_MONTHS = 6
    }
}
