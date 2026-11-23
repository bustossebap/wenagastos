package cl.wenasea.wenagastos.capture.fakes

import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.AccountMovementCount
import cl.wenasea.wenagastos.data.local.dao.AccountSnapshotDao
import cl.wenasea.wenagastos.data.local.dao.CaptureLogDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.MerchantRuleDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.numbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/*
 * DAO en memoria para probar el motor de captura sin Room ni Android.
 *
 * Cada implementación imita el SQL de su `@Query` (filtros, orden y límites), que es
 * justo lo que el motor da por cierto: si el fake ordena distinto, el test pasa o falla
 * por una razón que en el teléfono no ocurre.
 */

class FakeTransactionDao : TransactionDao {

    private val rows = linkedMapOf<Long, TransactionEntity>()
    private val state = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<TransactionEntity> get() = rows.values.toList()

    fun seed(vararg transactions: TransactionEntity): List<Long> {
        val ids = transactions.map(::insertSync)
        publish()
        return ids
    }

    private fun insertSync(transaction: TransactionEntity): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        rows[id] = transaction.copy(id = id)
        return id
    }

    private fun publish() {
        state.value = rows.values.sortedBy { it.id }
    }

    private fun newestFirst(list: List<TransactionEntity>) =
        list.sortedWith(
            compareByDescending<TransactionEntity> { it.occurredAt }.thenByDescending { it.id },
        )

    override fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>> =
        state.map { list -> newestFirst(list.filter { it.occurredAt >= from && it.occurredAt < to }) }

    override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> =
        state.map { list -> newestFirst(list).take(limit) }

    override fun observePendingCount(): Flow<Int> =
        state.map { list -> list.count { it.status == TransactionStatus.PENDING } }

    /**
     * La consulta real mira `account_snapshots`, tabla que este fake no conoce. El motor de
     * captura no pide saldos, así que devuelve vacío en vez de inventar cortes; el cálculo del
     * saldo se prueba en `domain/BalancesTest` con listas de entidades.
     */
    override fun observeSinceSnapshotCutoffs(): Flow<List<TransactionEntity>> =
        state.map { emptyList() }

    /** Espeja `occurredAt >= :from`; la ventana del ciclo de crédito la elige la pantalla. */
    override fun observeSince(from: Long): Flow<List<TransactionEntity>> =
        state.map { list -> list.filter { it.occurredAt >= from } }

    override suspend fun getById(id: Long): TransactionEntity? = rows[id]

    /** Espeja `BETWEEN :from AND :to` con `ORDER BY occurredAt ASC, id ASC LIMIT 1`. */
    override suspend fun findNearby(
        amount: Long,
        direction: String,
        from: Long,
        to: Long,
    ): TransactionEntity? = rows.values
        .filter { it.amount == amount && it.direction.name == direction && it.occurredAt in from..to }
        .minWithOrNull(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })

    /** Espeja `findAllNearbyIn`: mismo filtro que [findNearby] más la cuenta, con todos los iguales. */
    override suspend fun findAllNearbyIn(
        accountId: Long,
        amount: Long,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity> = rows.values
        .filter {
            it.amount == amount && it.direction.name == direction &&
                it.accountId == accountId && it.occurredAt in from..to
        }
        .sortedWith(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })

    override suspend fun countByHash(hash: String): Int =
        rows.values.count { it.dedupeHash == hash }

    override suspend fun insert(transaction: TransactionEntity): Long {
        val id = insertSync(transaction)
        publish()
        return id
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactions.forEach { insertSync(it) }
        publish()
    }

    /** Espeja `ORDER BY occurredAt ASC, id ASC`: el orden en que sale el export. */
    override suspend fun getAll(): List<TransactionEntity> =
        rows.values.sortedWith(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })

    override suspend fun update(transaction: TransactionEntity) {
        rows[transaction.id] = transaction
        publish()
    }

    override suspend fun delete(transaction: TransactionEntity) {
        rows.remove(transaction.id)
        publish()
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
        publish()
    }

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }

    /** Espeja el filtro de los traspasos cuyas dos patas caen en las dos cuentas. */
    override suspend fun countTransfersBetween(a: Long, b: Long): Int =
        rows.values.count { internalTransfer(it, a, b) }

    override suspend fun deleteTransfersBetween(a: Long, b: Long) {
        rows.values.filter { internalTransfer(it, a, b) }.forEach { rows.remove(it.id) }
        publish()
    }

    override suspend fun moveMovements(from: Long, to: Long, now: Long): Int {
        val moved = rows.values.filter { it.accountId == from }
        moved.forEach { rows[it.id] = it.copy(accountId = to, updatedAt = now) }
        publish()
        return moved.size
    }

    override suspend fun moveTransferLegs(from: Long, to: Long, now: Long): Int {
        val moved = rows.values.filter { it.transferAccountId == from }
        moved.forEach { rows[it.id] = it.copy(transferAccountId = to, updatedAt = now) }
        publish()
        return moved.size
    }

    /** Espeja `WHERE source = 'CSV' ORDER BY createdAt DESC, id DESC LIMIT 1`. */
    override suspend fun lastImported(): TransactionEntity? = rows.values
        .filter { it.source == TransactionSource.CSV }
        .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))

    /** Espeja el borrado acotado por cuenta, momento y `updatedAt <= createdAt`. */
    override suspend fun deleteImportedSince(accountId: Long, since: Long): Int {
        val doomed = rows.values.filter {
            it.source == TransactionSource.CSV && it.accountId == accountId &&
                it.createdAt >= since && it.updatedAt <= it.createdAt
        }
        doomed.forEach { rows.remove(it.id) }
        publish()
        return doomed.size
    }

    /** Espeja el conteo de los que se editaron después de importarlos. */
    override suspend fun countEditedSince(accountId: Long, since: Long): Int = rows.values.count {
        it.source == TransactionSource.CSV && it.accountId == accountId &&
            it.createdAt >= since && it.updatedAt > it.createdAt
    }

    private fun internalTransfer(tx: TransactionEntity, a: Long, b: Long): Boolean {
        val target = tx.transferAccountId ?: return false
        return (tx.accountId == a && target == b) || (tx.accountId == b && target == a)
    }

    /** Espeja el filtro por monto, sentido y cuenta, con el mismo orden. */
    override suspend fun findCounterpart(
        amount: Long,
        direction: String,
        accountId: Long,
        from: Long,
        to: Long,
    ): TransactionEntity? = rows.values
        .filter {
            it.amount == amount && it.direction.name == direction &&
                it.accountId == accountId && it.occurredAt in from..to
        }
        .minWithOrNull(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })

    /** Espeja el filtro en otra cuenta (`accountId != :accountId AND id != :excludeId`). */
    override suspend fun findOppositeLeg(
        amount: Long,
        direction: String,
        accountId: Long,
        excludeId: Long,
        from: Long,
        to: Long,
    ): TransactionEntity? = rows.values
        .filter {
            it.amount == amount && it.direction.name == direction &&
                it.accountId != accountId && it.id != excludeId && it.occurredAt in from..to
        }
        .minWithOrNull(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })
}

