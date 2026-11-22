package cl.wenasea.wenagastos.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.ui.common.SectionHeader
import cl.wenasea.wenagastos.ui.common.WenaSwitch
import cl.wenasea.wenagastos.ui.lock.AppLockState
import cl.wenasea.wenagastos.ui.lock.UnlockPrompt

/**
 * Interruptor del bloqueo al abrir. Si el teléfono no tiene PIN ni huella el interruptor queda
 * apagado: pedir una credencial que no existe dejaría la app abierta igual, con una falsa
 * sensación de protección.
 */
@Composable
internal fun LockCard(
    lock: AppLockState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canProtect = UnlockPrompt.isDeviceSecure(context)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "Bloqueo al abrir")
            Text(
                text = when {
                    lock.enabled && !canProtect ->
                        "El bloqueo está activado, pero este teléfono ya no tiene PIN ni huella: " +
                            "mientras siga así la app se abre sin preguntar. Configura un bloqueo " +
                            "de pantalla en Ajustes del sistema."
                    canProtect ->
                        "Al abrir Wenagastos se pide la huella o el PIN del teléfono. Después de " +
                            "un minuto en segundo plano se vuelve a pedir."
                    else ->
                        "Este teléfono no tiene PIN, patrón ni huella. Configura un bloqueo de " +
                            "pantalla en Ajustes del sistema para poder activarlo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (lock.enabled) "Activado" else "Desactivado",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                WenaSwitch(
                    checked = lock.enabled,
                    onCheckedChange = { lock.setLockEnabled(it) },
                    enabled = canProtect || lock.enabled,
                )
            }
            if (lock.enabled) {
                Text(
                    text = "El bloqueo no cifra la base: es una cortina para quien tome el " +
                        "teléfono desbloqueado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
