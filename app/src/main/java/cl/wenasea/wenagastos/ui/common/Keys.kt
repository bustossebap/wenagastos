package cl.wenasea.wenagastos.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * Interruptor-tecla: la pista es un bloque de 44 × 26 dp con radio de 3 dp —no la píldora de
 * Material— y el pulgar es una tecla de 20 dp. Apagado es papel con borde; encendido es `primary`
 * con el pulgar en `onPrimary`, el mismo par que el botón.
 *
 * Se dibuja a mano, pero la semántica no: `Modifier.toggleable` con `Role.Switch` deja que un
 * lector de pantalla anuncie «activado/desactivado», que es la razón por la que la etapa 5 no lo
 * había tocado. El toque mide 48 dp aunque el dibujo mida 26.
 *
 * Los `Checkbox` se quedan como los trae Material: ya son un cuadrado de 2 dp de radio con
 * `primary` encendido, o sea que no hay píldora que corregir.
 */
@Composable
fun WenaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val edge = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val thumb = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha = if (enabled) 1f else 0.45f
    val offset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        label = "pulgar",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(track.copy(alpha = alpha))
                .border(1.dp, edge.copy(alpha = alpha), RoundedCornerShape(3.dp))
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .size(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(thumb.copy(alpha = alpha)),
            )
        }
    }
}
