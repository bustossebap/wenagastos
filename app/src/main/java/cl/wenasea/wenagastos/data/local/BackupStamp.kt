package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.content.pm.PackageManager
import cl.wenasea.wenagastos.domain.BackupReminder

/**
 * La fecha del último respaldo y, cuando nunca hubo uno, la de instalación: el recordatorio del
 * inicio necesita las dos para no convertirse en un aviso molesto el primer día.
 */
class BackupStamp(private val context: Context) {

    /** Se llama solo después de escribir el archivo: si falló, no hay nada que recordar. */
    fun mark(now: Long = System.currentTimeMillis()) = Prefs.setLastBackupAt(context, now)

    /** Milisegundos del último `.wena`; `0` si nunca se creó uno. */
    fun lastAt(): Long = Prefs.lastBackupAt(context)

    /** Fecha de instalación de la app, que es cuando empezó a existir el historial. */
    fun installedAt(now: Long = System.currentTimeMillis()): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (error: PackageManager.NameNotFoundException) {
        now
    }

    /** Texto del aviso, o `null` si todavía no toca pedir un respaldo. */
    fun reminder(now: Long = System.currentTimeMillis()): String? =
        BackupReminder.label(
            lastBackupAt = lastAt(),
            firstInstallAt = installedAt(now),
            now = now,
        )
}
