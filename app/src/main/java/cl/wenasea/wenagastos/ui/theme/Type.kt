package cl.wenasea.wenagastos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cl.wenasea.wenagastos.R

/*
 * Del manual de marca (p. 14): Ouma Latin para display e Instrument Sans para texto y tagline.
 * Ouma no se puede empaquetar — licencia de uso de Adobe Fonts, no de distribución — así que la
 * escala display queda en Instrument Sans con tracking cerrado; el contraste de pesos hace el
 * trabajo que hacía la display.
 *
 * Instrument Sans: SIL OFL 1.1, instancias estáticas 400/600/700 descargadas de Google Fonts
 * (app/src/main/assets/licenses/instrument-sans-OFL.txt).
 *
 * VG5000: SIL OFL 1.1, Velvetyne / Justin Bihan; es la tipografía del logotipo del manual y se
 * reserva para la cifra protagonista, nunca para texto corrido. Vive en [HeroAmount]: ahí no se
 * fuerza ningún peso, porque la familia tiene uno solo y engordarla sintéticamente mancha el trazo.
 */

private const val CIFRAS_TABULARES = "tnum"

private val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

/** Familia de un solo peso, para los montos del héroe (etapa 3). */
val Vg5000 = FontFamily(Font(R.font.vg5000_regular, FontWeight.Normal))

/**
 * Cifra protagonista: VG5000 en su único peso, sin tocar `fontWeight` — engordarla sintéticamente
 * mancha el trazo. El tamaño y el trigo sobre marino hacen el énfasis. Cabe hasta "$12.345.678"
 * en un teléfono de 360 dp sin encoger.
 */
val HeroAmount = TextStyle(
    fontFamily = Vg5000,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = 0.5.sp,
)

/*
 * Las cifras tabulares van en todos los roles: en una lista de gastos los montos quedan
 * alineados en columna sin trucos de ancho fijo. Instrument Sans trae la característica `tnum`.
 */
val WenagastosTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = CIFRAS_TABULARES,
    ),
    displayMedium = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = CIFRAS_TABULARES,
    ),
    displaySmall = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Bold,
        fontSize = 33.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = CIFRAS_TABULARES,
    ),
    headlineLarge = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    ),
)

