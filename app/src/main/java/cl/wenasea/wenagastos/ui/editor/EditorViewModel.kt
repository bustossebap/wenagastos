package cl.wenasea.wenagastos.ui.editor

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
import cl.wenasea.wenagastos.domain.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transaction: TransactionEntity? = null,
    val isEditing: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class EditorViewModel(
    private val txId: Long,
    private val transactions: TransactionRepository,
    private val accountsRepository: AccountRepository,
    private val categoriesRepository: CategoryRepository,
) : ViewModel() {

    private val local = MutableStateFlow(EditorUiState(isEditing = txId != 0L))

    val state: StateFlow<EditorUiState> = combine(
        local,
        accountsRepository.observeActive(),
        categoriesRepository.observeAll(),
    ) { current, accounts, categories ->
        current.copy(accounts = accounts, categories = categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorUiState(isEditing = txId != 0L),
    )

    init {
        if (txId != 0L) {
            viewModelScope.launch {
                transactions.get(txId)?.let { stored ->
                    local.update { it.copy(transaction = stored) }
                }
            }
        }
    }

    fun save(
        amountText: String,
        merchant: String,
        accountId: Long?,
        categoryId: Long?,
        direction: Direction,
        isTransfer: Boolean = false,
        occurredAtMillis: Long,
    ) {
        val amount = Money.parse(amountText)
        if (amount == null) {
            local.update { it.copy(error = "Ingresa un monto mayor a cero") }
            return
        }
        if (accountId == null) {
            local.update { it.copy(error = "Elige una cuenta") }
            return
        }

        val cleanMerchant = merchant.trim().ifBlank { "Sin nombre" }
        val base = local.value.transaction ?: TransactionEntity(
            accountId = accountId,
            occurredAt = occurredAtMillis,
            amount = amount,
            direction = direction,
            merchantRaw = cleanMerchant,
            merchantNorm = cleanMerchant.lowercase(),
        )

        val entity = base.copy(
            accountId = accountId,
            occurredAt = occurredAtMillis,
            amount = amount,
            direction = direction,
            merchantRaw = cleanMerchant,
            merchantNorm = cleanMerchant.lowercase(),
            categoryId = categoryId,
            isExpense = direction == Direction.DEBIT && !isTransfer,
            isTransfer = isTransfer,
            status = TransactionStatus.CONFIRMED,
            // El motivo del "por revisar" lo generó el motor: al guardar, el usuario ya decidió.
            notes = null,
        )

        viewModelScope.launch {
            local.update { it.copy(saving = true, error = null) }
            transactions.save(entity)
            local.update { it.copy(saving = false, saved = true) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = local.value.transaction ?: return
        viewModelScope.launch {
            transactions.delete(current)
            onDeleted()
        }
    }
}
