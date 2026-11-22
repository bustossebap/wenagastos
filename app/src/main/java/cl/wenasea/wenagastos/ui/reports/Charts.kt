package cl.wenasea.wenagastos.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.MonthPoint
import cl.wenasea.wenagastos.ui.theme.Wena
import java.time.YearMonth
import kotlin.math.max

/**
 * Barra-tecla apilada: cada categoría ocupa un tramo recto y entre tramos queda papel. Es la
 * lectura de partes del informe sin curvas — una dona con quince categorías es confeti, y el
 * manual no tiene curvas. [slices] viene como color + parte (0..1) en el mismo orden que la
 * leyenda, que es la lista ordenada de mayor a menor gasto.
 */
@Composable
fun CategoryStack(
    slices: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .background(track)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(1.dp)) {
            val gap = 2.dp.toPx()
            val minimo = 2.dp.toPx()
            val tramos = slices.filter { it.second > 0f }
            // Los huecos también ocupan ancho: se descuentan antes de repartir las partes, si no el
            // último tramo se sale de la barra y desaparece.
            val ancho = (size.width - gap * (tramos.size - 1).coerceAtLeast(0)).coerceAtLeast(0f)
            var left = 0f
            tramos.forEach { (color, share) ->
                val tramo = (ancho * share)
                    .coerceAtLeast(minimo)
                    .coerceAtMost(size.width - left)
                if (tramo <= 0f) return@forEach
                drawRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = Size(tramo, size.height),
                )
                left += tramo + gap
            }
        }
    }
}

/**
 * Gastos e ingresos de los últimos meses como teclas: barras rectas de altura distinta, separación
 * fina y una regla de base compartida, como el módulo gráfico del manual. Tocar un mes lo abre en
 * el reporte.
 *
 * Los colores son los del dinero (`cargo`/`abono`), los mismos que firman los montos en las filas:
 * el reporte no inventa una paleta propia. La barra más alta del período define la escala.
 */
@Composable
fun MonthlyBars(
    points: List<MonthPoint>,
    selected: YearMonth,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paleta = Wena.paleta
    val regla = MaterialTheme.colorScheme.outlineVariant
    if (points.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            val slot = size.width / points.size
            val bar = slot * 0.22f
            val gap = 3.dp.toPx()
            val linea = 1.dp.toPx()
            val pie = size.height - linea
            val escala = max(points.maxOf { it.expenses }, points.maxOf { it.income })
                .coerceAtLeast(1L)
            points.forEachIndexed { index, point ->
                val left = slot * index + slot / 2f - bar - gap / 2f
                drawKey(paleta.cargo, point.expenses, escala, left, bar, pie)
                drawKey(paleta.abono, point.income, escala, left + bar + gap, bar, pie)
            }
            drawRect(
                color = regla,
                topLeft = Offset(0f, pie),
                size = Size(size.width, linea),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                val isSelected = point.month == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(point.month) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = Dates.shortMonthLabel(point.month),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(3.dp))
                    // La tecla pisada del mes abierto: un rectángulo corto, no un pill.
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 14.dp else 0.dp)
                            .height(3.dp)
                            .background(if (isSelected) paleta.cargo else Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * Una tecla desde la regla de base: rectángulo recto, sin radio. Las cantidades chicas quedan con
 * un mínimo visible para que un gasto de $1.000 no desaparezca al lado de un mes de $900.000.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKey(
    color: Color,
    value: Long,
    max: Long,
    left: Float,
    width: Float,
    baseline: Float,
) {
    if (value <= 0L) return
    val height = (value.toFloat() / max * baseline)
        .coerceAtLeast(3.dp.toPx())
        .coerceAtMost(baseline)
    drawRect(
        color = color,
        topLeft = Offset(left, baseline - height),
        size = Size(width, height),
    )
}
