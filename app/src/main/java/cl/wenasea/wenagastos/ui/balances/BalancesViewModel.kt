package cl.wenasea.wenagastos.ui.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.SnapshotRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.AccountBalance
import cl.wenasea.wenagastos.domain.Balances
import cl.wenasea.wenagastos.domain.Credit
import cl.wenasea.wenagastos.domain.CreditUsage
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.InstitutionBalance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BalancesUiState(
    val institutions: List<InstitutionBalance> = emptyList(),
    /** Gasto del mes en curso por cuenta: lo que se lleva la tarjeta de cada cuenta. */
    val monthSpend: Map<Long, Long> = emptyMap(),
    /** Cuotas fijas del mes por cuenta: tarjetas, suscripciones. */
    val monthFixed: Map<Long, Long> = emptyMap(),
    /** Gastos variables del mes por cuenta: débitos sueltos e impredecibles. */
    val monthVariable: Map<Long, Long> = emptyMap(),
    /** Ahorros del mes por cuenta: transferencias entre cuentas propias. */
    val monthSavings: Map<Long, Long> = emptyMap(),
    /** Cupo, ciclo y compras del ciclo de cada tarjeta de crédito, por id de cuenta. */
    val credit: Map<Long, CreditUsage> = emptyMap(),
    val loaded: Boolean = false,
) {
    /** Todas las cuentas activas, en el orden del cálculo (por id). */
    val rows: List<AccountBalance> get() = institutions.flatMap { it.accounts }

    /** La plata que hay: cta. cte., ahorro/billetera y efectivo. */
    val cash: List<AccountBalance> get() = rows.filter { it.account.kind != AccountKind.CREDIT }

    /** Lo que se debe: las tarjetas de crédito van aparte de la plata. */
    val cards: List<AccountBalance> get() = rows.filter { it.account.kind == AccountKind.CREDIT }

    /**
     * El patrimonio es **solo el efectivo**: la suma de los saldos conocidos de las cuentas.
     * La deuda de una tarjeta no se resta acá, porque no es plata que se haya ido de la cuenta
     * (la tarjeta tiene su propio bloque, con su cupo) y restarla dejaría un patrimonio que no
     * es lo que se puede girar.
     */
    val total: Long get() = cash.sumOf { it.balance ?: 0L }

    val accountCount: Int get() = rows.size

    val knownCount: Int get() = cash.count { it.known }

    val unknownCount: Int get() = cash.size - knownCount

    val hasBalances: Boolean get() = knownCount > 0

    val hasCards: Boolean get() = cards.isNotEmpty()

    /** Suma de lo que queda disponible en las tarjetas que tienen cupo y deuda anotados. */
    val creditAvailable: Long get() =
        cards.sumOf { row -> credit[row.account.id]?.available ?: 0L }

    /** Suma de los cupos escritos, para decir «de cuánto». */
    val creditLimit: Long get() = cards.sumOf { it.account.creditLimit ?: 0L }

    /** Cuántas tarjetas aportan al cupo disponible; el resto no tiene cupo o deuda escrita. */
    val creditKnown: Int get() = cards.count { credit[it.account.id]?.available != null }

    /** Suma de gastos fijos por cuenta. */
    val totalFixed: Long get() = cash.sumOf { row -> monthFixed[row.account.id] ?: 0L }

    /** Suma de gastos variables por cuenta. */
    val totalVariable: Long get() = cash.sumOf { row -> monthVariable[row.account.id] ?: 0L }

    /** Suma de ahorros (transferencias) por cuenta. */
    val totalSavings: Long get() = cash.sumOf { row -> monthSavings[row.account.id] ?: 0L }
}

/**
 * Saldos por cuenta (Fase 7).
 *
 * El cálculo no se guarda: se arma con el último corte de cada cuenta (tabla
 * `account_snapshots`) más los movimientos confirmados posteriores. Por eso la pantalla se
 * corrige sola cuando llega una notificación, se edita un movimiento o se escribe un saldo.
 *
 * Desde la Fase 7.1 la tarjeta de crédito suma `Credit`: cupo, ciclo en curso y compras del ciclo.
 * Ese cálculo es aparte del saldo a propósito —el saldo es lo que dijo el banco, las compras del
 * ciclo son lo que llegó por aviso— y se lee con una sola consulta de movimientos por la ventana
 * de dos meses, no una por tarjeta.
 */
