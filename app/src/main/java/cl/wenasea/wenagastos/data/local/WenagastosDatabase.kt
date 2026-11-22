package cl.wenasea.wenagastos.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.AccountSnapshotDao
import cl.wenasea.wenagastos.data.local.dao.CaptureLogDao
import cl.wenasea.wenagastos.data.local.dao.CategoryDao
import cl.wenasea.wenagastos.data.local.dao.MerchantRuleDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        AccountSnapshotEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CaptureLogEntity::class,
        MerchantRuleEntity::class,
    ],
    version = WenagastosDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // Fase 4: columna `transferAccountId` (la otra pata del traspaso) e índice.
        AutoMigration(from = 2, to = 3),
        // Fase 3: tabla `merchant_rules` (categoría aprendida por comercio) e índice único.
        AutoMigration(from = 3, to = 4),
        // Fase 7: tabla `account_snapshots` (saldo confirmado por cuenta) e índice `accountId + asOf`.
        AutoMigration(from = 5, to = 6),
    ],
)
@TypeConverters(Converters::class)
abstract class WenagastosDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun accountSnapshotDao(): AccountSnapshotDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun captureLogDao(): CaptureLogDao

    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        const val NAME = "wenagastos.db"
        const val VERSION = 6

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

        /**
         * Fase 6: `last4` se parte en el número de la cuenta y los de sus tarjetas, para que el
         * editor diga qué es cada número en vez de una lista sin dueño. Se recrea la tabla porque
         * `DROP COLUMN` no existe en el SQLite de `minSdk 26`; Room desactiva las claves foráneas
         * durante la migración, así que `transactions.accountId` no se entera.
         *
         * Reparto de lo que hubiera: en una corriente o ahorro el primer número es el de la cuenta
         * y el resto son tarjetas (el orden en que se agregaban); en débito y crédito, todo es
         * número de tarjeta.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `institution` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `accountLast4` TEXT,
                        `cardLast4` TEXT,
                        `colorHex` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO accounts_new
                        (id, name, institution, kind, accountLast4, cardLast4, colorHex, isActive, createdAt)
                    SELECT id, name, institution, kind,
                        CASE
                            WHEN kind IN ('CHECKING', 'SAVINGS') AND instr(last4, ',') > 0
                                THEN substr(last4, 1, instr(last4, ',') - 1)
                            WHEN kind IN ('CHECKING', 'SAVINGS') THEN last4
                        END,
                        CASE
                            WHEN kind IN ('DEBIT', 'CREDIT') THEN last4
                            WHEN kind IN ('CHECKING', 'SAVINGS') AND instr(last4, ',') > 0
                                THEN substr(last4, instr(last4, ',') + 1)
                        END,
                        colorHex, isActive, createdAt
                    FROM accounts
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_accounts_institution_kind_accountLast4 " +
                        "ON `accounts` (institution, kind, accountLast4)",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_4_5)
    }
}
