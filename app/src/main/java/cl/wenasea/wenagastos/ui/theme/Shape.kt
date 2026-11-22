package cl.wenasea.wenagastos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * El manual construye con bloques casi rectos: rectángulos de papel, reglas finas y bordes
 * duros. Nada de píldoras ni esquinas de 28 dp. La escala queda corta y contenida.
 */
val WenagastosShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(5.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
