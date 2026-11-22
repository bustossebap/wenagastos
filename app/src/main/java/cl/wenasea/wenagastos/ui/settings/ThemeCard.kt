package cl.wenasea.wenagastos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.ThemeMode
import cl.wenasea.wenagastos.data.local.Prefs
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.WenaChip
import cl.wenasea.wenagastos.ui.common.findActivity

/** Las tres opciones, en el orden en que se leen: primero la que no hay que pensar. */
private val themeOptions = listOf(
    ThemeMode.SYSTEM to "Automático",
    ThemeMode.LIGHT to "Claro",
    ThemeMode.DARK to "Oscuro",
)

/**
 * Modo de color de la app. El manual no trae modo oscuro —se deduce de la familia del marino—,
 * pero quién lo elige es quien usa el teléfono: automático, siempre claro o siempre oscuro.
 *
 * Acá no se le pasa `darkTheme` a Compose: la preferencia se aplica en `attachBaseContext`
 * ([cl.wenasea.wenagastos.AppAppearance]), o sea antes de que la actividad infle nada, y por eso
 * también deciden bien las capas de arranque y los íconos de la barra de estado. El precio es que
 * hay que volver a crear la actividad para que el cambio valga.
 */
@Composable
internal fun ThemeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ThemeMode.fromKey(Prefs.themeKey(context))) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "Tema")
            Text(
                text = "De día el papel es crema y la tinta es el texto; de noche el papel es " +
                    "marino y las cifras se leen en crema. Automático sigue al teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                themeOptions.forEach { (option, label) ->
                    WenaChip(
                        text = label,
                        selected = option == mode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (option != mode) {
                                Prefs.setThemeKey(context, option.key)
                                mode = option
                                context.findActivity()?.recreate()
                            }
                        },
                    )
                }
            }
            Text(
                text = "El cambio se aplica en el acto: la pantalla se vuelve a dibujar una vez.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