class FakeAccountDao : AccountDao {

    private val rows = linkedMapOf<Long, AccountEntity>()
    private val state = MutableStateFlow<List<AccountEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<AccountEntity> get() = rows.values.toList()

    /** Devuelve los ids asignados, en el orden en que se pasaron las cuentas. */
    fun seed(vararg accounts: AccountEntity): List<Long> {
        val ids = accounts.map { account ->
            val id = if (account.id == 0L) nextId++ else account.id
            rows[id] = account.copy(id = id)
            id
        }
        publish()
        return ids
    }

    private fun publish() {
        state.value = rows.values.sortedBy { it.id }
    }

    override fun observeAll(): Flow<List<AccountEntity>> = state.map { it }

    override fun observeActive(): Flow<List<AccountEntity>> =
        state.map { list -> list.filter { it.isActive } }

    override suspend fun getAllActive(): List<AccountEntity> =
        rows.values.filter { it.isActive }.sortedBy { it.id }

    override suspend fun getAll(): List<AccountEntity> = rows.values.sortedBy { it.id }

    override suspend fun findByLast4(last4: String): AccountEntity? = rows.values
        .filter { it.isActive && it.numbers().contains(last4) }
        .minByOrNull { it.id }

    override suspend fun getById(id: Long): AccountEntity? = rows[id]

