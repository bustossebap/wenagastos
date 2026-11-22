package cl.wenasea.wenagastos.ui.nav

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El enlace entre Inicio y Movimientos: la ruta lleva el mes que se estaba mirando, y un mes
 * ilegible no puede tumbar la pantalla. Cuando no hay mes —el tab— el ViewModel abre en el de hoy.
 */
class RoutesTest {

    @Test
    fun `sin mes la ruta es la del tab`() {
        assertEquals("movements", Routes.movements())
    }

    @Test
    fun `con mes la ruta lleva el mes en formato ISO`() {
        assertEquals("movements?month=2026-10", Routes.movements(YearMonth.of(2026, 10)))
    }

    @Test
    fun `el mes de la ruta se lee de vuelta`() {
        val month = YearMonth.of(2026, 10)
        assertEquals(month, Routes.monthFrom("2026-10"))
    }

    @Test
    fun `sin argumento no hay mes`() {
        assertNull(Routes.monthFrom(null))
    }

    @Test
    fun `un mes imposible o basura se ignora`() {
        assertNull(Routes.monthFrom("2026-13"))
        assertNull(Routes.monthFrom("octubre"))
        assertNull(Routes.monthFrom(""))
    }

    @Test
    fun `el editor se arma con el id`() {
        assertEquals("editor?txId=42", Routes.editor(42L))
        assertEquals("editor?txId=-1", Routes.editor(-1L))
    }
}
