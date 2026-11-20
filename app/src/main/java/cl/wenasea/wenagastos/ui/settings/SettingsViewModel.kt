package cl.wenasea.wenagastos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val capturedCount: Int = 0,
)

class SettingsViewModel(
    private val accounts: AccountRepository,
    categories: CategoryRepository,
    captureLogs: CaptureLogRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        accounts.observeAll(),
        categories.observeAll(),
        captureLogs.observeCount(),
    ) { accountList, categoryList, captured ->
        SettingsUiState(
            accounts = accountList,
            categories = categoryList,
            capturedCount = captured,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /** Guarda los últimos 4 dígitos de la tarjeta; con menos de 4 dígitos queda vacío. */
    fun setLast4(account: AccountEntity, text: String) {
        val digits = text.filter(Char::isDigit).take(4)
        val last4 = digits.takeIf { it.length == 4 }
        viewModelScope.launch {
            accounts.update(account.copy(last4 = last4))
        }
    }
}

