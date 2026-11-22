package cl.wenasea.wenagastos.ui.capture

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.wenasea.wenagastos.capture.AppSettings
import cl.wenasea.wenagastos.capture.SourceRow
import cl.wenasea.wenagastos.capture.SourceState
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureOutcome
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.ui.common.EmptyState
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.common.VmFactories

/** Muestra lo que llegó por notificación y qué se hizo con eso. */
@Composable
fun CaptureDiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val viewModel: CaptureDiagnosticsViewModel =
        viewModel(factory = VmFactories.captureDiagnostics(container))
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()

    // El permiso de notificaciones de un banco se cambia en Ajustes del sistema: al volver, la
    // lista de apps se relee sola y la fila deja de decir "Notificaciones apagadas".
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("‹ Ajustes", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clear) {
                        Text("Limpiar", fontSize = 13.sp)
                    }
                }
                Text(
                    text = "Captura de notificaciones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Solo se guardan los últimos 200 avisos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sources.isNotEmpty()) {
            item { SourcesCard(sources) }
        }

        if (entries.isEmpty()) {
            item {
                EmptyState(
                    title = "Todavía no hay avisos",
                    hint = "Activa el acceso a notificaciones y espera un aviso del banco. " +
                        "Aquí verás el texto crudo y qué se hizo con él.",
                )
            }
        }

        items(entries, key = { it.id }) { entry ->
            CaptureLogCard(entry)
        }
    }
}

/**
 * Estado real de las apps de los bancos en este teléfono. Es lo primero que hay que mirar cuando
 * un movimiento no aparece: el retiro de Falabella se perdió porque la app del banco estaba
 * instalada como `cl.android` y la de la tienda estaba deshabilitada, y nada de eso se veía.
 */
@Composable
private fun SourcesCard(sources: List<SourceRow>) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Apps de los bancos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Si un aviso no llega, la causa casi siempre está en esta lista: la app no " +
                    "está instalada, está deshabilitada o tiene las notificaciones apagadas. " +
                    "MACH no tiene app: sus avisos llegan por correo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            sources.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.source.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = row.source.packages.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = row.state.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = row.state.color(),
                        )
                        row.installedPackage?.let { packageName ->
                            TextButton(
                                onClick = { openAppSettings(context, packageName) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("Ajustes", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceState.color(): Color = when (this) {
    SourceState.READY -> MaterialTheme.colorScheme.primary
    // No instalada no es un problema: quizá ese banco no se usa. Apagada sí lo es.
    SourceState.NOT_INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant
    SourceState.DISABLED, SourceState.MUTED -> MaterialTheme.colorScheme.error
}

/** La pantalla de notificaciones de la app; si el teléfono no la tiene, la ficha de la app. */
private fun openAppSettings(context: Context, packageName: String) {
    try {
        context.startActivity(AppSettings.notifications(packageName))
    } catch (error: ActivityNotFoundException) {
        context.startActivity(AppSettings.details(packageName))
    }
}

@Composable
private fun CaptureLogCard(entry: CaptureLogEntity) {
    val outcomeColor = when (entry.outcome) {
        CaptureOutcome.SAVED -> MaterialTheme.colorScheme.primary
        CaptureOutcome.PENDING, CaptureOutcome.MERGED -> MaterialTheme.colorScheme.tertiary
        CaptureOutcome.DUPLICATE -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.outcome.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = outcomeColor,
                )
                Text(
                    text = entry.sourceLabel ?: entry.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Dates.timeLabel(entry.postedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.title.isNotBlank()) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (entry.text.isNotBlank()) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            entry.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = outcomeColor,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
