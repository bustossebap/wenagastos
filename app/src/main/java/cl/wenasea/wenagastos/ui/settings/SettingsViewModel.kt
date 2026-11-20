package cl.wenasea.wenagastos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
)

class SettingsViewModel(
    accounts: AccountRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        accounts.observeAll(),
        categories.observeAll(),
    ) { accountList, categoryList ->
        SettingsUiState(accounts = accountList, categories = categoryList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )
}
