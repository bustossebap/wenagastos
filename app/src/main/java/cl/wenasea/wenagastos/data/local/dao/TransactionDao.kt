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

    /**
     * Los movimientos de compras en cuotas (Fase 9.3), de todos los meses: una compra en 12 cuotas
     * empezó hace meses y sigue cobrándose, así que la pregunta «cuánto falta pagar» no cabe en un
     * mes. La pantalla de Reportes los agrupa por compra.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE installmentTotal IS NOT NULL
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    fun observeInstallments(): Flow<List<TransactionEntity>>

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
     * Igual que [findNearby] pero dentro de una cuenta y sin `LIMIT`: la importación de cartolas
     * necesita saber cuántos iguales hay, porque el mismo gasto puede estar dos veces (dos cafés
     * de $2.500 el mismo día) y cada fila de la cartola tiene que calzar con uno.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND direction = :direction AND accountId = :accountId
          AND occurredAt BETWEEN :from AND :to
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun findAllNearbyIn(
        accountId: Long,
        amount: Long,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity>

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
     * La otra pata de un traspaso que **todavía no está enlazada**: mismo monto, sentido contrario,
     * en otra cuenta, y las dos son traspasos sin pareja. La importación de cartolas la usa al
     * anotar una pata; las dos llegan en archivos distintos y con días de diferencia, así que la
     * pareja se busca en la base y gana la más cercana en el tiempo.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND direction = :direction AND accountId != :accountId
          AND id != :excludeId AND isTransfer = 1 AND transferAccountId IS NULL
          AND occurredAt BETWEEN :from AND :to
        ORDER BY ABS(occurredAt - :at) ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findUnlinkedLeg(
        amount: Long,
        direction: String,
        accountId: Long,
        excludeId: Long,
        at: Long,
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

    /**
     * La última fila que entró por cartola: dice qué cuenta y en qué momento se anotó. Lo usa
     * "Deshacer la última importación" (Fase 8.4): todo lo que comparte ese momento es de la misma
     * importación, porque se escribe dentro de una sola transacción.
     */
    @Query("SELECT * FROM transactions WHERE source = 'CSV' ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun lastImported(): TransactionEntity?

    /**
     * Borra lo que anotó una importación. Se pide `updatedAt <= createdAt` a propósito: un
     * movimiento que el usuario revisó o corrigió después ya no es "lo que dijo la cartola", y
     * deshacer la importación no puede llevarse ese trabajo.
     */
    @Query(
        """
        DELETE FROM transactions
        WHERE source = 'CSV' AND accountId = :accountId AND createdAt >= :since
          AND updatedAt <= createdAt
        """,
    )
    suspend fun deleteImportedSince(accountId: Long, since: Long): Int

    /** Los de esa importación que se quedan porque el usuario los tocó después. */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE source = 'CSV' AND accountId = :accountId AND createdAt >= :since
          AND updatedAt > createdAt
        """,
    )
    suspend fun countEditedSince(accountId: Long, since: Long): Int

    /** Vacía la tabla. Solo lo usa la restauración de un respaldo, dentro de una transacción. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
