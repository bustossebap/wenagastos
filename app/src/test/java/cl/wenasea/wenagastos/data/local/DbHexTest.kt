package cl.wenasea.wenagastos.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * La contraseña de SQLCipher son los 32 bytes de la llave escritos en hexadecimal: si esa
 * conversión cambiara entre arranques, la base dejaría de abrir. Por eso se prueba el ida y vuelta.
 */
class DbHexTest {

    @Test
    fun `ida y vuelta de los 32 bytes de la llave`() {
        val raw = ByteArray(32) { (it * 5).toByte() }

        val text = DbHex.encode(raw)

        assertEquals(64, text.length)
        assertArrayEquals(raw, DbHex.decode(text))
    }

    @Test
    fun `siempre en minusculas y con dos digitos por byte`() {
        val text = DbHex.encode(byteArrayOf(0x00, 0x0f, 0xab.toByte(), 0xff.toByte()))

        assertEquals("000fabff", text)
    }

    @Test
    fun `los bytes altos no se vuelven negativos al volver`() {
        val raw = byteArrayOf(0x80.toByte(), 0xc3.toByte(), 0x10)

        assertArrayEquals(raw, DbHex.decode(DbHex.encode(raw)))
    }

    @Test
    fun `un hexadecimal roto se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) { DbHex.decode("abc") }
        assertThrows(IllegalArgumentException::class.java) { DbHex.decode("zz") }
    }
}
