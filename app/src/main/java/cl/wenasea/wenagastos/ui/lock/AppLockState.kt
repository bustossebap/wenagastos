package cl.wenasea.wenagastos.ui.lock

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.wenasea.wenagastos.data.local.Prefs

/**
 * Estado del bloqueo al abrir, compartido por toda la app: vive en [cl.wenasea.wenagastos.AppContainer]
 * para que un cambio de configuración no lo reinicie.
 *
 * El bloqueo no cifra nada: la base sigue en el teléfono. Es una cortina para que quien tome el
 * teléfono desbloqueado no vea los movimientos; quien tenga el teléfono con root puede leer la
 * base igual. Por eso el aviso de Ajustes es explícito.
 */
class AppLockState(context: Context) {

    private val appContext = context.applicationContext

    var enabled by mutableStateOf(Prefs.isLockEnabled(appContext))
        private set

    var unlocked by mutableStateOf(!enabled)
        private set

    private var leftAt = 0L

    val locked: Boolean get() = enabled && !unlocked

    /** Activar o desactivar desde Ajustes: la sesión abierta no se cierra de golpe. */
    fun setLockEnabled(value: Boolean) {
        enabled = value
        Prefs.setLockEnabled(appContext, value)
        unlocked = true
        leftAt = 0L
    }

    /** La app pasó a segundo plano (o se abrió el selector de archivos). */
    fun onLeave() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /** Vuelve a primer plano: si estuvo fuera más que la gracia, se pide otra vez. */
    fun onReturn() {
        val awaySince = leftAt
        leftAt = 0L
        if (!enabled || awaySince == 0L || !unlocked) return
        if (SystemClock.elapsedRealtime() - awaySince >= GRACE_MS) unlocked = false
    }

    fun unlock() {
        unlocked = true
    }

    companion object {
        /** Abrir el selector de archivos o mirar una notificación no debería pedir la huella. */
        const val GRACE_MS = 60_000L
    }
}
