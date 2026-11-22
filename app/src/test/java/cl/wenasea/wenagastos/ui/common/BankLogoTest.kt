package cl.wenasea.wenagastos.ui.common

import cl.wenasea.wenagastos.data.local.entity.Institution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los logos de banco van empacados en `res/drawable-nodpi/`: la app no pide `INTERNET`, así que un
 * banco con marca **tiene** que resolverse a un dibujo y los que no tienen marca tienen que caer
 * al emoji. Si alguien borra un PNG, esta prueba no compila o falla acá y no en el teléfono.
 */
class BankLogoTest {

    @Test
    fun `los bancos con marca tienen su dibujo`() {
        assertNotNull(Institution.SANTANDER.logoRes())
        assertNotNull(Institution.FALABELLA.logoRes())
        assertNotNull(Institution.MERCADOPAGO.logoRes())
    }

    @Test
    fun `efectivo y otra siguen con emoji`() {
        assertNull(Institution.CASH.logoRes())
        assertNull(Institution.OTHER.logoRes())
    }

    @Test
    fun `cada banco dibuja una marca distinta`() {
        val logos = Institution.entries.mapNotNull { it.logoRes() }
        assertEquals(3, logos.size)
        assertEquals(3, logos.toSet().size)
    }
}
