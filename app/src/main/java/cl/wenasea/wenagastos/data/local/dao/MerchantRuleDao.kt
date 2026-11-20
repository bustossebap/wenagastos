package cl.wenasea.wenagastos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reglas aprendidas por comercio (Fase 3). La única llave es `merchantKey + direction`:
 * volver a enseñar la misma categoría actualiza la fila en vez de crear otra.
 */
@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules ORDER BY uses DESC, merchantLabel ASC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    /** Todas las reglas, para el respaldo. */
    @Query("SELECT * FROM merchant_rules ORDER BY id ASC")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Query(
        """
        SELECT * FROM merchant_rules
        WHERE merchantKey = :key AND direction = :direction
        LIMIT 1
        """,
    )
    suspend fun find(key: String, direction: String): MerchantRuleEntity?

    @Insert
    suspend fun insert(rule: MerchantRuleEntity): Long

    /** Restauración: las reglas llegan con su id original, así siguen apuntando a su categoría. */
    @Insert
    suspend fun insertAll(rules: List<MerchantRuleEntity>)

    @Update
    suspend fun update(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM merchant_rules")
    suspend fun count(): Int

    /** Vacía la tabla. Solo lo usa la restauración de un respaldo, dentro de una transacción. */
    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()
}
