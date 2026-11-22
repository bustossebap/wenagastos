package cl.wenasea.wenagastos.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Recordatorio de respaldo en el inicio.
 *
 * La base va cifrada con la llave del Keystore, que no sale del teléfono: el `.wena` es el único
 * archivo que se puede abrir en otro aparato. Sin respaldo reciente, un robo o un "borrar datos"
 * se llevan todo el historial.
 *
 * Se relee al volver a la pantalla (ON_RESUME) para que la tarjeta desaparezca apenas se crea el
 * respaldo en Ajustes, sin reiniciar la app.
 *
 * La tecla del canto va en salvia —abono, plata que entra— y no en `tertiaryContainer`: el verde
 * pálido teñía la tarjeta entera y no era de la paleta.
 */
@Composable
fun BackupDueCard(
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stamp = LocalContainer.current.backupStamp
    var text by remember { mutableStateOf(stamp.reminder()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) text = stamp.reminder()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val body = text ?: return

    WenaNotice(
        accent = MaterialTheme.colorScheme.tertiary,
        title = "Respaldo pendiente",
        body = body,
        modifier = modifier,
        footnote = "Ajustes → Datos → Crear respaldo",
        actionLabel = "Ir a Ajustes",
        onAction = onOpenSettings,
    )
}
