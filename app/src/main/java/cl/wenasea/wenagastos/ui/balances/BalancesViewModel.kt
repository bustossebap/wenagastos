package cl.wenasea.wenagastos.ui.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.SnapshotRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.Balances
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
 */
class BalancesViewModel(
    accounts: AccountRepository,
    private val snapshots: SnapshotRepository,
    transactions: TransactionRepository,
) : ViewModel() {

    private val month = Dates.currentMonth()

    val state: StateFlow<BalancesUiState> = combine(
        accounts.observeActive(),
        snapshots.observeAll(),
        transactions.observeSinceSnapshotCutoffs(),
        transactions.observeBetween(Dates.monthStart(month), Dates.monthEndExclusive(month)),
    ) { active, cuts, afterCutoff, monthTransactions ->
        BalancesUiState(
            institutions = Balances.institutions(active, cuts, afterCutoff),
            monthSpend = Balances.monthSpendByAccount(monthTransactions, active),
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
     * Devuelve `false` si el texto no es un monto, para que el diálogo no se cierre en vano.
     */
    fun saveBalance(accountId: Long, input: String): Boolean {
        val balance = parseBalance(input) ?: return false
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
