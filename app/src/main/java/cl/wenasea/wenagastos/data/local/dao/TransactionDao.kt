package cl.wenasea.wenagastos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAt >= :from AND occurredAt < :to
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** Busca un movimiento parecido: mismo monto y sentido, dentro de una ventana de tiempo. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND direction = :direction
          AND occurredAt BETWEEN :from AND :to
        ORDER BY occurredAt ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findNearby(
        amount: Long,
        direction: String,
        from: Long,
        to: Long,
    ): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE dedupeHash = :hash")
    suspend fun countByHash(hash: String): Int

    /**
     * La otra pata de un traspaso en una cuenta concreta: mismo monto, sentido contrario y
     * cerca en el tiempo. Lo usa el editor para no crear dos veces la misma pata.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND direction = :direction AND accountId = :accountId
          AND occurredAt BETWEEN :from AND :to
        ORDER BY occurredAt ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findCounterpart(
        amount: Long,
        direction: String,
        accountId: Long,
        from: Long,
        to: Long,
    ): TransactionEntity?

    /**
     * La otra pata cuando todavía no se sabe en qué cuenta está: mismo monto, sentido contrario,
     * en **otra** cuenta y sin ser el mismo movimiento.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND direction = :direction AND accountId != :accountId
          AND id != :excludeId AND occurredAt BETWEEN :from AND :to
        ORDER BY occurredAt ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findOppositeLeg(
        amount: Long,
        direction: String,
        accountId: Long,
        excludeId: Long,
        from: Long,
        to: Long,
    ): TransactionEntity?

    /**
     * Traspasos cuyas dos patas caen en estas dos cuentas: al unirlas dejan de tener sentido,
     * porque ya no hay plata que vaya de una a la otra.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE transferAccountId IS NOT NULL
          AND ((accountId = :a AND transferAccountId = :b)
            OR (accountId = :b AND transferAccountId = :a))
        """,
    )
    suspend fun countTransfersBetween(a: Long, b: Long): Int

    @Query(
        """
        DELETE FROM transactions
        WHERE transferAccountId IS NOT NULL
          AND ((accountId = :a AND transferAccountId = :b)
            OR (accountId = :b AND transferAccountId = :a))
        """,
    )
    suspend fun deleteTransfersBetween(a: Long, b: Long)

    /** Mueve el historial de una cuenta a otra. Lo usa la fusión de cuentas. */
    @Query("UPDATE transactions SET accountId = :to, updatedAt = :now WHERE accountId = :from")
    suspend fun moveMovements(from: Long, to: Long, now: Long): Int

    /** Repunta las patas de traspaso que apuntaban a la cuenta que desaparece. */
    @Query("UPDATE transactions SET transferAccountId = :to, updatedAt = :now WHERE transferAccountId = :from")
    suspend fun moveTransferLegs(from: Long, to: Long, now: Long): Int

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /** Restauración: los movimientos llegan con su id original, así se conservan los traspasos. */
    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Todos los movimientos en orden cronológico: es lo que sale en el export y el respaldo. */
    @Query("SELECT * FROM transactions ORDER BY occurredAt ASC, id ASC")
    suspend fun getAll(): List<TransactionEntity>

    /**
     * Solo lo que puede mover un saldo: los movimientos posteriores al corte más viejo que haya.
     * Cada cuenta suma los que están después de **su** corte, así que este conjunto es un
     * superconjunto y el cálculo fino se hace en `domain/Balances.kt`.
     *
     * Sin ningún corte no se devuelve nada, a propósito: el historial completo no sirve para
     * calcular saldos (empieza tarde y está incompleto) y cargarlo en cada refresco del inicio
     * sería pagar por nada.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAt > COALESCE(
            (SELECT MIN(asOf) FROM account_snapshots), 9223372036854775807
        )
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    fun observeSinceSnapshotCutoffs(): Flow<List<TransactionEntity>>

    /**
     * Los movimientos desde un instante, para el ciclo de una tarjeta de crédito (Fase 7.1): el
     * ciclo puede empezar antes que el corte de saldo, así que la ventana la pide la pantalla.
     */
    @Query(
        "SELECT * FROM transactions WHERE occurredAt >= :from ORDER BY occurredAt ASC, id ASC",
    )
    fun observeSince(from: Long): Flow<List<TransactionEntity>>

    /** Vacía la tabla. Solo lo usa la restauración de un respaldo, dentro de una transacción. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
