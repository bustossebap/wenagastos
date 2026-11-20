package cl.wenasea.wenagastos.ui.lock

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Pantalla que tapa la app mientras está bloqueada. No muestra ningún dato: ni el mes, ni los
 * totales. Se reemplaza por la app entera, así que no hay forma de llegar a otra pestaña sin
 * desbloquear.
 */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val canAuthenticate = remember(context) { UnlockPrompt.isDeviceSecure(context) }
    var message by remember { mutableStateOf<String?>(null) }

    // En Android 10 y anteriores el PIN se pide en la pantalla de bloqueo del sistema.
    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> if (result.resultCode == Activity.RESULT_OK) onUnlock() }

    // Si el teléfono no tiene PIN ni huella no hay nada que pedir: se deja pasar y se avisa.
    // Sin esto, quitar el bloqueo de pantalla dejaría la app encerrada para siempre.
    LaunchedEffect(canAuthenticate) {
        if (!canAuthenticate) onUnlock()
    }

    fun ask() {
        message = null
        if (!canAuthenticate) {
            message = "Este teléfono no tiene PIN ni huella: activa un bloqueo de pantalla " +
                "para que el de Wenagastos sirva de algo."
            return
        }
        val intent = UnlockPrompt.credentialIntent(context)
        if (intent != null) {
            credentialLauncher.launch(intent)
            return
        }
        val host = activity
        val asked = host != null && UnlockPrompt.biometric(host) { ok -> if (ok) onUnlock() }
        if (!asked) {
            // Sin forma de autenticar no se deja a nadie encerrado fuera de sus propios datos.
            onUnlock()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🔒", style = MaterialTheme.typography.displayMedium)
            Text(
                text = "Wenagastos está bloqueado",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Tus movimientos siguen acá. Confirma que eres tú para verlos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { ask() },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Desbloquear")
            }
            message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
