package cl.wenasea.wenagastos.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El respaldo es JSON escrito y leído a mano: si el formato se rompe, se rompe la restauración.
 * Estas pruebas fijan los casos que aparecen en datos reales: acentos, comillas en el nombre del
 * comercio, textos con salto de línea y archivos cortados por una descarga a medias.
 */
class JsonTest {

    @Test
    fun `un objeto vuelve igual, con acentos y comillas`() {
        val original = linkedMapOf<String, Any?>(
            "nombre" to "Súper \"Don Pepe\"",
            "nulo" to null,
            "activo" to true,
            "monto" to 12_000L,
        )

        assertEquals(original, Json.read(Json.write(original)))
    }

    @Test
    fun `los enteros no se escriben con decimales`() {
        assertEquals("[12000,0.5]", Json.write(listOf(12_000L, 0.5)))
    }

    @Test
    fun `saltos de linea y tabulaciones sobreviven`() {
        val texto = "linea 1\nlinea 2\ttab\rfin"

        assertEquals(texto, Json.read(Json.write(texto)))
    }

    @Test
    fun `lee escapes unicode`() {
        assertEquals("ñandú", Json.read("\"\\u00f1and\\u00fa\""))
    }

    @Test
    fun `arreglo y objeto vacios se leen`() {
        assertEquals(emptyList<Any?>(), Json.read(" [ ] "))
        assertEquals(emptyMap<String, Any?>(), Json.read("{ }"))
    }

    @Test
    fun `un valor suelto tambien se lee`() {
        assertEquals("hola", Json.read("\"hola\""))
        assertNull(Json.read("null"))
        assertEquals(42L, Json.read("42"))
        assertEquals(1.5, Json.read("1.5"))
        assertEquals(false, Json.read("false"))
    }

    @Test
    fun `un json cortado falla con un mensaje claro`() {
        val error = assertThrows(Json.JsonFormatException::class.java) { Json.read("{\"a\":1") }

        assertTrue(error.message.orEmpty().contains("JSON inválido"))
    }

    @Test
    fun `el texto que sobra despues del json falla`() {
        assertThrows(Json.JsonFormatException::class.java) { Json.read("{} {}") }
    }
}
