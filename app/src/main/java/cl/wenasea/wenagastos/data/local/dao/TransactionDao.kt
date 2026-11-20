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

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
