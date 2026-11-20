package cl.wenasea.wenagastos.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.Dates
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CategorySlice(
    val category: CategoryEntity?,
    val total: Long,
    val share: Float,
)

data class LedgerUiState(
    val month: YearMonth = Dates.currentMonth(),
    val expenses: Long = 0L,
    val income: Long = 0L,
    val pendingCount: Int = 0,
    val slices: List<CategorySlice> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val accounts: Map<Long, AccountEntity> = emptyMap(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val loaded: Boolean = false,
) {
    val balance: Long get() = income - expenses
}

class LedgerViewModel(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val month = MutableStateFlow(Dates.currentMonth())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LedgerUiState> = month
        .flatMapLatest { yearMonth ->
            combine(
                transactions.observeBetween(
                    Dates.monthStart(yearMonth),
                    Dates.monthEndExclusive(yearMonth),
                ),
                categories.observeAll(),
                accounts.observeAll(),
                transactions.observePendingCount(),
            ) { monthTransactions, allCategories, allAccounts, pending ->
                buildState(yearMonth, monthTransactions, allCategories, allAccounts, pending)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LedgerUiState(),
        )

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    fun currentMonth() {
        month.value = Dates.currentMonth()
    }

    private fun buildState(
        yearMonth: YearMonth,
        monthTransactions: List<TransactionEntity>,
        allCategories: List<CategoryEntity>,
        allAccounts: List<AccountEntity>,
        pending: Int,
    ): LedgerUiState {
        // Los movimientos "Por revisar" (PENDING) no suman: todavía no se sabe con certeza
        // con qué cuenta se pagó, así que esperan en la tarjeta "Por revisar" del inicio.
        val counted = monthTransactions.filter {
            it.status == TransactionStatus.CONFIRMED && !it.isTransfer
        }

        val expenses = counted.filter { it.direction == Direction.DEBIT }.sumOf { it.amount }
        val income = counted.filter { it.direction == Direction.CREDIT }.sumOf { it.amount }

        val byCategory = counted
            .filter { it.direction == Direction.DEBIT }
            .groupBy { it.categoryId }
            .map { (categoryId, group) ->
                val total = group.sumOf { it.amount }
                CategorySlice(
                    category = categoryId?.let { id -> allCategories.firstOrNull { it.id == id } },
                    total = total,
                    share = if (expenses > 0) total.toFloat() / expenses else 0f,
                )
            }
            .sortedByDescending { it.total }

        return LedgerUiState(
            month = yearMonth,
            expenses = expenses,
            income = income,
            pendingCount = pending,
            slices = byCategory,
            transactions = monthTransactions,
            accounts = allAccounts.associateBy { it.id },
            categories = allCategories.associateBy { it.id },
            loaded = true,
        )
    }
}
