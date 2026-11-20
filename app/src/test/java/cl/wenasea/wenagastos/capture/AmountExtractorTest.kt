package cl.wenasea.wenagastos.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountExtractorTest {

    @Test
    fun `lee el monto de una compra con simbolo`() {
        assertEquals(
            12_500L,
            AmountExtractor.find("Compraste $12.500 en LIDER EXPRESS con tu tarjeta"),
        )
    }

    @Test
    fun `lee el monto marcado con CLP`() {
        assertEquals(
            45_990L,
            AmountExtractor.find("Realizaste una compra por CLP 45.990 en PARIS"),
        )
    }

    @Test
    fun `lee el monto sin simbolo despues del verbo`() {
        assertEquals(3_500L, AmountExtractor.find("Pagaste 3.500 pesos en el Metro"))
    }

    @Test
    fun `lee el formato de Google Wallet en ingles`() {
        assertEquals(
            12_500L,
            AmountExtractor.find("You paid CLP 12,500.00 with Visa ••••4321"),
        )
    }

    @Test
    fun `ignora el saldo cuando hay un monto de compra`() {
        assertEquals(
            12_500L,
            AmountExtractor.find("Compraste $12.500 en LIDER. Saldo disponible: $85.000"),
        )
    }

    @Test
    fun `sin movimiento, el saldo no es un monto`() {
        assertNull(AmountExtractor.find("Tu saldo disponible es $150.000"))
        assertNull(AmountExtractor.find("Cupo disponible $500.000"))
    }

    @Test
    fun `descarta montos en otras monedas`() {
        assertNull(AmountExtractor.find("Compraste US$25,00 en AMAZON PRIME"))
        assertNull(AmountExtractor.find("Pago de €9,99 en SPOTIFY"))
        assertNull(AmountExtractor.find("Compraste UF 1,5 en lo de siempre"))
    }

    @Test
    fun `sin monto no hay nada que registrar`() {
        assertNull(AmountExtractor.find("Tu compra fue aprobada"))
        assertNull(AmountExtractor.find("Aprovecha 30% de descuento en tus compras"))
    }

    @Test
    fun `convierte formatos chilenos y anglosajones`() {
        assertEquals(12_500L, AmountExtractor.parseClp("12.500"))
        assertEquals(12_500L, AmountExtractor.parseClp("12.500,50"))
        assertEquals(12_500L, AmountExtractor.parseClp("12,500.00"))
        assertEquals(1_234_567L, AmountExtractor.parseClp("1.234.567"))
        assertEquals(1_234_567L, AmountExtractor.parseClp("1,234,567.89"))
        assertEquals(950L, AmountExtractor.parseClp("950"))
    }

    @Test
    fun `rechaza montos fuera de rango`() {
        assertNull(AmountExtractor.parseClp("0"))
        assertNull(AmountExtractor.parseClp("999.999.999"))
        assertNull(AmountExtractor.parseClp("abc"))
    }
}
