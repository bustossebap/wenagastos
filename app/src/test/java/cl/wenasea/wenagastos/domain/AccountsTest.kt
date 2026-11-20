package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Institution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los números de una cuenta se guardan separados por coma en un solo campo: tarjeta en las
 * compras y número de cuenta en las transferencias. Estas pruebas fijan ese formato, porque
 * de él depende el cruce de `findByLast4` en el motor de captura.
 */
class AccountsTest {

    private fun account(last4: String?) = AccountEntity(
        name = "Santander Cta. Cte.",
        institution = Institution.SANTANDER,
        kind = AccountKind.CHECKING,
        colorHex = "#EC0000",
        last4 = last4,
    )

    @Test
    fun `un numero suelto se lee como lista de uno`() {
        assertEquals(listOf("5678"), account("5678").numbers())
    }

    @Test
    fun `varios numeros vuelven en el orden guardado`() {
        assertEquals(listOf("5678", "1234"), account("5678,1234").numbers())
    }

    @Test
    fun `se descartan vacios, cortos, largos y repetidos`() {
        assertEquals(listOf("1234"), account(" 1234 ,  ,12,12345,1234").numbers())
    }

    @Test
    fun `sin numeros la lista queda vacia`() {
        assertEquals(emptyList<String>(), account(null).numbers())
        assertEquals(emptyList<String>(), account("").numbers())
    }

    @Test
    fun `normalizar deja solo los cuatro digitos de cada entrada`() {
        assertEquals("1234,5678", normalizeNumbers(listOf("1234", "1234", "5678")))
        assertEquals("1234,5678", normalizeNumbers(listOf("12 34", " 5678 ")))
    }

    @Test
    fun `normalizar sin numeros validos borra el campo`() {
        assertNull(normalizeNumbers(emptyList()))
        assertNull(normalizeNumbers(listOf("12", "abc")))
    }
}
