package cl.wenasea.wenagastos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Modo oscuro: el manual no lo trae, se deduce de la familia del marino (#383A5B), que de día
 * funciona como tinta y de noche pasa a ser el papel. Los acentos van al revés que en claro:
 * donde terracota, salvia y trigo no alcanzan AA sobre crema, sí lo alcanzan sobre #1C1E30
 * (5.57:1, 6.60:1 y 10.72:1 respectivamente).
 *
 * Los papeles M3 se mantienen iguales que en claro para que la app no cambie de significado al
 * cambiar de modo: primary es la marca, secondary el cargo (terracota) y tertiary el abono
 * (salvia). Así el gráfico de Reportes y el semáforo de presupuesto conservan su color.
 */
private val DarkColors = darkColorScheme(
    primary = Trigo,
    onPrimary = Tinta,
    primaryContainer = Color(0xFF4A3C15),
    onPrimaryContainer = Trigo,
    secondary = Terracota,
    onSecondary = Color(0xFF2A1008),
    secondaryContainer = Color(0xFF55291E),
    onSecondaryContainer = Color(0xFFF7C3B3),
    tertiary = Salvia,
    onTertiary = Color(0xFF11241A),
    tertiaryContainer = Color(0xFF2A4A3B),
    onTertiaryContainer = Color(0xFFB7DCC8),
    background = Noche,
    onBackground = Crema,
    surface = Noche,
    onSurface = Crema,
    surfaceVariant = NocheCapa3,
    onSurfaceVariant = PenumbraNoche,
    surfaceContainerLowest = NocheHueco,
    surfaceContainerLow = NocheCapa1,
    surfaceContainer = NocheCapa2,
    surfaceContainerHigh = NocheCapa3,
    surfaceContainerHighest = NocheCapa4,
    outline = BordeNoche,
    outlineVariant = Color(0xFF3A3D5C),
    error = Terracota,
    onError = Color(0xFF2A1008),
    errorContainer = Color(0xFF55291E),
    onErrorContainer = Color(0xFFF7C3B3),
    inverseSurface = Crema,
    inverseOnSurface = Noche,
    inversePrimary = Marino,
)

/*
 * Modo claro: papel. Fondo crema, tinta para leer, marino para la marca. Terracota, trigo y
 * salvia quedan como relleno (chips, barras, bloques); cuando necesitan ser texto usan su
 * variante entintada, que sí pasa AA sobre crema.
 */
private val LightColors = lightColorScheme(
    primary = Marino,
    onPrimary = Crema,
    primaryContainer = Color(0xFFDFDEE8),
    onPrimaryContainer = Color(0xFF2B2D47),
    secondary = TerracotaTinta,
    onSecondary = Crema,
    secondaryContainer = Color(0xFFF9E4DD),
    onSecondaryContainer = Color(0xFF6A2A1A),
    tertiary = SalviaTinta,
    onTertiary = Crema,
    tertiaryContainer = Color(0xFFE2EEE7),
    onTertiaryContainer = Color(0xFF223F2F),
    background = Crema,
    onBackground = Tinta,
    surface = Crema,
    onSurface = Tinta,
    surfaceVariant = CremaHonda,
    onSurfaceVariant = PenumbraClara,
    surfaceContainerLowest = CremaAlta,
    surfaceContainerLow = Color(0xFFF8F5E7),
    surfaceContainer = Color(0xFFF0ECDA),
    surfaceContainerHigh = CremaHonda,
    surfaceContainerHighest = CremaTope,
    outline = BordeClaro,
    outlineVariant = CremaBorde,
    error = TerracotaTinta,
    onError = Crema,
    errorContainer = Color(0xFFF9E4DD),
    onErrorContainer = Color(0xFF6A2A1A),
    inverseSurface = Tinta,
    inverseOnSurface = Crema,
    inversePrimary = Trigo,
)

@Composable
fun WenagastosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // `dynamicColor` se conserva por compatibilidad con la firma anterior, pero Material You
    // reemplazaría la paleta de marca por la del fondo de pantalla; sólo se aplica si se pide.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    val paleta = if (darkTheme) WenaPaletteOscura else WenaPaletteClara

    CompositionLocalProvider(LocalWenaPalette provides paleta) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WenagastosTypography,
            shapes = WenagastosShapes,
            content = content,
        )
    }
}

