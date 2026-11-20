package cl.wenasea.wenagastos.data.local

import android.content.Context

/**
 * Preferencias que no son datos financieros (hoy, solo el bloqueo al abrir). Van en
 * `SharedPreferences` y no en Room a propósito: son de la instalación, no del historial, y no
 * tienen por qué viajar en el respaldo.
 */
object Prefs {

    private const val FILE = "wenagastos.prefs"
    private const val KEY_LOCK = "bloqueo_al_abrir"

    fun isLockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK, false)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