    override suspend fun count(): Int = rows.size

    override suspend fun insert(account: AccountEntity): Long {
        val id = if (account.id == 0L) nextId++ else account.id
        rows[id] = account.copy(id = id)
        publish()
        return id
    }

    override suspend fun insertAll(accounts: List<AccountEntity>) {
        accounts.forEach { insert(it) }
    }

    override suspend fun update(account: AccountEntity) {
        rows[account.id] = account
        publish()
    }

    /**
     * El conteo real agrupa `transactions`, tabla que este fake no conoce. Los tests de Ajustes
     * lo simulan cargando [movementCounts].
     */
    var movementCounts: Map<Long, Int> = emptyMap()

    override fun observeMovementCounts(): Flow<List<AccountMovementCount>> =
        state.map { list ->
            list.map { account ->
                AccountMovementCount(account.id, movementCounts[account.id] ?: 0)
            }
        }

    override suspend fun countMovements(accountId: Long): Int = movementCounts[accountId] ?: 0

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
        publish()
    }

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }
}

/**
 * Cortes de saldo en memoria. Imita el SQL de [AccountSnapshotDao]: el "vigente" es el de `asOf`
 * más alto y, si empatan, el id más alto —el mismo desempate que `Balances.latestByAccount`.
 */
class FakeAccountSnapshotDao : AccountSnapshotDao {

    private val rows = linkedMapOf<Long, AccountSnapshotEntity>()
    private val state = MutableStateFlow<List<AccountSnapshotEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<AccountSnapshotEntity> get() = rows.values.toList()

    fun seed(vararg snapshots: AccountSnapshotEntity): List<Long> {
        val ids = snapshots.map { snapshot ->
            val id = if (snapshot.id == 0L) nextId++ else snapshot.id
            rows[id] = snapshot.copy(id = id)
            id
        }
        publish()
        return ids
    }

    private fun publish() {
        state.value = rows.values.sortedWith(
            compareBy<AccountSnapshotEntity> { it.accountId }
                .thenBy { it.asOf }
                .thenBy { it.id },
        )
    }

    override fun observeAll(): Flow<List<AccountSnapshotEntity>> = state.map { it }

    override fun observeFor(accountId: Long): Flow<List<AccountSnapshotEntity>> = state.map { list ->
        list.filter { it.accountId == accountId }
            .sortedWith(compareByDescending<AccountSnapshotEntity> { it.asOf }.thenByDescending { it.id })
    }

    override suspend fun latestFor(accountId: Long): AccountSnapshotEntity? = rows.values
        .filter { it.accountId == accountId }
        .maxWithOrNull(compareBy({ it.asOf }, { it.id }))

    override suspend fun getAll(): List<AccountSnapshotEntity> = stored

    override suspend fun count(): Int = rows.size

    /** Espeja `WHERE source = 'CARTOLA' ORDER BY createdAt DESC, id DESC LIMIT 1`. */
    override suspend fun lastImported(): AccountSnapshotEntity? = rows.values
        .filter { it.source == SnapshotSource.CARTOLA }
        .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))

    override suspend fun insert(snapshot: AccountSnapshotEntity): Long {
        val id = if (snapshot.id == 0L) nextId++ else snapshot.id
        rows[id] = snapshot.copy(id = id)
        publish()
        return id
    }

    override suspend fun insertAll(snapshots: List<AccountSnapshotEntity>) {
        snapshots.forEach { insert(it) }
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
        publish()
    }

    override suspend fun moveAccount(from: Long, to: Long): Int {
        val moving = rows.filterValues { it.accountId == from }.keys.toList()
        moving.forEach { id -> rows[id] = rows.getValue(id).copy(accountId = to) }
        publish()
        return moving.size
    }

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }
}

class FakeCategoryDao : CategoryDao {

