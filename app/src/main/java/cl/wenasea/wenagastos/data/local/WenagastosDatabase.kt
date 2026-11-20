package cl.wenasea.wenagastos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.CaptureLogDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CaptureLogEntity::class,
    ],
    version = WenagastosDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WenagastosDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun captureLogDao(): CaptureLogDao

    companion object {
        const val NAME = "wenagastos.db"
        const val VERSION = 2

        /** Fase 2: tabla de diagnóstico de la captura por notificaciones. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `capture_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `sourceLabel` TEXT,
                        `title` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `postedAt` INTEGER NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `detail` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_logs_postedAt` " +
                        "ON `capture_logs` (`postedAt`)",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
