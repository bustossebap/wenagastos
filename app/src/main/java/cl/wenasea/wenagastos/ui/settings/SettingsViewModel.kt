package cl.wenasea.wenagastos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.domain.normalizeNumbers
import cl.wenasea.wenagastos.domain.numbers
import cl.wenasea.wenagastos.domain.parseNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val capturedCount: Int = 0,
    /** Movimientos por cuenta: una cuenta con movimientos se archiva en vez de borrarse. */
    val movementCounts: Map<Long, Int> = emptyMap(),
    val error: String? = null,
    /** Se pone en true cuando una acción terminó bien; la pantalla cierra el diálogo. */
    val saved: Boolean = false,
) {
    fun movementCount(account: AccountEntity): Int = movementCounts[account.id] ?: 0
}

class SettingsViewModel(
    private val accounts: AccountRepository,
    categories: CategoryRepository,
    captureLogs: CaptureLogRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(
        accounts.observeAll(),
        categories.observeAll(),
        captureLogs.observeCount(),
        accounts.observeMovementCounts(),
        local,
    ) { accountList, categoryList, captured, counts, mine ->
        mine.copy(
            accounts = accountList,
            categories = categoryList,
            capturedCount = captured,
            movementCounts = counts.associate { it.accountId to it.total },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /**
     * Crea la cuenta ([editing] `null`) o la actualiza. El color sale de la institución, así el
     * alta no pide un color aparte. Los números se validan contra las otras cuentas activas: dos
     * cuentas no pueden reclamar el mismo número, porque el cruce de los avisos es por número
     * exacto y ganaría siempre la de menor id.
     */
    fun saveAccount(
        editing: AccountEntity?,
        name: String,
        institution: Institution,
        kind: AccountKind,
        numbers: List<String>,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            fail("Ponle un nombre a la cuenta")
            return
        }
        val normalized = normalizeNumbers(numbers)
        viewModelScope.launch {
            val wanted = parseNumbers(normalized)
            val other = accounts.listActive().firstOrNull { account ->
                account.id != editing?.id && account.numbers().any { it in wanted }
            }
            val repeated = wanted.firstOrNull { number -> other?.numbers()?.contains(number) == true }
            if (other != null && repeated != null) {
                fail("El número $repeated ya está en ${other.name}")
                return@launch
            }
            val base = editing ?: AccountEntity(
                name = cleanName,
                institution = institution,
                kind = kind,
                colorHex = institution.colorHex,
            )
            val entity = base.copy(
                name = cleanName,
                institution = institution,
                kind = kind,
                last4 = normalized,
                colorHex = institution.colorHex,
            )
            if (editing == null) accounts.add(entity) else accounts.update(entity)
            local.update { it.copy(error = null, saved = true) }
        }
    }

    /** Archiva (deja de usarse en la captura y desaparece del editor) o la reactiva. */
    fun setActive(account: AccountEntity, active: Boolean) {
        viewModelScope.launch {
            accounts.update(account.copy(isActive = active))
            local.update { it.copy(error = null, saved = true) }
        }
    }

    /**
     * Borra la cuenta solo si no tiene movimientos. Si tiene, la archiva: la FK de `transactions`
     * es `CASCADE` y borrarla se llevaría el historial completo.
     */
    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch {
            val movements = accounts.countMovements(account.id)
            if (movements > 0) {
                accounts.update(account.copy(isActive = false))
            } else {
                accounts.delete(account.id)
            }
            local.update { it.copy(error = null, saved = true) }
        }
    }

    fun clearError() {
        local.update { it.copy(error = null) }
    }

    /** La pantalla ya cerró el diálogo por haber guardado. */
    fun consumeSaved() {
        local.update { it.copy(saved = false) }
    }

    private fun fail(message: String) {
        local.update { it.copy(error = message, saved = false) }
    }
}


