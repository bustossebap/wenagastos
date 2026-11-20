package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity

class AccountRepository(private val dao: AccountDao) {

    fun observeAll() = dao.observeAll()

    fun observeActive() = dao.observeActive()

    suspend fun get(id: Long) = dao.getById(id)

    suspend fun add(account: AccountEntity) = dao.insert(account)

    suspend fun addAll(accounts: List<AccountEntity>) = dao.insertAll(accounts)

    suspend fun update(account: AccountEntity) = dao.update(account)

    suspend fun count() = dao.count()
}

class CategoryRepository(private val dao: CategoryDao) {

    fun observeAll() = dao.observeAll()

    suspend fun get(id: Long) = dao.getById(id)

    suspend fun add(category: CategoryEntity) = dao.insert(category)

    suspend fun addAll(categories: List<CategoryEntity>) = dao.insertAll(categories)

    suspend fun update(category: CategoryEntity) = dao.update(category)

    suspend fun count() = dao.count()
}

class TransactionRepository(private val dao: TransactionDao) {

    fun observeBetween(from: Long, to: Long) = dao.observeBetween(from, to)

    fun observeRecent(limit: Int) = dao.observeRecent(limit)

    fun observePendingCount() = dao.observePendingCount()

    suspend fun get(id: Long) = dao.getById(id)

    /** Inserta si [transaction].id == 0, actualiza en caso contrario. */
    suspend fun save(transaction: TransactionEntity): Long {
        val now = System.currentTimeMillis()
        return if (transaction.id == 0L) {
            dao.insert(transaction.copy(createdAt = now, updatedAt = now))
        } else {
            dao.update(transaction.copy(updatedAt = now))
            transaction.id
        }
    }

    suspend fun delete(transaction: TransactionEntity) = dao.delete(transaction)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun existsByHash(hash: String) = dao.countByHash(hash) > 0
}
