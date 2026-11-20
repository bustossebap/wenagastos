package cl.wenasea.wenagastos.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import cl.wenasea.wenagastos.WenagastosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Escucha las notificaciones del teléfono y guarda los movimientos de las apps del banco.
 *
 * Solo mira paquetes listados en [BankSource]: el resto se descarta sin leerlo.
 * El parseo corre fuera del hilo principal, en un [CoroutineScope] propio del servicio.
 */
class WenagastosNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener conectado")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener desconectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (BankSource.of(packageName) == null) return

        val extras = sbn.notification?.extras ?: return
        val raw = RawNotification(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" · "),
            postedAt = sbn.postTime,
            key = sbn.key.orEmpty(),
        )

        Log.i(TAG, "recibida pkg=$packageName title=${raw.title} text=${raw.text} big=${raw.bigText}")

        val container = (application as? WenagastosApp)?.container ?: return
        scope.launch {
            try {
                val outcome = container.capture.handle(raw)
                Log.i(TAG, "resultado=$outcome")
            } catch (t: Throwable) {
                Log.e(TAG, "No pude procesar la notificación", t)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WenagastosCapture"
    }
}
