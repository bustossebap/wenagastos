package cl.wenasea.wenagastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * Cortes de saldo por cuenta (Fase 7). Se guardan todos los que hubo: el saldo actual usa el más
 * nuevo, pero el historial sirve para revisar de dónde salió. Las cuentas con movimientos no se
 * borran (se archivan), así que sus cortes tampoco se pierden por accidente; el `CASCADE` solo
 * actúa en un borrado de verdad o en una restauración de respaldo.
 */
@Dao
interface AccountSnapshotDao {

    @Query("SELECT * FROM account_snapshots ORDER BY accountId ASC, asOf ASC, id ASC")
    fun observeAll(): Flow<List<AccountSnapshotEntity>>

    /** En orden cronológico inverso: el primero es el corte vigente. */
    @Query("SELECT * FROM account_snapshots WHERE accountId = :accountId ORDER BY asOf DESC, id DESC")
    fun observeFor(accountId: Long): Flow<List<AccountSnapshotEntity>>

    @Query(
        "SELECT * FROM account_snapshots WHERE accountId = :accountId " +
            "ORDER BY asOf DESC, id DESC LIMIT 1",
    )
    suspend fun latestFor(accountId: Long): AccountSnapshotEntity?

    @Query("SELECT * FROM account_snapshots ORDER BY accountId ASC, asOf ASC, id ASC")
    suspend fun getAll(): List<AccountSnapshotEntity>

    @Query("SELECT COUNT(*) FROM account_snapshots")
    suspend fun count(): Int

    @Insert
    suspend fun insert(snapshot: AccountSnapshotEntity): Long

    @Insert
    suspend fun insertAll(snapshots: List<AccountSnapshotEntity>)

    /** Se usa cuando un corte se cargó mal: se borra y el anterior vuelve a mandar. */
    @Query("DELETE FROM account_snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Mueve los cortes a otra cuenta. Lo usa la fusión: el saldo no puede quedar en el aire. */
    @Query("UPDATE account_snapshots SET accountId = :to WHERE accountId = :from")
    suspend fun moveAccount(from: Long, to: Long): Int

    /** Vacía la tabla. Solo lo usa la restauración de un respaldo, dentro de una transacción. */
    @Query("DELETE FROM account_snapshots")
    suspend fun deleteAll()
}