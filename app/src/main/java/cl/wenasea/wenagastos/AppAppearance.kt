package cl.wenasea.wenagastos

import android.content.Context
import android.content.res.Configuration
import cl.wenasea.wenagastos.data.local.Prefs

/**
 * Modo de color elegido en Ajustes.
 *
 * El manual no trae modo oscuro y la app lo deduce del marino, pero quién decide qué modo se pinta
 * es el usuario: `SYSTEM` sigue al teléfono, `LIGHT` deja el papel crema y `DARK` el papel marino,
 * aunque el teléfono diga lo contrario.
 */
enum class ThemeMode(val key: String) {
    SYSTEM("sistema"),
    LIGHT("claro"),
    DARK("oscuro");

    companion object {
        /** Lo guardado, o automático si no hay nada o el valor no se reconoce. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Aplica el modo de color antes de que la actividad infle nada.
 *
 * Se sobreescribe `uiMode` en la configuración del contexto base, igual que [AppLocale] hace con el
 * idioma, y no se pasa `darkTheme` a mano a Compose. Así una sola decisión alcanza a todo lo que
 * mira la configuración: `isSystemInDarkTheme()`, los recursos `values-night` —el papel de la
 * ventana en el arranque en frío— y el color de los iconos de la barra de estado que instala
 * `enableEdgeToEdge()`.
 */
object AppAppearance {

    fun wrap(base: Context): Context {
        val mode = ThemeMode.fromKey(Prefs.themeKey(base))
        if (mode == ThemeMode.SYSTEM) return base
        val configuration = Configuration(base.resources.configuration)
        val night = if (mode == ThemeMode.DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        configuration.uiMode =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(configuration)
    }
}
