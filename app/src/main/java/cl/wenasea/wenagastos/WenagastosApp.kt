package cl.wenasea.wenagastos

import android.app.Application
import androidx.room.Room
import cl.wenasea.wenagastos.capture.CaptureEngine
import cl.wenasea.wenagastos.data.Seeder
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

/**
 * Contenedor de dependencias (DI manual, sin Hilt).
 * Todo vive en el teléfono: no hay backend ni cliente HTTP.
 */
class AppContainer(application: Application) {

    val database: WenagastosDatabase = Room.databaseBuilder(
        application,
        WenagastosDatabase::class.java,
        WenagastosDatabase.NAME,
    )
        .addMigrations(*WenagastosDatabase.ALL_MIGRATIONS)
        .build()

    val accounts = AccountRepository(database.accountDao())
    val categories = CategoryRepository(database.categoryDao())
    val transactions = TransactionRepository(database.transactionDao())
    val captureLogs = CaptureLogRepository(database.captureLogDao())
    val rules = MerchantRuleRepository(database.merchantRuleDao(), categories)

    /** Exportar y respaldar: la foto de las cuatro tablas y la restauración en bloque. */
    val backup = BackupRepository(
        database,
        database.accountDao(),
        database.categoryDao(),
        database.transactionDao(),
        database.merchantRuleDao(),
    )

    /** Escribe y lee el archivo que el usuario elige en el selector del sistema. */
    val files = DocumentFiles(application.contentResolver)

    /** Bloqueo al abrir: estado en memoria, valor inicial en `SharedPreferences`. */
    val appLock = AppLockState(application)

    /** Motor que traduce notificaciones del banco en movimientos. */
    val capture = CaptureEngine(transactions, accounts, categories, rules, captureLogs)
}

class WenagastosApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            Seeder.seedIfEmpty(container.accounts, container.categories)
        }
    }
}
