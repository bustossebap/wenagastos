package cl.wenasea.wenagastos.capture

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Lo que el sistema dice de una app sin pedir permisos: si está instalada, si no está
 * deshabilitada y si puede publicar notificaciones.
 */
data class AppFacts(
    val installed: Boolean = false,
    val enabled: Boolean = true,
    val canNotify: Boolean = true,
)

/**
 * Qué se puede esperar de una fuente. El caso que importa es [NOT_INSTALLED]: cuando el retiro
 * de Falabella no apareció, la app del banco estaba instalada como `cl.android` —fuera de la
 * lista de [BankSource]— y la de la tienda estaba deshabilitada. Nada de eso se veía desde la app,
 * y costó un movimiento perdido y una tarde de dudas.
 *
 * Una fuente con dos paquetes (Santander publica desde dos apps) queda en el mejor de los dos:
 * alcanza con una que avise. Se devuelve el peor solo cuando ninguna sirve.
 */
enum class SourceState(val label: String) {
    NOT_INSTALLED("No instalada"),
    DISABLED("Deshabilitada"),
    MUTED("Notificaciones apagadas"),
    READY("Lista"),
    ;

    companion object {
        fun of(facts: List<AppFacts>): SourceState = when {
            facts.any { it.installed && it.enabled && it.canNotify } -> READY
            facts.any { it.installed && it.enabled } -> MUTED
            facts.any { it.installed } -> DISABLED
            else -> NOT_INSTALLED
        }
    }
}

/** Una fuente y cómo está en este teléfono. */
data class SourceRow(
    val source: BankSource,
    val state: SourceState,
    /** El paquete que conviene abrir en Ajustes: el primero que esté instalado, si hay alguno. */
    val installedPackage: String?,
)

/**
 * Pregunta al sistema por cada paquete: `PackageManager` para instalada y habilitada,
 * `AppOpsManager` para el permiso de notificaciones. Son consultas sin permiso y baratas, pero
 * van a IO porque recorren la lista de apps instaladas.
 */
class AppLookup(private val context: Context) {

    fun facts(packageName: String): AppFacts {
        val info = applicationInfo(packageName) ?: return AppFacts(installed = false)
        return AppFacts(
            installed = true,
            enabled = info.enabled && !isDisabledByUser(packageName),
            canNotify = canNotify(packageName, info),
        )
    }

    /**
     * `ApplicationInfo.enabled` trae el estado del manifiesto, y no siempre el "desactivar" que el
     * usuario hizo desde Ajustes. La consulta explícita es la que gana: es justo el caso de la app
     * de la tienda Falabella, que estaba deshabilitada y por eso no publicaba nada.
     */
    private fun isDisabledByUser(packageName: String): Boolean = try {
        context.packageManager.getApplicationEnabledSetting(packageName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } catch (error: IllegalArgumentException) {
        false
    }

    /**
     * Una fila por fuente que declare paquetes: MACH llega solo por correo, no tiene app, y
     * [BankSource.SHELL] existe únicamente para las pruebas por `adb`.
     */
    fun rows(): List<SourceRow> = BankSource.entries
        .filter { it != BankSource.SHELL && it.packages.isNotEmpty() }
        .map { source ->
            val byPackage = source.packages.associateWith { facts(it) }
            SourceRow(
                source = source,
                state = SourceState.of(byPackage.values.toList()),
                installedPackage = byPackage.entries.firstOrNull { it.value.installed }?.key,
            )
        }

    private fun applicationInfo(packageName: String): ApplicationInfo? = try {
        context.packageManager.getApplicationInfo(packageName, 0)
    } catch (error: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * El permiso de notificaciones se publica como *app op*, no como permiso normal.
     * `MODE_DEFAULT` cuenta como permitido —es lo que responde el teléfono cuando nadie lo tocó—
     * y solo `MODE_IGNORED` o `MODE_ERRORED` significan que el aviso no sale. En Android viejo
     * consultar el op de otra app puede lanzar `SecurityException`: ahí se responde "no sé" en vez
     * de acusar a una app que quizá está bien.
     */
    private fun canNotify(packageName: String, info: ApplicationInfo): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(OP_POST_NOTIFICATION, info.uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(OP_POST_NOTIFICATION, info.uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
        } catch (error: SecurityException) {
            true
        }
    }

    private companion object {
        /** `AppOpsManager.OPSTR_POST_NOTIFICATION` existe desde API 28; el nombre es estable. */
        const val OP_POST_NOTIFICATION = "android:post_notification"
    }
}

/** Cómo llegar a los ajustes de una app, para arreglar desde la misma app lo que se ve mal. */
object AppSettings {

    /** Notificaciones de esa app. Es la pantalla que hay que tocar si el aviso no sale. */
    fun notifications(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Ficha de la app: el respaldo cuando el fabricante no tiene la pantalla anterior. */
    fun details(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
