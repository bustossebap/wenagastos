package cl.wenasea.wenagastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureLogDao {

    @Query("SELECT * FROM capture_logs ORDER BY postedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<CaptureLogEntity>>

    @Query("SELECT COUNT(*) FROM capture_logs")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(log: CaptureLogEntity): Long

    /** Deja solo las [keep] filas más nuevas. */
    @Query(
        "DELETE FROM capture_logs WHERE id NOT IN " +
            "(SELECT id FROM capture_logs ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int = CaptureLogEntity.MAX_ROWS)

    @Query("DELETE FROM capture_logs")
    suspend fun clearAll()
}
