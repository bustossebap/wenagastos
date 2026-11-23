package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.CategoryNature
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La migración 7 → 8 contra una base de verdad.
 *
 * Se crea la base v8 con Room, se le quita la columna `nature` y se baja `user_version` a 7 con el
 * hash de identidad de `schemas/…/7.json`; al volver a abrir, Room corre `MIGRATION_7_8` de verdad.
 * Se comprueba lo que importa de esa migración: la categoría vieja queda **variable**, la que ya
 * se llamaba «Arriendo» no se duplica y queda **fija**, y las categorías nuevas entran con su
 * naturaleza.
 *
 * `DROP COLUMN` existe desde SQLite 3.35 (Android 12): hace falta un aparato de API 31 o más.
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-7-8-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun laBaseV7SubeAV8ConNaturalezaYCatalogoNuevo() = runBlocking {
        withDatabase { db ->
            // Una que el usuario ya tenía con el nombre de una de las nuevas, y una cualquiera.
            db.categoryDao().insert(
                CategoryEntity(
                    name = "Arriendo",
                    emoji = "🔑",
                    colorHex = "#5E35B1",
                    kind = CategoryKind.EXPENSE,
                    sortOrder = 75,
                ),
            )
            db.categoryDao().insert(
                CategoryEntity(
                    name = "Categoría vieja",
                    emoji = "❓",
                    colorHex = "#616161",
                    kind = CategoryKind.EXPENSE,
                    sortOrder = 300,
                ),
            )
        }

        downgradeToSeven()

        withDatabase { db ->
            val all = db.categoryDao().getAll()

            val vieja = all.first { it.name == "Categoría vieja" }
            assertEquals("sin clasificar es variable", CategoryNature.VARIABLE, vieja.nature)

            // La que existía no se duplica y la migración la marca fija por nombre.
            assertEquals(1, all.count { it.name == "Arriendo" })
            assertEquals(CategoryNature.FIXED, all.first { it.name == "Arriendo" }.nature)

            // Las que faltaban entran con su naturaleza.
            assertEquals(CategoryNature.SAVINGS, all.first { it.name == "Ahorro" }.nature)
            assertTrue(all.any { it.name == "Hogar" })
            assertTrue(all.any { it.name == "Fondo de emergencia" })
            assertEquals(CategoryKind.EXPENSE, all.first { it.name == "Hogar" }.kind)

            assertEquals(WenagastosDatabase.VERSION, db.openHelper.readableDatabase.version)
        }
    }

    private fun open(): WenagastosDatabase = Room.databaseBuilder(
        context,
        WenagastosDatabase::class.java,
        name,
    ).addMigrations(WenagastosDatabase.MIGRATION_7_8).build()

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
     * Deja el archivo como lo dejaba la versión 7: sin la columna `nature` y con `user_version` en
     * 7. El hash es el que exportó Room en `schemas/…/7.json`, para que la base mienta lo justo.
     */
    private fun downgradeToSeven() {
        val file = context.getDatabasePath(name)
        PlainDatabase.openDatabase(file.path, null, PlainDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE categories DROP COLUMN nature")
            db.execSQL("UPDATE room_master_table SET identity_hash = '$V7_IDENTITY_HASH'")
            db.execSQL("PRAGMA user_version = 7")
        }
    }

    private companion object {
        /** `database.identityHash` de `schemas/cl.wenasea.wenagastos.data.local.WenagastosDatabase/7.json`. */
        const val V7_IDENTITY_HASH = "39123b78aa724b057b8185d54ef4b363"
    }
}
