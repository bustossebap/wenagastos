package cl.wenasea.wenagastos.ui.db

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import cl.wenasea.wenagastos.MainActivity

/**
 * Pantalla para cuando la base cifrada no se puede abrir: el teléfono perdió la llave del Keystore
 * o el archivo quedó dañado. Sin esto la app arrancaría en un ciclo de errores, así que se explica
 * qué pasó y se ofrece empezar de cero (y restaurar después el respaldo `.wena`).
 */
@Composable
fun DbBrokenScreen(
    problem: String,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }

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
            Text(text = "🗄️", style = MaterialTheme.typography.displayMedium)
            Text(
                text = "No puedo abrir tus datos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "La base va cifrada con una llave que vive en este teléfono. Si se perdió, " +
                    "la única forma de recuperar tus movimientos es un respaldo .wena: primero " +
                    "empieza de cero y después restáuralo en Ajustes → Datos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = { confirming = true },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Empezar de cero")
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Borrar los datos del teléfono?") },
            text = {
                Text(
                    "Se borra la base que no puedo abrir. Si tienes un respaldo .wena, " +
                        "podrás restaurarlo después. Esto no se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDiscard()
                        restart(context)
                    },
                ) {
                    Text("Borrar y reiniciar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/** Reinicia la app: el contenedor se arma otra vez, ya sin la base vieja. */
private fun restart(context: Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    Process.killProcess(Process.myPid())
}
