package cl.wenasea.wenagastos.ui.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.SnapshotRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
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
    /** Cupo, ciclo y compras del ciclo de cada tarjeta de crédito, por id de cuenta. */
    val credit: Map<Long, CreditUsage> = emptyMap(),
    val loaded: Boolean = false,
) {
    /** Suma de lo que se sabe: el patrimonio, no el saldo de una cuenta. */
    val total: Long get() = institutions.sumOf { it.total }

    val accountCount: Int get() = institutions.sumOf { it.accounts.size }

    val knownCount: Int get() = institutions.sumOf { it.known }

    val unknownCount: Int get() = institutions.sumOf { it.unknown }

    val hasBalances: Boolean get() = knownCount > 0
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
        BalancesUiState(
            institutions = institutions,
            monthSpend = Balances.monthSpendByAccount(monthTransactions, active),
            credit = institutions
                .flatMap { it.accounts }
                .filter { it.account.kind == AccountKind.CREDIT }
                .associate { row ->
                    row.account.id to Credit.usage(row.account, row.balance, cycleTransactions)
                },
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
