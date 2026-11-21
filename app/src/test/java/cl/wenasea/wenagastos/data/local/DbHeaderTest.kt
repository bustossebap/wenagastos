package cl.wenasea.wenagastos.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cabecera decide si hay que cifrar la base, así que tiene que distinguir los dos casos sin
 * dudas: decir "cifrada" sobre un archivo de texto plano dejaría los datos ilegibles.
 */
class DbHeaderTest {

    @Test
    fun `una base de SQLite comun se reconoce como texto plano`() {
        assertTrue(DbHeader.isPlainText(plainHeader()))
    }

    @Test
    fun `la cabecera de SQLCipher no se confunde con texto plano`() {
        // La sal son 16 bytes aleatorios: acá se usan fijos, pero del mismo tipo.
        val salt = ByteArray(DbHeader.SIZE) { (it * 7 + 3).toByte() }

        assertFalse(DbHeader.isPlainText(salt))
    }

    @Test
    fun `un archivo mas corto que la cabecera no alcanza para decidir`() {
        assertFalse(DbHeader.isPlainText(ByteArray(DbHeader.SIZE - 1)))
        assertFalse(DbHeader.isPlainText(ByteArray(0)))
    }

    @Test
    fun `una cabecera que empieza igual pero cambia al final no es texto plano`() {
        val header = plainHeader()
        header[header.lastIndex] = 1

        assertFalse(DbHeader.isPlainText(header))
    }

    private fun plainHeader(): ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
}
