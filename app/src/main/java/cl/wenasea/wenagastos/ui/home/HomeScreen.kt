package cl.wenasea.wenagastos.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ChecklistItem(val titulo: String, val detalle: String, val hecho: Boolean)

private val faseCero = listOf(
    ChecklistItem("JDK 21 + Gradle", "Toolchain instalado con Homebrew", true),
    ChecklistItem("Android SDK 36", "platform-tools, platform 36 y build-tools", true),
    ChecklistItem("Proyecto Kotlin + Compose", "cl.wenasea.wenagastos · minSdk 26", true),
    ChecklistItem("Instalado en el Galaxy A34", "adb install del APK debug", false),
)

private val proximosPasos = listOf(
    "Fase 1 — Cuentas (Santander, Falabella, MercadoPago), categorías y carga manual",
    "Fase 2 — Captura automática por notificaciones + pantalla de diagnóstico",
    "Fase 3 — Reglas y aprendizaje de comercios",
    "Fase 4 — Transferencias entre cuentas propias, reportes y presupuestos",
    "Fase 5 — Export CSV/XLSX, backup cifrado y bloqueo biométrico",
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Wenagastos",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "v$version · Fase 0 completada",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "Entorno listo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                faseCero.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(if (item.hecho) "✅" else "⬜️")
                        Spacer(Modifier.fillMaxWidth(0.03f))
                        Column {
                            Text(item.titulo, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = item.detalle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Siguiente",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            proximosPasos.forEachIndexed { index, paso ->
                if (index > 0) HorizontalDivider()
                Text(text = paso, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "100% local · sin nube · sin permiso de internet",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
