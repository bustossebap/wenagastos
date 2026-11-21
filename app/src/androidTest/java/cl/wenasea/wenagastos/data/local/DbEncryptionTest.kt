package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migración de verdad, contra SQLCipher y su librería nativa: un archivo en texto plano con datos
 * pasa a cifrado sin perder filas, y después **nadie** puede leerlo sin la llave.
 *
 * Es lo único de la Fase 5.1 que no se puede probar en la JVM.
 *
 * Los nombres van sin backticks a propósito: el DEX de las pruebas instrumentadas no acepta
 * espacios en el nombre de un método (solo a partir de DEX 040).
 */
@RunWith(AndroidJUnit4::class)
class DbEncryptionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        .toByteArray(Charsets.UTF_8)
    private lateinit var dir: File

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        dir = File(context.cacheDir, "db-encryption-test")
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun cifraLaBaseYNoPierdeNingunaFila() {
        val file = File(dir, "wenagastos.db")
        createPlainTextDatabase(file, rows = 3)

        DbEncryption(context).migrate(file, key)

        assertFalse("el archivo quedó en texto plano", DbEncryption.isPlainText(file))
        assertFalse("quedó el archivo temporal", File(file.path + ".new").exists())
        assertFalse("quedó la base anterior", File(file.path + ".old").exists())
        assertEquals(4, userVersion(file, key))
        assertEquals(3, count(file, key))
    }

    @Test
    fun sinLaLlaveElArchivoNoAbreNiConSqliteComun() {
        val file = File(dir, "wenagastos.db")
        createPlainTextDatabase(file, rows = 2)
        DbEncryption(context).migrate(file, key)

        val other = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
            .toByteArray(Charsets.UTF_8)
        assertThrows(Exception::class.java) { count(file, other) }

        assertThrows(Exception::class.java) {
            val plain = PlainDatabase.openDatabase(file.path, null, PlainDatabase.OPEN_READONLY)
            try {
                plain.rawQuery("SELECT count(*) FROM transactions", null).use { it.moveToFirst() }
            } finally {
                plain.close()
            }
        }
    }

    @Test
    fun unaBaseYaCifradaSeQuedaIntactaAlCifrarOtraVez() {
        val file = File(dir, "wenagastos.db")
        createPlainTextDatabase(file, rows = 2)
        DbEncryption(context).migrate(file, key)

        val other = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
            .toByteArray(Charsets.UTF_8)
        assertThrows(DbMigrationException::class.java) {
            DbEncryption(context).migrate(file, other)
        }

        assertFalse(DbEncryption.isPlainText(file))
        assertEquals(2, count(file, key))
    }

    private fun createPlainTextDatabase(file: File, rows: Int) {
        val database = PlainDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountCents INTEGER NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS capture_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, body TEXT NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS merchant_rules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, merchant TEXT NOT NULL)",
            )
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            for (index in 1..rows) {
                database.execSQL("INSERT INTO transactions (amountCents) VALUES (${index * 1000})")
            }
            database.execSQL("PRAGMA user_version = 4")
        } finally {
            database.close()
        }
    }

    private fun count(file: File, key: ByteArray): Int {
        val database = open(file, key)
        return try {
            database.rawQuery("SELECT count(*) FROM transactions").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            database.close()
        }
    }

    private fun userVersion(file: File, key: ByteArray): Int {
        val database = open(file, key)
        return try {
            database.rawQuery("PRAGMA user_version").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            database.close()
        }
    }

    private fun open(file: File, key: ByteArray): SQLiteDatabase = SQLiteDatabase.openDatabase(
        file.path,
        String(key, Charsets.UTF_8),
        null,
        SQLiteDatabase.OPEN_READWRITE,
        null,
    )
}
