package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** La base no se pudo cifrar; quedó como estaba antes de empezar. */
class DbMigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Convierte la base de texto plano en una base cifrada con SQLCipher, una sola vez.
 *
 * La receta es la de Zetetic: abrir el archivo de texto plano con contraseña vacía, adjuntar un
 * archivo nuevo con la llave y copiar todo con `sqlcipher_export`. Después se verifica el archivo
 * nuevo (versión de Room y cantidad de filas por tabla) y recién ahí se cambia uno por otro,
 * dejando el viejo como `…db.old` hasta comprobar que el nuevo abre.
 *
 * `sqlcipher_export` copia también `room_master_table`; sin eso Room rechazaría la base por no
 * poder verificar la identidad del esquema. El `user_version` no se copia solo, así que se fija a
 * mano: Room lo lee para decidir qué migraciones correr.
 *
 * Todo corre antes de que Room abra el archivo, así que si algo falla la base original sigue
 * intacta y la app arranca en texto plano como siempre.
 */
class DbEncryption(private val context: Context) {

    /** Cifra la base si todavía está en texto plano; si ya está cifrada no hace nada. */
    fun migrateIfNeeded(passphrase: ByteArray) {
        val file = context.getDatabasePath(WenagastosDatabase.NAME)
        if (!file.exists()) return // instalación nueva: Room la crea cifrada
        if (!isPlainText(file)) return // ya está cifrada
        migrate(file, passphrase)
    }

    /**
     * Cifra [file] en el lugar, verificando antes de reemplazar. Es público para poder probarlo
     * con un archivo cualquiera en la prueba instrumentada.
     */
    fun migrate(file: File, passphrase: ByteArray) {
        val dir = file.parentFile ?: throw DbMigrationException("No hay carpeta para la base")
        val staging = File(dir, file.name + ".new")
        val previous = File(dir, file.name + ".old")
        staging.delete()
        previous.delete()

        try {
            val version = readVersion(file)
            val before = counts(file, key = null)

            export(file, staging, passphrase, version)

            if (counts(staging, passphrase) != before) {
                throw DbMigrationException("La copia cifrada no quedó completa")
            }
            if (readVersion(staging, passphrase) != version) {
                throw DbMigrationException("La copia cifrada perdió la versión del esquema")
            }
            if (isPlainText(staging)) {
                throw DbMigrationException("La copia quedó en texto plano")
            }

            if (!file.renameTo(previous)) {
                throw DbMigrationException("No pude apartar la base anterior")
            }
            deleteSideFiles(file) // -wal/-shm/-journal eran del archivo viejo
            if (!staging.renameTo(file)) {
                previous.renameTo(file)
                throw DbMigrationException("No pude poner la base cifrada en su lugar")
            }
            try {
                counts(file, passphrase)
            } catch (error: Throwable) {
                file.delete()
                deleteSideFiles(file)
                previous.renameTo(file)
                throw DbMigrationException("La base cifrada no abre; dejé la anterior", error)
            }
            previous.delete()
            deleteSideFiles(staging)
            Log.i(TAG, "Base cifrada: ${before.values.sum()} filas, versión $version")
        } catch (error: Throwable) {
            staging.delete()
            deleteSideFiles(staging)
            if (error is DbMigrationException) throw error
            throw DbMigrationException("No pude cifrar la base; quedó como estaba", error)
        }
    }

    /** Borra la base y sus archivos auxiliares. Solo se usa cuando la base no se puede abrir. */
    fun wipe() {
        val file = context.getDatabasePath(WenagastosDatabase.NAME)
        file.delete()
        deleteSideFiles(file)
        file.parentFile?.let { dir ->
            File(dir, file.name + ".new").delete()
            File(dir, file.name + ".old").delete()
        }
    }

    /**
     * Copia a un archivo cifrado nuevo.
     *
     * Dos detalles que cuestan caro si se pasan por alto: el archivo de destino tiene que **existir**
     * antes del `ATTACH` (SQLCipher abre el adjunto sin `CREATE` y si no está da "unable to open
     * database"), y la llave va como literal dentro del SQL porque SQLCipher la lee al preparar la
     * sentencia: con `?` no llega.
     */
    private fun export(file: File, staging: File, passphrase: ByteArray, version: Int) {
        if (!staging.exists()) staging.createNewFile()
        val source = open(file, key = null, readOnly = false)
        try {
            // La app usa WAL: primero se consolida, así el archivo principal queda completo.
            runCatching { source.execSQL("PRAGMA wal_checkpoint(FULL)") }
            val path = staging.path.replace("'", "''")
            val key = String(passphrase, Charsets.UTF_8)
            source.execSQL("ATTACH DATABASE '$path' AS encrypted KEY '$key'")
            source.rawQuery("SELECT sqlcipher_export('encrypted')").use { it.moveToFirst() }
            source.execSQL("PRAGMA encrypted.user_version = $version")
            source.execSQL("DETACH DATABASE encrypted")
        } finally {
            source.close()
        }
    }

    private fun counts(file: File, key: ByteArray?): Map<String, Int> {
        val database = open(file, key)
        return try {
            TABLES.associateWith { table ->
                database.rawQuery("SELECT count(*) FROM `$table`").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }
        } finally {
            database.close()
        }
    }

    private fun readVersion(file: File, key: ByteArray? = null): Int {
        val database = open(file, key)
        return try {
            database.rawQuery("PRAGMA user_version").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            database.close()
        }
    }

    /** Contraseña vacía = SQLCipher lee el archivo tal cual, sin descifrar. */
    private fun open(file: File, key: ByteArray?, readOnly: Boolean = true): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.path,
            key?.let { String(it, Charsets.UTF_8) } ?: "",
            null,
            if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE,
            null,
        )

    private fun deleteSideFiles(file: File) {
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
    }

    companion object {
        private const val TAG = "WenagastosDb"

        /** Las tablas que tienen que llegar completas a la copia cifrada. */
        private val TABLES = listOf(
            "accounts",
            "categories",
            "transactions",
            "capture_logs",
            "merchant_rules",
        )

        /** `true` si el archivo sigue siendo una base de SQLite sin cifrar. */
        fun isPlainText(file: File): Boolean {
            val header = ByteArray(DbHeader.SIZE)
            val read = runCatching { RandomAccessFile(file, "r").use { it.read(header) } }
                .getOrDefault(0)
            return read == DbHeader.SIZE && DbHeader.isPlainText(header)
        }
    }
}

