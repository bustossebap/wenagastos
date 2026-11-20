package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.CaptureLogDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.MerchantRuleDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.domain.TextNorm

class AccountRepository(private val dao: AccountDao) {

    fun observeAll() = dao.observeAll()

    fun observeActive() = dao.observeActive()

    suspend fun get(id: Long) = dao.getById(id)

    suspend fun listActive() = dao.getAllActive()

    suspend fun findByLast4(last4: String) = dao.findByLast4(last4)

    suspend fun add(account: AccountEntity) = dao.insert(account)

    suspend fun addAll(accounts: List<AccountEntity>) = dao.insertAll(accounts)

    suspend fun update(account: AccountEntity) = dao.update(account)

    /** Solo para cuentas sin movimientos: la FK `CASCADE` borraría su historia. */
    suspend fun delete(id: Long) = dao.deleteById(id)

    fun observeMovementCounts() = dao.observeMovementCounts()

    suspend fun countMovements(id: Long) = dao.countMovements(id)

    suspend fun count() = dao.count()
}

class CategoryRepository(private val dao: CategoryDao) {

    fun observeAll() = dao.observeAll()

    suspend fun get(id: Long) = dao.getById(id)

    suspend fun listActive() = dao.getAllActive()

    suspend fun add(category: CategoryEntity) = dao.insert(category)

    suspend fun addAll(categories: List<CategoryEntity>) = dao.insertAll(categories)

    suspend fun update(category: CategoryEntity) = dao.update(category)

    /**
     * Presupuesto mensual de una categoría. `null` (o 0) lo deja sin presupuesto; el resto de los
     * campos se conserva, así cambiar el monto no toca el nombre ni el color.
     */
    suspend fun setMonthlyBudget(id: Long, budget: Long?) {
        val category = dao.getById(id) ?: return
        val clean = budget?.takeIf { it > 0L }
        if (category.monthlyBudget == clean) return
        dao.update(category.copy(monthlyBudget = clean))
    }

    suspend fun count() = dao.count()
}

/**
 * Reglas por comercio: lo que el usuario corrige se recuerda y gana sobre la semilla
 * `MerchantCategories`. Una regla cuya categoría se archivó o desapareció se ignora, así el
 * motor cae a la semilla en vez de dejar el movimiento sin categoría válida.
 */
class MerchantRuleRepository(
    private val dao: MerchantRuleDao,
    private val categories: CategoryRepository,
) {

    fun observeAll() = dao.observeAll()

    suspend fun count() = dao.count()

    /** La regla guardada para [merchant], sin validar la categoría (la usa el editor). */
    suspend fun ruleFor(merchant: String, direction: Direction): MerchantRuleEntity? {
        val key = TextNorm.key(merchant)
        if (key.isEmpty()) return null
        return dao.find(key, direction.name)
    }

    /** Categoría recordada para [merchant] en ese sentido, si la regla sigue vigente. */
    suspend fun resolve(merchant: String, direction: Direction): Long? {
        val categoryId = ruleFor(merchant, direction)?.categoryId ?: return null
        val stillActive = categories.listActive().any { it.id == categoryId }
        return if (stillActive) categoryId else null
    }

    /**
     * Aprende la categoría de [merchant]. Si ya había una regla para ese comercio y sentido se
     * corrige y suma un uso: nunca se duplica.
     */
    suspend fun remember(merchant: String, direction: Direction, categoryId: Long) {
        val key = TextNorm.key(merchant)
        if (key.isEmpty() || categoryId <= 0L) return
        val label = merchant.trim().take(LABEL_MAX)
        val now = System.currentTimeMillis()
        val existing = dao.find(key, direction.name)
        if (existing == null) {
            dao.insert(
                MerchantRuleEntity(
                    merchantKey = key,
                    merchantLabel = label,
                    direction = direction,
                    categoryId = categoryId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            dao.update(
                existing.copy(
                    merchantLabel = label,
                    categoryId = categoryId,
                    uses = existing.uses + 1,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun forget(id: Long) = dao.deleteById(id)

    /** Cambia la categoría de una regla sin sumar un uso: no la enseñó el motor, la editaste tú. */
    suspend fun updateCategory(rule: MerchantRuleEntity, categoryId: Long) {
        if (categoryId <= 0L) return
        dao.update(rule.copy(categoryId = categoryId, updatedAt = System.currentTimeMillis()))
    }

    private companion object {
        /** Los nombres de comercio de los bancos son cortos; el resto suele ser relleno. */
        const val LABEL_MAX = 60
    }
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

    /** Movimiento con el mismo monto y sentido dentro de ±[windowMillis] milisegundos. */
    suspend fun findNearby(
        amount: Long,
        direction: Direction,
        at: Long,
        windowMillis: Long = 5 * 60_000L,
    ): TransactionEntity? = dao.findNearby(amount, direction.name, at - windowMillis, at + windowMillis)

    /** La otra pata de un traspaso que ya está anotada en [accountId]. */
    suspend fun findCounterpart(
        amount: Long,
        direction: Direction,
        accountId: Long,
        at: Long,
        windowMillis: Long = COUNTERPART_WINDOW,
    ): TransactionEntity? = dao.findCounterpart(
        amount,
        direction.name,
        accountId,
        at - windowMillis,
        at + windowMillis,
    )

    /** La otra pata de un traspaso cuando todavía no se sabe en qué cuenta quedó. */
    suspend fun findOppositeLeg(
        amount: Long,
        direction: Direction,
        accountId: Long,
        excludeId: Long,
        at: Long,
        windowMillis: Long = 5 * 60_000L,
    ): TransactionEntity? = dao.findOppositeLeg(
        amount,
        direction.name,
        accountId,
        excludeId,
        at - windowMillis,
        at + windowMillis,
    )

    /**
     * Deja las dos patas de un traspaso apuntándose entre sí, con un `transferGroupId` común.
     * Una pata ya guardada conserva su vínculo si no hay nada que corregir.
     */
    suspend fun linkTransfer(leg: TransactionEntity, counterpart: TransactionEntity) {
        if (leg.id == 0L || counterpart.id == 0L) return
        val groupId = leg.transferGroupId ?: counterpart.transferGroupId ?: leg.id
        if (leg.transferAccountId != counterpart.accountId || leg.transferGroupId != groupId) {
            save(leg.copy(transferAccountId = counterpart.accountId, transferGroupId = groupId))
        }
        if (counterpart.transferAccountId != leg.accountId ||
            counterpart.transferGroupId != groupId
        ) {
            save(counterpart.copy(transferAccountId = leg.accountId, transferGroupId = groupId))
        }
    }

    private companion object {
        /** Las dos patas de un traspaso pueden llegar con minutos de diferencia entre bancos. */
        const val COUNTERPART_WINDOW = 6 * 60 * 60_000L
    }
}

/** Bitácora de lo que ven las notificaciones (para la pantalla de diagnóstico). */
class CaptureLogRepository(private val dao: CaptureLogDao) {

    fun observeRecent(limit: Int = 100) = dao.observeRecent(limit)

    fun observeCount() = dao.observeCount()

    suspend fun add(log: CaptureLogEntity): Long {
        val id = dao.insert(log)
        dao.trim(CaptureLogEntity.MAX_ROWS)
        return id
    }

    suspend fun clear() = dao.clearAll()
}

