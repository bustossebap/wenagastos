package cl.wenasea.wenagastos.capture.fakes

import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.CaptureLogDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
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

    override suspend fun countByHash(hash: String): Int =
        rows.values.count { it.dedupeHash == hash }

    override suspend fun insert(transaction: TransactionEntity): Long {
        val id = insertSync(transaction)
        publish()
        return id
    }

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

    override suspend fun findByLast4(last4: String): AccountEntity? = rows.values
        .filter { it.isActive && it.last4 == last4 }
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
