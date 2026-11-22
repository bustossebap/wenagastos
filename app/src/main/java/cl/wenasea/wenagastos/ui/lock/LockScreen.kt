package cl.wenasea.wenagastos.ui.lock

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cl.wenasea.wenagastos.ui.common.WenaButton
import cl.wenasea.wenagastos.ui.common.findActivity
import cl.wenasea.wenagastos.ui.theme.Tinta
import cl.wenasea.wenagastos.ui.theme.Wena

/**
 * Pantalla que tapa la app mientras está bloqueada. No muestra ningún dato: ni el mes, ni los
 * totales. Se reemplaza por la app entera, así que no hay forma de llegar a otra pestaña sin
 * desbloquear.
 *
 * Es la portada de la app: el bloque de marca a pantalla completa —marino con crema encima— y una
 * sola tecla en trigo con tinta, el par que el manual sanciona sobre relleno claro. Adentro no hay
 * nada que leer salvo el nombre, así que el color es lo único que puede hablar.
 */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paleta = Wena.paleta
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val canAuthenticate = remember(context) { UnlockPrompt.isDeviceSecure(context) }
    var message by remember { mutableStateOf<String?>(null) }

    // La portada llega hasta la barra de estado, que sigue pintada con el papel del tema: mientras
    // dure el bloqueo los íconos del sistema van en claro, y al salir vuelven a como estaban.
    val window = activity?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNav != null) controller.isAppearanceLightNavigationBars = lightNav
        }
    }

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
        color = paleta.heroFondo,
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
                color = paleta.heroTexto,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Tus movimientos siguen acá. Confirma que eres tú para verlos.",
                style = MaterialTheme.typography.bodyMedium,
                color = paleta.heroTexto.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            WenaButton(
                text = "Desbloquear",
                onClick = { ask() },
                // Trigo con tinta encima: el manual lo pide así sobre relleno claro, y el marino
                // del botón normal se perdería sobre el marino de la portada.
                containerColor = paleta.heroCifra,
                contentColor = Tinta,
                modifier = Modifier.padding(top = 24.dp),
            )
            message?.let { text ->
                // La nota va en papel: la terracota de error no alcanza AA sobre el marino.
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                )
            }
        }
    }
}
