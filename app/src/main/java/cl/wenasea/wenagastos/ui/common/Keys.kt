package cl.wenasea.wenagastos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/*
 * Teclas que se tocan: el botón y el chip del manual. Los dos son bloques casi rectos con radio de
 * 5 dp —la misma tecla que la flecha del mes— y sin sombra: el plano se separa con color y borde.
 *
 * Material trae la píldora (`Button`) y el chip con relleno de `secondaryContainer`, que en este
 * tema es un rosado que no está en la paleta. Estas dos piezas los reemplazan para que tocar algo
 * se vea como pisar una tecla.
 */

/**
 * Botón-tecla. Por defecto usa `primary`/`onPrimary`, el par que el manual sanciona en cada modo
 * (crema sobre marino de día, tinta sobre trigo de noche).
 *
 * [containerColor] y [contentColor] existen para los bloques de color: sobre el marino del héroe
 * o de la portada, el botón va en trigo con tinta encima, porque marino sobre marino no se ve.
 */
@Composable
fun WenaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Chip-tecla: borde fino cuando está apagado, bloque de marca cuando está elegido. Se usa en las
 * filas de opciones (sentido del movimiento, cuenta, categoría, institución) donde antes había un
 * `FilterChip` rosado.
 */
@Composable
fun WenaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(5.dp)
    val alpha = if (enabled) 1f else 0.45f
    val fondo = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    } else {
        Color.Transparent
    }
    val borde = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(fondo)
            .border(1.dp, borde, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}
