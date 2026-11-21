package cl.wenasea.wenagastos

import android.app.Application
import androidx.room.Room
import cl.wenasea.wenagastos.capture.CaptureEngine
import cl.wenasea.wenagastos.data.Seeder
import cl.wenasea.wenagastos.data.local.DbEncryption
import cl.wenasea.wenagastos.data.local.DbKey
import cl.wenasea.wenagastos.data.local.WenagastosDatabase
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.BackupRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.MerchantRuleRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.importexport.DocumentFiles
import cl.wenasea.wenagastos.ui.lock.AppLockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Contenedor de dependencias (DI manual, sin Hilt).
 * Todo vive en el teléfono: no hay backend ni cliente HTTP.
 *
 * Al construirse deja la base lista para SQLCipher: recupera la llave del Keystore y, si el
 * archivo todavía está en texto plano, lo cifra. Room no abre el archivo hasta la primera
 * consulta, así que la migración ocurre antes de que nadie lea nada.
 */
class AppContainer(application: Application) {

    private val app = application

    /** Motivo por el que la base no se puede usar; `null` cuando todo salió bien. */
    val dbProblem: String?

    /** Contraseña que consume SQLCipher; `null` si la base no se pudo preparar. */
    private val passphrase: ByteArray?

    init {
        var key: ByteArray? = null
        var problem: String? = null
        try {
            System.loadLibrary("sqlcipher")
            val material = DbKey(app).load()
            val file = app.getDatabasePath(WenagastosDatabase.NAME)
            // Llave recién creada sobre un archivo ya cifrado: este teléfono perdió la anterior.
            if (material.created && file.exists() && !DbEncryption.isPlainText(file)) {
                problem = "La base está cifrada con una llave que este teléfono ya no tiene."
            } else {
                DbEncryption(app).migrateIfNeeded(material.passphrase)
                key = material.passphrase
            }
        } catch (error: Throwable) {
            problem = error.message ?: "No pude preparar la base cifrada."
        }
        dbProblem = problem
        passphrase = key
    }

    /**
     * `by lazy` para que la app pueda arrancar sin base: si [dbProblem] no es `null` nadie la
     * toca y la pantalla de error ofrece empezar de cero.
     */
    val database: WenagastosDatabase by lazy {
        val password = passphrase ?: error("La base no está disponible: $dbProblem")
        Room.databaseBuilder(app, WenagastosDatabase::class.java, WenagastosDatabase.NAME)
            .addMigrations(*WenagastosDatabase.ALL_MIGRATIONS)
            .openHelperFactory(SupportOpenHelperFactory(password, null, true))
            .build()
    }

    val accounts by lazy { AccountRepository(database.accountDao()) }
    val categories by lazy { CategoryRepository(database.categoryDao()) }
    val transactions by lazy { TransactionRepository(database.transactionDao()) }
    val captureLogs by lazy { CaptureLogRepository(database.captureLogDao()) }
    val rules by lazy { MerchantRuleRepository(database.merchantRuleDao(), categories) }

    /** Exportar y respaldar: la foto de las cuatro tablas y la restauración en bloque. */
    val backup by lazy {
        BackupRepository(
            database,
            database.accountDao(),
            database.categoryDao(),
            database.transactionDao(),
            database.merchantRuleDao(),
        )
    }

    /** Escribe y lee el archivo que el usuario elige en el selector del sistema. */
    val files = DocumentFiles(application.contentResolver)

    /** Bloqueo al abrir: estado en memoria, valor inicial en `SharedPreferences`. */
    val appLock = AppLockState(application)

    /** Motor que traduce notificaciones del banco en movimientos. */
    val capture by lazy {
        CaptureEngine(transactions, accounts, categories, rules, captureLogs)
    }

    /**
     * Deja el teléfono como recién instalado: borra la base y la llave. Solo se ofrece cuando la
     * base no se puede abrir, para que la app no quede en un ciclo de errores.
     */
    fun discardDatabase() {
        DbEncryption(app).wipe()
        DbKey(app).reset()
    }
}

class WenagastosApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.dbProblem == null) {
            appScope.launch {
                Seeder.seedIfEmpty(container.accounts, container.categories)
            }
        }
    }
}