class BalancesViewModel(
    accounts: AccountRepository,
    private val snapshots: SnapshotRepository,
    transactions: TransactionRepository,
) : ViewModel() {

    private val month = Dates.currentMonth()

    /**
     * Ventana para el ciclo de las tarjetas: un ciclo nunca empieza antes del día 1 del mes
     * anterior (el día de corte más tardío es el 31), así que dos meses cubren cualquiera sin
     * tener que reconstruir la consulta cuando se edita el día de corte.
     */
    private val cycleFrom = Dates.monthStart(month.minusMonths(1))

    val state: StateFlow<BalancesUiState> = combine(
    accounts.observeActive(),
    snapshots.observeAll(),
    transactions.observeSinceSnapshotCutoffs(),
    transactions.observeBetween(Dates.monthStart(month), Dates.monthEndExclusive(month)),
    transactions.observeBetween(cycleFrom, Long.MAX_VALUE),
) { active, cuts, afterCutoff, monthTransactions, cycleTransactions ->
    val institutions = Balances.institutions(active, cuts, afterCutoff)
    val monthSpend = Balances.monthSpendByAccount(monthTransactions, active)
    // Tres cortes del mismo mes. No se reusa `monthSpendByAccount` con un filtro encima: ese
    // criterio descarta los traspasos, así que los ahorros salían siempre en cero.
    val monthFixed = active.associate {
        it.id to Balances.fixedExpensesByAccount(monthTransactions, it.id)
    }
    val monthVariable = active.associate {
        it.id to Balances.variableExpensesByAccount(monthTransactions, it.id)
    }
    val monthSavings = active.associate {
        it.id to Balances.savingsByAccount(monthTransactions, it.id)
    }
    BalancesUiState(
        institutions = institutions,
        monthSpend = monthSpend,
        monthFixed = monthFixed,
        monthVariable = monthVariable,
        monthSavings = monthSavings,
        credit = institutions
            .flatMap { it.accounts }
            .filter { it.account.kind == AccountKind.CREDIT }
            .associate { row -> row.account.id to Credit.usage(row.account, row.balance, cycleTransactions) },
        loaded = true,
    )
}.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BalancesUiState(),
    )

    /**
     * Escribe el saldo que el banco muestra **ahora**. El corte queda con la hora del teléfono:
     * lo que ya está registrado antes de este instante se considera incluido en ese saldo, y lo
     * que llegue después lo mueve solo.
     *
     * En una tarjeta (`credit = true`) el número que se escribe es la **deuda**, no el saldo:
     * `Credit.balanceFromDebt` la guarda negativa para que se reste del cupo.
     *
     * Devuelve `false` si el texto no es un monto, para que el diálogo no se cierre en vano.
     */
    fun saveBalance(accountId: Long, input: String, credit: Boolean = false): Boolean {
        val balance = (if (credit) Credit.balanceFromDebt(input) else parseBalance(input))
            ?: return false
        viewModelScope.launch {
            snapshots.add(
                AccountSnapshotEntity(
                    accountId = accountId,
                    asOf = System.currentTimeMillis(),
                    balance = balance,
                    source = SnapshotSource.MANUAL,
                ),
            )
        }
        return true
    }

    /** Borra el último saldo de la cuenta: manda el corte anterior o el saldo vuelve a no saberse. */
    fun clearBalance(accountId: Long) {
        viewModelScope.launch { snapshots.forgetLatest(accountId) }
    }
}

/**
 * Acepta "1.250.000", "$1250000" y "-45.000". El signo es parte del dato: un saldo negativo es
 * una deuda (la tarjeta de crédito usada o un sobregiro), no un error de tipeo.
 */
internal fun parseBalance(text: String): Long? {
    val trimmed = text.trim()
    val digits = trimmed.filter(Char::isDigit)
    if (digits.isEmpty()) return null
    val value = digits.toLongOrNull() ?: return null
    return if (trimmed.startsWith("-")) -value else value
}
