package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La migración 8 → 9 contra una base de verdad.
 *
 * Se crea la base v9 con Room, se le quitan las tres columnas de las cuotas y se baja `user_version`
 * a 8 con el hash de identidad de `schemas/…/8.json`; al volver a abrir, Room corre la
 * `AutoMigration` de verdad. Se comprueba lo que importa de la Fase 9.3: los movimientos de antes
 * siguen ahí y quedan **sin cuota**, y las tres columnas nuevas se escriben y se consultan de
 * verdad —`observeInstallments` solo trae las compras que van en cuotas—.
 *
 * `DROP COLUMN` existe desde SQLite 3.35 (Android 12), así que la prueba necesita un API 31 o más.
 */
@RunWith(AndroidJUnit4::class)
class Migration8To9Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-8-9-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun laBaseV8SubeV9ConLasCuotasVaciasYSePuedenEscribir() = runBlocking {
        val (cardId, normalId, compraId) = withDatabase { db ->
            val id = db.accountDao().insert(
                AccountEntity(
                    name = "CMR",
                    institution = Institution.FALABELLA,
                    kind = AccountKind.CREDIT,
                    colorHex = "#7CB342",
                ),
            )
            val normal = db.transactionDao().insert(movement(id, amount = 12_345L, merchant = "LIDER"))
            val compra = db.transactionDao().insert(movement(id, amount = 50_000L, merchant = "ALTAMAR SPA"))
            Triple(id, normal, compra)
        }

        downgradeToEight()

        withDatabase { db ->
            // Nada de lo que había se pierde: la migración solo agrega columnas.
            val todos = db.transactionDao().getAll()
            assertEquals(2, todos.size)
            // Un movimiento v8 no va en cuotas, que es distinto de «no se sabe».
            assertNull(todos.first { it.id == compraId }.installmentNumber)
            assertNull(todos.first { it.id == compraId }.installmentTotal)
            assertNull(todos.first { it.id == compraId }.originAmount)

            // Y sin cuotas anotadas no hay nada por pagar.
            assertEquals(0, db.transactionDao().observeInstallments().first().size)

            // Las columnas nuevas se escriben de verdad y la consulta de cuotas las encuentra.
            val compra = db.transactionDao().getById(compraId)!!
            db.transactionDao().update(
                compra.copy(installmentNumber = 3, installmentTotal = 12, originAmount = 600_000L),
            )

            val saved = db.transactionDao().getById(compraId)!!
            assertEquals(3, saved.installmentNumber)
            assertEquals(12, saved.installmentTotal)
            assertEquals(600_000L, saved.originAmount)
            assertEquals(cardId, saved.accountId)

            // Solo las compras en cuotas: el gasto normal no entra en «Cuotas por pagar».
            val cuotas = db.transactionDao().observeInstallments().first()
            assertEquals(listOf(compraId), cuotas.map { it.id })
            assertEquals(normalId, db.transactionDao().getById(normalId)!!.id)

            assertEquals(WenagastosDatabase.VERSION, db.openHelper.readableDatabase.version)
        }
    }

    private fun movement(accountId: Long, amount: Long, merchant: String) = TransactionEntity(
        accountId = accountId,
        occurredAt = 1_760_000_000_000L,
        amount = amount,
        direction = Direction.DEBIT,
        merchantRaw = merchant,
        merchantNorm = merchant.lowercase(),
    )

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
     * Deja el archivo como lo dejaba la versión 8: sin las tres columnas de las cuotas y con
     * `user_version` en 8. El hash es el que exportó Room en `schemas/…/8.json`.
     */
    private fun downgradeToEight() {
        val file = context.getDatabasePath(name)
        PlainDatabase.openDatabase(file.path, null, PlainDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE transactions DROP COLUMN installmentNumber")
            db.execSQL("ALTER TABLE transactions DROP COLUMN installmentTotal")
            db.execSQL("ALTER TABLE transactions DROP COLUMN originAmount")
            db.execSQL("UPDATE room_master_table SET identity_hash = '$V8_IDENTITY_HASH'")
            db.execSQL("PRAGMA user_version = 8")
        }
    }

    private companion object {
        /** `database.identityHash` de `schemas/cl.wenasea.wenagastos.data.local.WenagastosDatabase/8.json`. */
        const val V8_IDENTITY_HASH = "c55807c1931b2d010fc54f3c170d14df"
    }
}