    private val rows = linkedMapOf<Long, CategoryEntity>()
    private val state = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<CategoryEntity> get() = rows.values.toList()

    fun seed(vararg categories: CategoryEntity): List<Long> = seedAll(categories.toList())

    fun seedAll(categories: List<CategoryEntity>): List<Long> {
        val ids = categories.map(::insertSync)
        publish()
        return ids
    }

    private fun insertSync(category: CategoryEntity): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        rows[id] = category.copy(id = id)
        return id
    }

    private fun publish() {
        state.value = rows.values.sortedBy { it.id }
    }

    override fun observeAll(): Flow<List<CategoryEntity>> =
        state.map { list -> list.filter { !it.isArchived } }

    override suspend fun getAllActive(): List<CategoryEntity> = rows.values
        .filter { !it.isArchived }
        .sortedWith(compareBy<CategoryEntity> { it.kind.name }.thenBy { it.sortOrder }.thenBy { it.id })

    override suspend fun getAll(): List<CategoryEntity> = rows.values.sortedBy { it.id }

    override suspend fun getById(id: Long): CategoryEntity? = rows[id]

    override suspend fun count(): Int = rows.size

    override suspend fun insert(category: CategoryEntity): Long {
        val id = insertSync(category)
        publish()
        return id
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        categories.forEach { insertSync(it) }
        publish()
    }

    override suspend fun update(category: CategoryEntity) {
        rows[category.id] = category
        publish()
    }

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }
}

class FakeMerchantRuleDao : MerchantRuleDao {

    private val rows = linkedMapOf<Long, MerchantRuleEntity>()
    private val state = MutableStateFlow<List<MerchantRuleEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<MerchantRuleEntity> get() = rows.values.toList()

    private fun publish() {
        state.value = rows.values.sortedWith(
            compareByDescending<MerchantRuleEntity> { it.uses }.thenBy { it.merchantLabel },
        )
    }

    override fun observeAll(): Flow<List<MerchantRuleEntity>> = state

    override suspend fun getAll(): List<MerchantRuleEntity> = rows.values.sortedBy { it.id }

    override suspend fun find(key: String, direction: String): MerchantRuleEntity? =
        rows.values.firstOrNull { it.merchantKey == key && it.direction.name == direction }

    override suspend fun insert(rule: MerchantRuleEntity): Long {
        val id = if (rule.id == 0L) nextId++ else rule.id
        rows[id] = rule.copy(id = id)
        publish()
        return id
    }

    override suspend fun insertAll(rules: List<MerchantRuleEntity>) {
        rules.forEach { insert(it) }
    }

    override suspend fun update(rule: MerchantRuleEntity) {
        rows[rule.id] = rule
        publish()
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
        publish()
    }

    override suspend fun count(): Int = rows.size

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }
}

class FakeCaptureLogDao : CaptureLogDao {

    private val rows = linkedMapOf<Long, CaptureLogEntity>()
    private val state = MutableStateFlow<List<CaptureLogEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<CaptureLogEntity> get() = rows.values.toList()

    private fun publish() {
        state.value = rows.values.sortedBy { it.id }
    }

    override fun observeRecent(limit: Int): Flow<List<CaptureLogEntity>> = state.map { list ->
        list.sortedWith(
            compareByDescending<CaptureLogEntity> { it.postedAt }.thenByDescending { it.id },
        ).take(limit)
    }

    override fun observeCount(): Flow<Int> = state.map { it.size }

    override suspend fun insert(log: CaptureLogEntity): Long {
        val id = if (log.id == 0L) nextId++ else log.id
        rows[id] = log.copy(id = id)
        publish()
        return id
    }

    /** Espeja `DELETE ... WHERE id NOT IN (SELECT id ... ORDER BY id DESC LIMIT :keep)`. */
    override suspend fun trim(keep: Int) {
        if (rows.size <= keep) return
        val keepIds = rows.values.sortedByDescending { it.id }.take(keep).map { it.id }.toSet()
        rows.keys.retainAll(keepIds)
        publish()
    }

    override suspend fun clearAll() {
        rows.clear()
        publish()
    }
}
