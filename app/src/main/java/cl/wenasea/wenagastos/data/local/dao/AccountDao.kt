package cl.wenasea.wenagastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** Cuántos movimientos tiene una cuenta: decide si se puede borrar o hay que archivarla. */
data class AccountMovementCount(
    val accountId: Long,
    val total: Int,
)

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    suspend fun getAllActive(): List<AccountEntity>

    /** Todas, incluidas las archivadas: es lo que viaja en el respaldo. */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAll(): List<AccountEntity>

    /** Conteo de movimientos por cuenta; una cuenta con movimientos se archiva, no se borra. */
    @Query("SELECT accountId, COUNT(*) AS total FROM transactions GROUP BY accountId")
    fun observeMovementCounts(): Flow<List<AccountMovementCount>>

    /**
     * Busca una cuenta activa por número exacto, sea el de la cuenta o el de una de sus tarjetas.
     * `cardLast4` guarda una lista separada por comas, así que se compara con los delimitadores
     * puestos: `1234` no matchea dentro de otro número. La cuenta gana sobre la tarjeta porque el
     * número de cuenta es único; entre varias tarjetas iguales gana la de menor id.
     */
    @Query(
        "SELECT * FROM accounts WHERE isActive = 1 " +
            "AND (accountLast4 = :last4 OR ',' || cardLast4 || ',' LIKE '%,' || :last4 || ',%') " +
            "ORDER BY id ASC LIMIT 1",
    )
    suspend fun findByLast4(last4: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    /** Conteo puntual, para decidir si una cuenta se puede borrar o hay que archivarla. */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countMovements(accountId: Long): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    /**
     * Borrado real. Solo se usa cuando la cuenta no tiene movimientos: la FK `accountId` es
     * `CASCADE`, así que borrar una cuenta con historia se llevaría sus movimientos.
     */
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Vacía la tabla. Solo lo usa la restauración de un respaldo, dentro de una transacción. */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
