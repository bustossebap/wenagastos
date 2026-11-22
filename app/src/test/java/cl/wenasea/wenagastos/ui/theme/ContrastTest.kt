package cl.wenasea.wenagastos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auditoría de contraste de la etapa 6, con la fórmula de WCAG 2.1 sobre los colores que la app
 * realmente pinta, no sobre una copia de la tabla de `docs/diseno.md`: si alguien toca un token,
 * este test lo delata.
 *
 * Los pares están separados por cómo se leen:
 * - **claro**: sobre papel (`Crema`) y sobre tarjeta (`CremaHonda`), que es el plano más oscuro
 *   donde va texto. Los acentos puros (terracota, trigo, salvia) no se prueban como texto porque
 *   la regla de la casa prohíbe usarlos así; lo que se prueba son sus variantes entintadas.
 * - **oscuro**: sobre la noche (`Noche`), sobre tarjeta (`NocheCapa2`) y sobre tarjeta alta
 *   (`NocheCapa4`), que es donde la auditoría encontró los dos defectos: el abono y el semáforo de
 *   presupuesto se leen dentro de tarjetas, y ahí la salvia y la terracota puras no alcanzaban AA.
 *
 * Un par que falle tiene que doler: el mensaje dice el rol, el ratio y el mínimo.
 */
class ContrastTest {

    @Test
    fun `el modo claro pasa AA en cada texto que dibuja`() {
        val paleta = WenaPaletteClara
        val papel = paleta.fondo
        val tarjeta = paleta.papelHondo
        val tope = CremaTope

        revisar(
            Par("texto sobre papel", paleta.texto, papel),
            Par("texto sobre tarjeta", paleta.texto, tarjeta),
            Par("texto sobre el plano más alto", paleta.texto, tope),
            Par("etiqueta secundaria sobre papel", PenumbraClara, papel),
            Par("etiqueta secundaria sobre tarjeta", PenumbraClara, tarjeta),
            Par("etiqueta secundaria sobre el plano más alto", PenumbraClara, tope),
            Par("marca (primary) sobre papel", paleta.marino, papel),
            Par("cargo sobre papel", paleta.terracotaTexto, papel),
            Par("cargo sobre tarjeta", paleta.terracotaTexto, tarjeta),
            Par("abono sobre papel", paleta.salviaTexto, papel),
            Par("abono sobre tarjeta", paleta.salviaTexto, tarjeta),
            Par("trigo de texto sobre tarjeta", paleta.trigoTexto, tarjeta),
            Par("presupuesto ok sobre tarjeta", paleta.presupuestoOk, tarjeta),
            Par("presupuesto cerca sobre tarjeta", paleta.presupuestoCerca, tarjeta),
            Par("presupuesto excedido sobre tarjeta", paleta.presupuestoExcedido, tarjeta),
            Par("héroe: texto sobre el bloque", paleta.heroTexto, paleta.heroFondo),
            Par("héroe: cifra sobre el bloque", paleta.heroCifra, paleta.heroFondo),
            // Teclas: el texto encendido va sobre `primary`, el apagado sobre el plano más alto.
            Par("tecla pisada", Crema, paleta.marino),
            Par("nota: barra de aviso sobre tarjeta", paleta.terracotaTexto, tarjeta, 3.0),
            Par("nota: barra de respaldo sobre tarjeta", paleta.salviaTexto, tarjeta, 3.0),
        )
    }

    @Test
    fun `el modo oscuro pasa AA en cada texto que dibuja`() {
        val paleta = WenaPaletteOscura
        val noche = paleta.fondo
        val tarjeta = paleta.papel
        val tarjetaAlta = NocheCapa4

        revisar(
            Par("texto sobre noche", paleta.texto, noche),
            Par("texto sobre tarjeta", paleta.texto, tarjeta),
            Par("texto sobre tarjeta alta", paleta.texto, tarjetaAlta),
            Par("etiqueta secundaria sobre noche", PenumbraNoche, noche),
            Par("etiqueta secundaria sobre tarjeta alta", PenumbraNoche, tarjetaAlta),
            Par("marca (primary) sobre tarjeta alta", Trigo, tarjetaAlta),
            Par("cargo sobre noche", paleta.cargo, noche),
            Par("cargo sobre tarjeta alta", paleta.cargo, tarjetaAlta),
            Par("abono sobre noche", paleta.abono, noche),
            Par("abono sobre tarjeta alta", paleta.abono, tarjetaAlta),
            Par("presupuesto ok sobre tarjeta alta", paleta.presupuestoOk, tarjetaAlta),
            Par("presupuesto cerca sobre tarjeta alta", paleta.presupuestoCerca, tarjetaAlta),
            Par("presupuesto excedido sobre tarjeta alta", paleta.presupuestoExcedido, tarjetaAlta),
            Par("héroe: texto sobre el bloque", paleta.heroTexto, paleta.heroFondo),
            Par("héroe: cifra sobre el bloque", paleta.heroCifra, paleta.heroFondo),
            Par("tecla pisada", Tinta, Trigo),
            Par("nota: barra de aviso sobre tarjeta alta", paleta.terracota, tarjetaAlta, 3.0),
            Par("nota: barra de respaldo sobre tarjeta alta", paleta.salvia, tarjetaAlta, 3.0),
        )
    }

    /**
     * El pulgar apagado del interruptor-tecla no es texto, pero tiene que verse: 3:1.
     */
    @Test
    fun `el interruptor apagado se distingue de su pista`() {
        revisar(
            Par("pulgar apagado claro", PenumbraClara, CremaTope, 3.0),
            Par("pulgar apagado oscuro", PenumbraNoche, NocheCapa4, 3.0),
        )
    }

    /**
     * Si esto falla, los demás ratios no significan nada: son los tres números del manual
     * (página 13) recalculados acá.
     */
    @Test
    fun `la formula reproduce los valores del manual`() {
        assertEquals(11.02, ratio(Tinta, Crema), 0.01)
        assertEquals(9.52, ratio(Marino, Crema), 0.01)
        assertEquals(5.57, ratio(Terracota, Noche), 0.01)
        assertEquals(4.26, ratio(Color(0xFFE58A70), NocheCapa4), 0.01)
    }

    private data class Par(
        val rol: String,
        val texto: Color,
        val fondo: Color,
        val minimo: Double = 4.5,
    )

    private fun revisar(vararg pares: Par) {
        val fallidos = pares.mapNotNull { par ->
            val ratio = ratio(par.texto, par.fondo)
            if (ratio + 0.005 >= par.minimo) {
                null
            } else {
                "${par.rol}: ${"%.2f".format(ratio)}:1 < %.2f:1".format(par.minimo)
            }
        }
        assertTrue(fallidos.joinToString("\n"), fallidos.isEmpty())
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
