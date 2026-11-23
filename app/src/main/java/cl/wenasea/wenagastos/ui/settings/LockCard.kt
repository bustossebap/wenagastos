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
            // Sin el párrafo de cómo funciona, pero el interruptor no puede quedar apagado sin
            // decir por qué: cuando el teléfono no tiene credencial, se dice en una línea.
            if (!canProtect) {
                Text(
                    text = if (lock.enabled) {
                        "Este teléfono ya no tiene PIN ni huella: la app se abre sin preguntar."
                    } else {
                        "Este teléfono no tiene PIN ni huella, así que no hay nada que pedir."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lock.enabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
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
                    text = "Es una cortina para quien tome el teléfono desbloqueado; la base ya " +
                        "va cifrada.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
