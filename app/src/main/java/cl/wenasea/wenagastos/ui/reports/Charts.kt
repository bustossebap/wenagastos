package cl.wenasea.wenagastos.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.MonthPoint
import java.time.YearMonth
import kotlin.math.max

/**
 * Dona de gastos por categoría, dibujada a mano: no hace falta ninguna librería de gráficos.
 * [slices] viene como color + parte (0..1) en el mismo orden que la leyenda.
 */
@Composable
fun CategoryDonut(
    slices: List<Pair<Color, Float>>,
    centerValue: String,
    centerCaption: String,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(164.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = Stroke(stroke),
            )
            var start = -90f
            slices.filter { it.second > 0f }.forEach { (color, share) ->
                val sweep = share * 360f
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = centerCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Gastos (teal) e ingresos (ámbar) de los últimos meses. Tocar un mes lo abre en el reporte.
 * La barra más alta del período es la que define la escala.
 */
@Composable
fun MonthlyBars(
    points: List<MonthPoint>,
    selected: YearMonth,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expenseColor = MaterialTheme.colorScheme.primary
    val incomeColor = MaterialTheme.colorScheme.tertiary
    if (points.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            val slot = size.width / points.size
            val bar = slot * 0.24f
            val gap = slot * 0.06f
            val top = max(points.maxOf { it.expenses }, points.maxOf { it.income }).coerceAtLeast(1L)
            points.forEachIndexed { index, point ->
                val left = slot * index + slot / 2f - bar - gap / 2f
                drawBar(expenseColor, point.expenses, top, left, bar)
                drawBar(incomeColor, point.income, top, left + bar + gap, bar)
            }
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
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 10.dp else 0.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) expenseColor else Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * Una barra redondeada desde la base. Las cantidades chicas quedan con un mínimo visible para que
 * un gasto de $1.000 no desaparezca al lado de un mes de $900.000.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    color: Color,
    value: Long,
    max: Long,
    left: Float,
    width: Float,
) {
    if (value <= 0L) return
    val height = (value.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx())
        .coerceAtMost(size.height)
    val radius = CornerRadius(width / 2f, width / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, size.height - height),
        size = Size(width, height),
        cornerRadius = radius,
    )
}
