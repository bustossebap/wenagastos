package cl.wenasea.wenagastos.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.ui.theme.HeroAmount
import cl.wenasea.wenagastos.ui.theme.Wena

/**
 * Bloque protagonista: marino, cifra en VG5000 al trigo y borde superior rasgado, como las bandas
 * de la portada del manual. El rasgado se dibuja una sola vez por pantalla — aquí, en Inicio — y no
 * se repite en tarjetas normales.
 *
 * @param label el rótulo chico de arriba ("Gastos del mes").
 * @param amount el monto ya formateado con [cl.wenasea.wenagastos.domain.Money.format].
 * @param stats pares rótulo/valor que van en la línea de abajo; vacío = sin línea.
 */
@Composable
fun HeroBlock(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    stats: List<Pair<String, String>> = emptyList(),
) {
    val paleta = Wena.paleta
    val rasgado = 7.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(paleta.heroFondo),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = rasgado + 12.dp, bottom = 18.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = paleta.heroTexto.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = amount,
                style = HeroAmount,
                color = paleta.heroCifra,
                maxLines = 1,
                softWrap = false,
            )
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    stats.forEach { (statLabel, statValue) ->
                        HeroStat(
                            label = statLabel,
                            value = statValue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        TornTopEdge(
            color = paleta.fondo,
            height = rasgado,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val paleta = Wena.paleta
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = paleta.heroTexto.copy(alpha = 0.78f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = paleta.heroTexto,
        )
    }
}

/*
 * Perfil del rasgado, en fracciones de la altura del borde. Es fijo — no aleatorio — para que el
 * borde no cambie de forma entre recomposiciones ni entre pantallas. Los extremos quedan en la
 * mitad para que las esquinas no se coman el bloque con una diagonal larga.
 */
private val PerfilRasgado = floatArrayOf(
    0.55f, 0.12f, 0.62f, 0.34f, 0.06f, 0.48f, 0.20f, 0.68f,
    0.30f, 0.04f, 0.54f, 0.24f, 0.72f, 0.18f, 0.58f,
)

/**
 * Pinta el papel del fondo sobre la franja de arriba del bloque con un borde inferior irregular:
 * el bloque queda como una banda arrancada.
 */
@Composable
fun TornTopEdge(color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val pasos = PerfilRasgado.size - 1
        val ancho = size.width / pasos
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * PerfilRasgado[0])
            for (i in 0 until pasos) {
                val x0 = size.width - ancho * i
                val controlY = if (i % 2 == 0) size.height else 0f
                quadraticTo(
                    x1 = x0 - ancho / 2f,
                    y1 = controlY,
                    x2 = x0 - ancho,
                    y2 = size.height * PerfilRasgado[i + 1],
                )
            }
            close()
        }
        drawPath(path, color)
    }
}
