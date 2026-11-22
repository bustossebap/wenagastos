package cl.wenasea.wenagastos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.R
import cl.wenasea.wenagastos.data.local.entity.Institution

/**
 * La marca de un banco como tecla: el logo se recorta con la misma forma y el mismo borde de 1 dp
 * que [CategoryAvatar], así que en una lista se lee como un sello y no como una calcomanía.
 *
 * Los logos van **empacados** en `res/drawable-nodpi/` y no se descargan nunca: la app no pide
 * `INTERNET`, y una pantalla de saldos tiene que abrir en el metro igual que en la casa. Efectivo
 * y «Otra» no tienen marca, así que siguen con el emoji de [Institution].
 */
@Composable
fun BankLogo(
    institution: Institution,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Int = 28,
) {
    val logo = institution.logoRes()
    if (logo == null) {
        CategoryAvatar(
            emoji = institution.emoji,
            color = colorHex.asColor(),
            modifier = modifier,
            size = size,
        )
        return
    }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = institution.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * El dibujo de cada institución. `null` es «no hay logo»: quien llama cae al emoji.
 *
 * Los tres bancos se dibujan a 144 px, que cubre una tecla de 34 dp en una pantalla de 3×. El de
 * MercadoPago es el ícono de su app —el favicon del sitio solo venía a 64 px y se veía blando—;
 * Santander y Falabella salen de sus `apple-touch-icon`.
 */
fun Institution.logoRes(): Int? = when (this) {
    Institution.SANTANDER -> R.drawable.ic_banco_santander
    Institution.FALABELLA -> R.drawable.ic_banco_falabella
    Institution.MERCADOPAGO -> R.drawable.ic_banco_mercadopago
    Institution.CASH, Institution.OTHER -> null
}
