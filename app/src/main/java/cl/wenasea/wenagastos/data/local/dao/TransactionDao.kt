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

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
