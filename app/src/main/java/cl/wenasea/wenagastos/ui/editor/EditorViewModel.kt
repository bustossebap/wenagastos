package cl.wenasea.wenagastos.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.MerchantRuleRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.Installments
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.domain.opposite
import cl.wenasea.wenagastos.domain.transferEnds
import kotlinx.coroutines.Job
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
    /** Ya hay un movimiento en la cuenta de destino que parece ser la otra pata del traspaso. */
    val counterpartExists: Boolean = false,
    /** Categoría que ya está recordada para el comercio escrito (null si no hay regla vigente). */
    val rememberedCategoryId: Long? = null,
    /** El comercio escrito ya tiene regla guardada, aunque su categoría ya no exista. */
    val hasRule: Boolean = false,
)

class EditorViewModel(
    private val txId: Long,
    private val transactions: TransactionRepository,
    private val accountsRepository: AccountRepository,
    private val categoriesRepository: CategoryRepository,
    private val rulesRepository: MerchantRuleRepository,
) : ViewModel() {

    private val local = MutableStateFlow(EditorUiState(isEditing = txId != 0L))

    /** Consulta en curso de "¿ya está la otra pata?"; se cancela al cambiar los datos. */
    private var counterpartJob: Job? = null

    /** Consulta en curso de "¿este comercio ya tiene regla?"; se cancela al cambiar los datos. */
    private var ruleJob: Job? = null

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
        transferAccountId: Long? = null,
        createCounterpart: Boolean = false,
        rememberRule: Boolean = false,
        installmentNumber: Int? = null,
        installmentTotal: Int? = null,
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
        if (isTransfer && transferAccountId == null) {
            local.update { it.copy(error = "Elige a qué cuenta se transfiere") }
            return
        }
        if (isTransfer && transferAccountId == accountId) {
            local.update { it.copy(error = "El origen y el destino no pueden ser la misma cuenta") }
            return
        }

        // Fase 9.3: la cuota. Vacío es «no va en cuotas»; si se escribe, los dos números tienen
        // que calzar —un «3 de ?» no dice nada y no se guarda—.
        val total = installmentTotal?.takeIf { it in 2..Installments.MAX }
        if (installmentTotal != null && total == null) {
            local.update {
                it.copy(error = "Hasta ${Installments.MAX} cuotas: revisa el número")
            }
            return
        }
        if (installmentNumber != null && (total == null || installmentNumber !in 1..total)) {
            local.update {
                it.copy(error = "La cuota va entre 1 y el total, por ejemplo 3 de 12")
            }
            return
        }
        // Una cuota es parte de un gasto: ni un traspaso ni un ingreso van en cuotas.
        val onInstallments = !isTransfer && direction == Direction.DEBIT && total != null

        val cleanMerchant = merchant.trim().ifBlank { "Sin nombre" }
        val base = local.value.transaction ?: TransactionEntity(
            accountId = accountId,
            occurredAt = occurredAtMillis,
            amount = amount,
            direction = direction,
            merchantRaw = cleanMerchant,
            merchantNorm = cleanMerchant.lowercase(),
        )
        // Un traspaso no es gasto ni ingreso: no lleva categoría, así que no hay nada que resolver.
        val entity = base.copy(
            accountId = accountId,
            occurredAt = occurredAtMillis,
            amount = amount,
            direction = direction,
            merchantRaw = cleanMerchant,
            merchantNorm = cleanMerchant.lowercase(),
            categoryId = if (isTransfer) null else categoryId,
            isExpense = direction == Direction.DEBIT && !isTransfer,
            isTransfer = isTransfer,
            transferAccountId = if (isTransfer) transferAccountId else null,
            transferGroupId = if (isTransfer) base.transferGroupId else null,
            status = TransactionStatus.CONFIRMED,
            // El motivo del "por revisar" lo generó el motor: al guardar, el usuario ya decidió.
            notes = null,
            // Fase 9.3: la cuota solo se guarda si el movimiento es un gasto y va en cuotas. El
            // precio de la compra (`originAmount`) se conserva: es de la compra, no de este mes,
            // pero se suelta si la compra deja de estar en cuotas.
            installmentNumber = if (onInstallments) installmentNumber else null,
            installmentTotal = if (onInstallments) total else null,
            originAmount = if (onInstallments) base.originAmount else null,
        )

        viewModelScope.launch {
            local.update { it.copy(saving = true, error = null) }
            val savedId = transactions.save(entity)
            if (isTransfer) {
                val leg = if (entity.id == 0L) entity.copy(id = savedId) else entity
                // Si la otra pata ya está anotada se enlaza igual, aunque no se pida crearla.
                syncCounterpart(leg, create = createCounterpart)
            }
            // Fase 3: la corrección del usuario se aprende y gana sobre la semilla del catálogo.
            val category = entity.categoryId
            if (rememberRule && !isTransfer && category != null) {
                rulesRepository.remember(entity.merchantRaw, entity.direction, category)
            }
            local.update { it.copy(saving = false, saved = true) }
        }
    }

    /**
     * Avisa si la otra pata del traspaso ya está anotada en la cuenta de destino, para ofrecer
     * crearla solo cuando falta. Se vuelve a consultar cuando cambia el monto, el sentido, las
     * cuentas o la fecha.
     */
    fun checkCounterpart(
        amountText: String,
        direction: Direction,
        accountId: Long?,
        transferAccountId: Long?,
        occurredAtMillis: Long,
    ) {
        counterpartJob?.cancel()
        val amount = Money.parse(amountText)
        if (amount == null || accountId == null || transferAccountId == null ||
            accountId == transferAccountId
        ) {
            local.update { it.copy(counterpartExists = false) }
            return
        }
        counterpartJob = viewModelScope.launch {
            val found = transactions.findCounterpart(
                amount = amount,
                direction = direction.opposite(),
                accountId = transferAccountId,
                at = occurredAtMillis,
            )
            local.update { it.copy(counterpartExists = found != null) }
        }
    }

    /**
     * Avisa si el comercio escrito ya tiene una regla guardada, para no volver a pedir la misma
     * categoría. Se consulta de nuevo al cambiar el comercio o el sentido.
     */
    fun checkRule(merchant: String, direction: Direction) {
        ruleJob?.cancel()
        if (merchant.isBlank()) {
            local.update { it.copy(rememberedCategoryId = null, hasRule = false) }
            return
        }
        ruleJob = viewModelScope.launch {
            val rule = rulesRepository.ruleFor(merchant, direction)
            val active = rule?.categoryId?.takeIf { id ->
                categoriesRepository.listActive().any { it.id == id }
            }
            local.update { it.copy(rememberedCategoryId = active, hasRule = rule != null) }
        }
    }

    /**
     * Deja las dos patas cuadradas: si la de destino ya existe la enlaza, y si falta y [create]
     * es `true` la crea con el sentido contrario, el mismo monto y la misma fecha.
     *
     * Un movimiento del mismo monto en la cuenta de destino que no sea traspaso no se toca ni se
     * duplica: se asume que es la otra pata anotada como gasto o ingreso y se deja como está.
     */
    private suspend fun syncCounterpart(leg: TransactionEntity, create: Boolean) {
        val destinationId = leg.transferAccountId ?: return
        val existing = transactions.findCounterpart(
            amount = leg.amount,
            direction = leg.direction.opposite(),
            accountId = destinationId,
            at = leg.occurredAt,
        )
        if (existing != null) {
            if (existing.isTransfer) transactions.linkTransfer(leg, existing)
            return
        }
        if (!create || leg.id == 0L) return

        val originName = leg.transferEnds().originId
            ?.let { id -> state.value.accounts.firstOrNull { it.id == id }?.name }
        val counterpartName = if (originName != null) "Traspaso desde $originName" else "Traspaso"
        val groupId = leg.transferGroupId ?: leg.id
        transactions.save(
            TransactionEntity(
                accountId = destinationId,
                occurredAt = leg.occurredAt,
                amount = leg.amount,
                direction = leg.direction.opposite(),
                merchantRaw = counterpartName,
                merchantNorm = counterpartName.lowercase(),
                // La otra pata tampoco lleva categoría: es el mismo traspaso.
                categoryId = null,
                status = TransactionStatus.CONFIRMED,
                isExpense = false,
                isTransfer = true,
                transferAccountId = leg.accountId,
                transferGroupId = groupId,
                source = TransactionSource.MANUAL,
            ),
        )
        // Las dos patas comparten el grupo, así se reconocen como el mismo traspaso.
        if (leg.transferGroupId != groupId) {
            transactions.save(leg.copy(transferGroupId = groupId))
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
