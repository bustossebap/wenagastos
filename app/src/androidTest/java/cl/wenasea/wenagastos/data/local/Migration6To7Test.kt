package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Institution
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La migración 6 → 7 contra una base de verdad, no contra el esquema esperado.
 *
 * El truco para tener una base v6 sin copiar el DDL a mano: Room crea el esquema de hoy, se le
 * quitan las tres columnas nuevas y se baja `user_version` a 6 con el hash de identidad de
 * `schemas/…/6.json`. Al volver a abrir, Room ve la versión vieja y corre la `AutoMigration` de
 * verdad; después se comprueba que la cuenta y su corte siguen ahí, que las tres columnas quedaron
 * en `NULL` y que se pueden escribir.
 *
 * `DROP COLUMN` existe desde SQLite 3.35 (Android 12), así que la prueba necesita un aparato de
 * API 31 o más. Los nombres van sin backticks: el DEX de las pruebas instrumentadas no acepta
 * espacios en el nombre de un método.
 */
@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-6-7-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun laBaseV6SubeAV7ConLosDatosYLasTresColumnasVacias() = runBlocking {
        val cardId = withDatabase { db ->
            val id = db.accountDao().insert(
                AccountEntity(
                    name = "Tarjeta vieja",
                    institution = Institution.FALABELLA,
                    kind = AccountKind.CREDIT,
                    colorHex = "#7CB342",
                ),
            )
            db.accountSnapshotDao().insert(
                AccountSnapshotEntity(accountId = id, asOf = 1_000L, balance = -200_000L),
            )
            id
        }

        downgradeToSix()

        withDatabase { db ->
            val account = db.accountDao().getById(cardId)!!
            assertEquals("Tarjeta vieja", account.name)
            assertEquals(AccountKind.CREDIT, account.kind)
            assertNull("el cupo no existía en v6", account.creditLimit)
            assertNull("el corte no existía en v6", account.statementDay)
            assertNull("el pago no existía en v6", account.paymentDay)

            // El corte de la Fase 7 no se toca: la migración solo agrega columnas.
            assertEquals(1, db.accountSnapshotDao().count())

            // Y las columnas nuevas se escriben de verdad, no solo se leen.
            db.accountDao().update(
                account.copy(creditLimit = 1_500_000L, statementDay = 25, paymentDay = 5),
            )
            val saved = db.accountDao().getById(cardId)!!
            assertEquals(1_500_000L, saved.creditLimit)
            assertEquals(25, saved.statementDay)
            assertEquals(5, saved.paymentDay)
            assertEquals(WenagastosDatabase.VERSION, db.openHelper.readableDatabase.version)
        }
    }

    private fun open(): WenagastosDatabase = Room.databaseBuilder(
        context,
        WenagastosDatabase::class.java,
        name,
    ).build()

    /** Abre la base de la prueba, usa lo que haga falta y la cierra aunque falle una aserción. */
    private suspend fun <T> withDatabase(block: suspend (WenagastosDatabase) -> T): T {
        val db = open()
        return try {
            block(db)
        } finally {
            db.close()
        }
    }

    /**
     * Deja el archivo como lo dejaba la versión 6: las tres columnas fuera y `user_version` en 6.
     * El hash es el que exportó Room en `schemas/…/6.json`, para que la base mienta lo justo.
     */
    private fun downgradeToSix() {
        val file = context.getDatabasePath(name)
        PlainDatabase.openDatabase(file.path, null, PlainDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE accounts DROP COLUMN creditLimit")
            db.execSQL("ALTER TABLE accounts DROP COLUMN statementDay")
            db.execSQL("ALTER TABLE accounts DROP COLUMN paymentDay")
            db.execSQL("UPDATE room_master_table SET identity_hash = '$V6_IDENTITY_HASH'")
            db.execSQL("PRAGMA user_version = 6")
        }
    }

    private companion object {
        /** `database.identityHash` de `schemas/cl.wenasea.wenagastos.data.local.WenagastosDatabase/6.json`. */
        const val V6_IDENTITY_HASH = "d9c6f9e7caad944745e14413962f1070"
    }
}
