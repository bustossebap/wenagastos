package cl.wenasea.wenagastos.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.CategoryTotal
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Ledger
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

data class CategorySlice(
    val category: CategoryEntity?,
    val total: Long,
    val share: Float,
)

data class LedgerUiState(
    val month: YearMonth = Dates.currentMonth(),
    /** Cuenta por la que se está mirando el mes; `null` es «todas». */
    val accountId: Long? = null,
    val expenses: Long = 0L,
    val income: Long = 0L,
    val pendingCount: Int = 0,
    val slices: List<CategorySlice> = emptyList(),
    /** Categorías del mes que ya están cerca del límite o pasadas: la tarjeta de presupuestos. */
    val budgetAlerts: List<CategoryTotal> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val accounts: Map<Long, AccountEntity> = emptyMap(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val loaded: Boolean = false,
) {
    val balance: Long get() = income - expenses

    /** El nombre de la cuenta filtrada, para los títulos; `null` cuando se ven todas. */
    val accountName: String? get() = accountId?.let { id -> accounts[id]?.name }
}

class LedgerViewModel(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    /**
     * Mes con el que abre la pantalla. Inicio y Movimientos comparten este ViewModel, así que
     * «Ver movimientos» en octubre tiene que abrir Movimientos **en octubre**: sin esto la lista
     * arrancaba siempre en el mes de hoy y el botón parecía no hacer nada.
     */
    initialMonth: YearMonth? = null,
    /**
     * Cuenta con la que abre la pantalla (Fase 9.2). Llega desde el detalle de *Saldos*, donde se
     * toca una cuenta para ver qué se movió en ella. `null` es el mes completo.
     */
    initialAccountId: Long? = null,
) : ViewModel() {

    private val month = MutableStateFlow(initialMonth ?: Dates.currentMonth())

    /**
     * El filtro por cuenta no vuelve a consultar la base: el mes entero llega igual y se recorta
     * acá. Son cien filas, y así el hero, la lista y el «sin movimientos» dicen lo mismo sin que
     * haya dos consultas que se puedan desincronizar.
     */
    private val accountId = MutableStateFlow(initialAccountId)

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
                accountId,
            ) { monthTransactions, allCategories, allAccounts, pending, account ->
                buildState(yearMonth, monthTransactions, allCategories, allAccounts, pending, account)
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

    /** «Todas» pasa `null`; tocar una cuenta la filtra. */
    fun selectAccount(id: Long?) {
        accountId.value = id
    }

    private fun buildState(
        yearMonth: YearMonth,
        monthTransactions: List<TransactionEntity>,
        allCategories: List<CategoryEntity>,
        allAccounts: List<AccountEntity>,
        pending: Int,
        account: Long?,
    ): LedgerUiState {
        // Fase 9.2: mirar una cuenta es mirar el mes a través de ella, no otra pantalla.
        val scoped = Ledger.scope(monthTransactions, account)
        // Los movimientos "Por revisar" (PENDING) no suman: todavía no se sabe con certeza
        // con qué cuenta se pagó, así que esperan en la tarjeta "Por revisar" del inicio.
        // Los traspasos tampoco: la plata no se gastó, solo cambió de cuenta. La misma regla
        // vive en `Reports.counted` para que Reportes diga exactamente lo mismo.
        val counted = Reports.counted(scoped)

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
            accountId = account,
            expenses = expenses,
            income = income,
            pendingCount = pending,
            slices = byCategory,
            budgetAlerts = Reports.budgetAlerts(scoped, allCategories),
            transactions = scoped,
            accounts = allAccounts.associateBy { it.id },
            categories = allCategories.associateBy { it.id },
            loaded = true,
        )
    }
}
