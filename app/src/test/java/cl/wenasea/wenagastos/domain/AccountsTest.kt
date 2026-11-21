package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Institution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Una cuenta tiene un número de cuenta (el de las transferencias) y los de sus tarjetas (los de
 * las compras). Estas pruebas fijan ese reparto, porque de él depende el cruce `findByLast4` del
 * motor de captura: la tarjeta de débito no es otra cuenta, es un número más de la misma.
 */
class AccountsTest {

    private fun account(
        accountLast4: String? = null,
        cardLast4: String? = null,
        kind: AccountKind = AccountKind.CHECKING,
    ) = AccountEntity(
        name = "Santander Cta. Cte.",
        institution = Institution.SANTANDER,
        kind = kind,
        colorHex = "#EC0000",
        accountLast4 = accountLast4,
        cardLast4 = cardLast4,
    )

    @Test
    fun `la cuenta y sus tarjetas vuelven en una sola lista`() {
        assertEquals(listOf("1483", "4321"), account("1483", "4321").numbers())
    }

    @Test
    fun `sin numero de cuenta quedan solo las tarjetas`() {
        val card = account(cardLast4 = "4321")
        assertEquals(listOf("4321"), card.numbers())
        assertEquals(listOf("4321"), card.cardNumbers())
        assertNull(card.accountNumber())
    }

    @Test
    fun `el numero de cuenta se lee aparte de las tarjetas`() {
        val checking = account("1483", "4321,5678")
        assertEquals("1483", checking.accountNumber())
        assertEquals(listOf("4321", "5678"), checking.cardNumbers())
        assertEquals(listOf("1483", "4321", "5678"), checking.numbers())
    }

    @Test
    fun `se descartan vacios, cortos, largos y repetidos`() {
        assertEquals(listOf("1234"), account(accountLast4 = " 1234 ").numbers())
        assertEquals(listOf("1234"), account(cardLast4 = " 1234 ,  ,12,12345,1234").numbers())
        assertEquals(listOf("1234"), account("1234", "1234").numbers())
    }

    @Test
    fun `sin numeros la lista queda vacia`() {
        assertEquals(emptyList<String>(), account().numbers())
        assertEquals(emptyList<String>(), account("", "").numbers())
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

    @Test
    fun `el numero de la cuenta exige exactamente cuatro digitos`() {
        assertEquals("1483", normalizeNumber("cuenta 1483"))
        assertNull(normalizeNumber("148"))
        assertNull(normalizeNumber(null))
    }

    @Test
    fun `una tarjeta conserva su numero al guardar sin cambios`() {
        val (account, cards) = splitNumbers(AccountKind.CREDIT, "6614", emptyList())

        assertNull(account)
        assertEquals("6614", cards)
    }

    @Test
    fun `una cuenta corriente separa el numero de cuenta de sus tarjetas`() {
        val (account, cards) = splitNumbers(AccountKind.CHECKING, "1483", listOf("4321", "5678"))

        assertEquals("1483", account)
        assertEquals("4321,5678", cards)
    }

    @Test
    fun `cambiar una corriente a tarjeta no pierde ningun numero`() {
        val (account, cards) = splitNumbers(AccountKind.CREDIT, "1483", listOf("4321"))

        assertNull(account)
        assertEquals("1483,4321", cards)
    }
}
