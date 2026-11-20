package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantCategoriesTest {

    @Test
    fun `los supermercados van a Supermercado`() {
        assertEquals("Supermercado", MerchantCategories.categoryFor("LIDER EXPRESS", Direction.DEBIT))
        assertEquals("Supermercado", MerchantCategories.categoryFor("JUMBO 123", Direction.DEBIT))
    }

    @Test
    fun `las apps de delivery no se confunden con Uber`() {
        assertEquals("Delivery", MerchantCategories.categoryFor("UBER EATS", Direction.DEBIT))
        assertEquals("Transporte", MerchantCategories.categoryFor("UBER TRIP", Direction.DEBIT))
    }

    @Test
    fun `bencina, suscripciones y salud`() {
        assertEquals("Bencina", MerchantCategories.categoryFor("COPEC 123", Direction.DEBIT))
        assertEquals("Suscripciones", MerchantCategories.categoryFor("NETFLIX.COM", Direction.DEBIT))
        assertEquals("Salud", MerchantCategories.categoryFor("FARMACIAS AHUMADA", Direction.DEBIT))
    }

    @Test
    fun `los ingresos usan sus propias reglas`() {
        assertEquals("Sueldo", MerchantCategories.categoryFor("PAGO SUELDO", Direction.CREDIT))
        assertEquals("Rendimientos", MerchantCategories.categoryFor("RENDIMIENTO CUENTA", Direction.CREDIT))
        assertNull(MerchantCategories.categoryFor("LIDER EXPRESS", Direction.CREDIT))
    }

    @Test
    fun `sin regla no se inventa una categoria`() {
        assertNull(MerchantCategories.categoryFor("BAR DONDE EL NANO", Direction.DEBIT))
    }

    @Test
    fun `una transferencia recibida de una persona va a Otros ingresos`() {
        assertEquals(
            "Otros ingresos",
            MerchantCategories.categoryFor(
                "MARIA SOTO",
                Direction.CREDIT,
                "Recibiste una transferencia de MARIA SOTO por \$45.000",
            ),
        )
        assertNull(
            MerchantCategories.categoryFor(
                "BAR DONDE EL NANO",
                Direction.DEBIT,
                "Compraste \$5.000 en BAR DONDE EL NANO",
            ),
        )
    }
}
