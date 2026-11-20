package cl.wenasea.wenagastos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Ink900,
    primaryContainer = Color(0xFF00453E),
    onPrimaryContainer = Color(0xFFB4EFE6),
    secondary = TealGrey80,
    onSecondary = Ink900,
    secondaryContainer = Color(0xFF2A3D3A),
    onSecondaryContainer = Color(0xFFD5E6E2),
    tertiary = Amber80,
    onTertiary = Ink900,
    tertiaryContainer = Color(0xFF4A3A00),
    onTertiaryContainer = Color(0xFFFFE2A6),
    background = Ink900,
    onBackground = Mist200,
    surface = Ink900,
    onSurface = Mist200,
    surfaceVariant = Ink800,
    onSurfaceVariant = Color(0xFFB3C7C3),
    surfaceContainerLowest = Color(0xFF060F11),
    surfaceContainerLow = Ink850,
    surfaceContainer = Ink850,
    surfaceContainerHigh = Ink800,
    surfaceContainerHighest = Color(0xFF223437),
    outline = Color(0xFF7C918D),
    error = ColorError,
    onError = Color(0xFF601410),
)

private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EFE6),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = TealGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8E3),
    onSecondaryContainer = Color(0xFF0B1F1C),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8B8),
    onTertiaryContainer = Color(0xFF241A00),
    background = Mist100,
    onBackground = Color(0xFF141C1B),
    surface = Mist100,
    onSurface = Color(0xFF141C1B),
    surfaceVariant = Mist200,
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F7F5),
    surfaceContainer = Mist200,
    surfaceContainerHigh = Color(0xFFDCEAE7),
    surfaceContainerHighest = Color(0xFFD2E2DE),
    outline = Color(0xFF6F7977),
    error = Negativo,
    onError = Color.White,
)

@Composable
fun WenagastosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WenagastosTypography,
        content = content,
    )
}
