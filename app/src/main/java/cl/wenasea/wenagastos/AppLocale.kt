package cl.wenasea.wenagastos

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Idioma de la app, independiente del idioma del teléfono.
 *
 * El A34 está en `es-US`, así que sin esto el calendario de Material 3 y los textos
 * de plataforma aparecerían en inglés. Los textos propios ya están en español en el código.
 */
object AppLocale {

    val LOCALE: Locale = Locale.forLanguageTag("es-CL")

    /** Copia el [Context] base con el locale de la app aplicado. */
    fun wrap(base: Context): Context {
        Locale.setDefault(LOCALE)
        val config = Configuration(base.resources.configuration)
        config.setLocale(LOCALE)
        config.setLocales(LocaleList(LOCALE))
        return base.createConfigurationContext(config)
    }
}
