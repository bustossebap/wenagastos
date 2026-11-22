package cl.wenasea.wenagastos.data.repo

import androidx.room.withTransaction
import cl.wenasea.wenagastos.data.local.WenagastosDatabase
import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.AccountSnapshotDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.MerchantRuleDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.importexport.BackupData

/**
 * Todo lo que se exporta, en un solo lugar.
 *
 * Restaurar es reemplazar: se borra lo que hay y se insertan las filas del respaldo con sus ids
 * originales, todo dentro de una transacción. Los ids son el punto: con ellos un traspaso sigue
 * apuntando a su otra pata y una regla aprendida a su categoría. Si algo falla a mitad de camino,
 * Room revierte y la base queda como estaba: nunca se pierden los datos por un archivo malo.
 *
 * Las reglas se borran antes que las categorías, y los movimientos antes que las cuentas: las
 * claves foráneas son `CASCADE`/`SET_NULL` y el orden importa. Los saldos dependen de la cuenta,
 * así que se borran primero y se insertan después de ella.
 */
class BackupRepository(
    private val database: WenagastosDatabase,
    private val accounts: AccountDao,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
    private val rules: MerchantRuleDao,
    private val snapshots: AccountSnapshotDao,
) {

    /** Foto completa de la base, con las archivadas incluidas. */
    suspend fun snapshot(): BackupData = BackupData(
        accounts = accounts.getAll(),
        categories = categories.getAll(),
        rules = rules.getAll(),
        transactions = transactions.getAll(),
        snapshots = snapshots.getAll(),
    )

    suspend fun restore(data: BackupData) {
        database.withTransaction {
            snapshots.deleteAll()
            rules.deleteAll()
            transactions.deleteAll()
            categories.deleteAll()
            accounts.deleteAll()

            if (data.accounts.isNotEmpty()) accounts.insertAll(data.accounts)
            if (data.categories.isNotEmpty()) categories.insertAll(data.categories)
            if (data.rules.isNotEmpty()) rules.insertAll(data.rules)
            if (data.transactions.isNotEmpty()) transactions.insertAll(data.transactions)
            if (data.snapshots.isNotEmpty()) snapshots.insertAll(data.snapshots)
        }
    }

    companion object {
        /** Versión del esquema con la que se escribió el respaldo; queda anotada en el archivo. */
        val SCHEMA: Int = WenagastosDatabase.VERSION
    }
}
