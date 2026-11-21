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
        // Lo que se publicó mientras el servicio estaba caído (un reinicio del teléfono, una
        // actualización de la app o Android matando el proceso) no llega por onNotificationPosted
        // y se perdería para siempre: se recupera de las notificaciones que siguen en la barra.
        val cutoff = System.currentTimeMillis() - RECENT_WINDOW
        val present = activeNotifications
        val relevant = present.orEmpty()
            .filter { it.packageName == MailSource.PACKAGE || BankSource.of(it.packageName) != null }
            .joinToString { "${it.packageName}@${it.postTime}" }
        Log.i(TAG, "activas=${present?.size ?: -1} relevantes=[$relevant]")
        present
            ?.filter { it.postTime >= cutoff }
            ?.forEach { sbn ->
                try {
                    handle(sbn)
                } catch (t: Throwable) {
                    Log.e(TAG, "No pude revisar una notificación ya presente", t)
                }
            }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener desconectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val isMail = packageName == MailSource.PACKAGE
        if (!isMail && BankSource.of(packageName) == null) return

        val extras = sbn.notification?.extras ?: return
        val raw = RawNotification(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" · "),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            postedAt = sbn.postTime,
            key = sbn.key.orEmpty(),
        )

        // Fase 1: el correo del banco ya se guarda como cualquier aviso; el filtro por remitente
        // vive dentro del motor (`MailSource`), antes de tocar la base.
        if (isMail) {
            val bank = MailSource.of(raw)
            if (bank == null) return
            Log.i(TAG, "correo banco=${bank.label} title=${raw.title} text=${raw.text}")
        } else {
            Log.i(TAG, "recibida pkg=$packageName title=${raw.title} text=${raw.text} big=${raw.bigText}")
        }

        val container = (application as? WenagastosApp)?.container ?: return
        // Sin base utilizable no se puede guardar nada; la pantalla principal ya avisa.
        if (container.dbProblem != null) return
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

        /**
         * Ventana para recuperar lo que quedó en la barra mientras el servicio no estaba
         * enlazado. Seis horas cubren un reinicio o una actualización largos, y a la vez evitan
         * registrar movimientos viejos que el usuario ya pudo haber anotado a mano.
         */
        const val RECENT_WINDOW = 6 * 60 * 60_000L
    }
}
