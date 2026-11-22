package cl.wenasea.wenagastos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Los acentos de marca no tienen hueco en [androidx.compose.material3.ColorScheme]: ahí
 * "primary" es un solo color por modo. Esta paleta paralela conserva los seis colores del
 * manual, sus variantes legibles y los dos colores de dinero (abono/cargo) resueltos por modo.
 *
 * Se lee con [Wena.paleta]; el tema la instala en [LocalWenaPalette].
 */
@Immutable
data class WenaPalette(
    val fondo: Color,
    val texto: Color,
    val papel: Color,
    val papelHondo: Color,
    val linea: Color,
    val marino: Color,
    val terracota: Color,
    val trigo: Color,
    val salvia: Color,
    val terracotaTexto: Color,
    val trigoTexto: Color,
    val salviaTexto: Color,
    val abono: Color,
    val cargo: Color,
    val esOscuro: Boolean,
    // El héroe de Inicio: bloque marino con la cifra en trigo. En claro es el Marino del manual
    // (crema encima 9,52); en oscuro sube un peldaño a MarinoAlto (crema 6,93, trigo 5,16) para
    // que no se confunda con las tarjetas.
    val heroFondo: Color,
    val heroTexto: Color,
    val heroCifra: Color,
    // Semáforo de presupuesto: verde / ámbar / rojo con los acentos del manual. No usa roles M3
    // porque `primary/tertiary` no significan "cerca del tope" y en oscuro daban verde.
    val presupuestoOk: Color,
    val presupuestoCerca: Color,
    val presupuestoExcedido: Color,
)

/**
 * Modo claro: papel. Los acentos puros quedan para relleno (chips, barras, bloques de color) y
 * el texto de acento usa la variante entintada, que sí pasa AA sobre crema.
 */
internal val WenaPaletteClara = WenaPalette(
    fondo = Crema,
    texto = Tinta,
    papel = CremaAlta,
    papelHondo = CremaHonda,
    linea = CremaBorde,
    marino = Marino,
    terracota = Terracota,
    trigo = Trigo,
    salvia = Salvia,
    terracotaTexto = TerracotaTinta,
    trigoTexto = TrigoTinta,
    salviaTexto = SalviaTinta,
    abono = SalviaTinta,
    cargo = TerracotaTinta,
    esOscuro = false,
    heroFondo = Marino,
    heroTexto = Crema,
    heroCifra = Trigo,
    presupuestoOk = SalviaTinta,
    presupuestoCerca = TrigoTinta,
    presupuestoExcedido = TerracotaTinta,
)

/**
 * Modo oscuro: noche. Aquí se invierte la regla del claro — sobre #1C1E30 los acentos puros
 * sí alcanzan AA (terracota 5.57:1, salvia 6.60:1, trigo 10.72:1), así que el texto de acento
 * puede usar el color del manual sin oscurecerlo.
 */
internal val WenaPaletteOscura = WenaPalette(
    fondo = Noche,
    texto = Crema,
    papel = NocheCapa2,
    papelHondo = NocheCapa3,
    linea = NocheCapa4,
    marino = Marino,
    terracota = Terracota,
    trigo = Trigo,
    salvia = Salvia,
    terracotaTexto = TerracotaNoche,
    trigoTexto = Trigo,
    salviaTexto = Salvia,
    abono = Salvia,
    cargo = TerracotaNoche,
    esOscuro = true,
    heroFondo = MarinoAlto,
    heroTexto = Crema,
    heroCifra = Trigo,
    presupuestoOk = Salvia,
    presupuestoCerca = Trigo,
    presupuestoExcedido = TerracotaNoche,
)

internal val LocalWenaPalette = staticCompositionLocalOf { WenaPaletteClara }

/** Punto de acceso a los acentos y colores de dinero: `Wena.paleta.abono`. */
object Wena {
    val paleta: WenaPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalWenaPalette.current
}
