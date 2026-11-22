package cl.wenasea.wenagastos.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.wenasea.wenagastos.capture.NotificationAccess

/**
 * Aviso en el inicio cuando el permiso de notificaciones está apagado: sin él la app
 * no ve ninguna compra y todo lo que se registre será a mano.
 *
 * El permiso se activa en Ajustes del sistema, así que se relee al volver a la pantalla
 * (ON_RESUME) y la tarjeta desaparece sin reiniciar la app.
 *
 * Va en papel con la tecla de terracota al canto, no en `errorContainer`: el rosado teñía la
 * tarjeta entera y no era de la paleta.
 */
@Composable
fun CaptureOffCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (enabled) return

    WenaNotice(
        accent = MaterialTheme.colorScheme.error,
        title = "La captura está apagada",
        body = "Los avisos de tus bancos no se están leyendo. Si das acceso a las " +
            "notificaciones, las compras se anotan solas.",
        modifier = modifier,
        actionLabel = "Activar",
        onAction = { context.startActivity(NotificationAccess.settingsIntent()) },
    )
}
