package cl.wenasea.wenagastos.data.local

import android.content.Context

/**
 * Preferencias que no son datos financieros: el bloqueo al abrir, el modo de color y la fecha del
 * último respaldo. Van en `SharedPreferences` y no en Room a propósito: son de la instalación, no
 * del historial, y no tienen por qué viajar en el respaldo.
 */
object Prefs {

    private const val FILE = "wenagastos.prefs"
    private const val KEY_LOCK = "bloqueo_al_abrir"
    private const val KEY_BACKUP = "ultimo_respaldo"
    private const val KEY_THEME = "tema"

    fun isLockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK, false)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK, enabled).apply()
    }

    /**
     * Modo de color elegido: `"sistema"`, `"claro"` u `"oscuro"`. `null` —lo que no eligió nadie—
     * se lee como automático, el modo del teléfono.
     */
    fun themeKey(context: Context): String? = prefs(context).getString(KEY_THEME, null)

    fun setThemeKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_THEME, key).apply()
    }

    /**
     * Cuándo se escribió el último `.wena` completo, en milisegundos. `0` cuando nunca hubo uno:
     * el recordatorio del inicio usa la fecha de instalación como punto de partida en ese caso.
     */
    fun lastBackupAt(context: Context): Long = prefs(context).getLong(KEY_BACKUP, 0L)

    fun setLastBackupAt(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_BACKUP, millis).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
