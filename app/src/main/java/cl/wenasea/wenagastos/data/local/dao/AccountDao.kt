package cl.wenasea.wenagastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    suspend fun getAllActive(): List<AccountEntity>

    /**
     * Busca una cuenta activa por número exacto. `last4` guarda una lista separada por comas,
     * así que se compara con los delimitadores puestos: `1234` no matchea dentro de otro número.
     */
    @Query(
        "SELECT * FROM accounts WHERE isActive = 1 " +
            "AND ',' || last4 || ',' LIKE '%,' || :last4 || ',%' " +
            "ORDER BY id ASC LIMIT 1",
    )
    suspend fun findByLast4(last4: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)
}
