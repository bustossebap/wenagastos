package cl.wenasea.wenagastos.importexport

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El CSV lo abre Excel, que en configuración chilena espera coma y BOM. Las comillas son lo que
 * evita que un nombre de comercio con coma corra las columnas, así que se prueban una por una.
 */
class CsvTest {

    @Test
    fun `empieza con bom y termina cada fila en crlf`() {
        val csv = Csv.write(listOf("fecha", "monto"), listOf(listOf("2026-11-20", "12000")))

        assertEquals("\uFEFFfecha,monto\r\n2026-11-20,12000\r\n", csv)
    }

    @Test
    fun `un campo con coma va entre comillas`() {
        val csv = Csv.write(listOf("comercio"), listOf(listOf("Lider, Express")))

        assertEquals("\uFEFFcomercio\r\n\"Lider, Express\"\r\n", csv)
    }

    @Test
    fun `las comillas se duplican`() {
        val csv = Csv.write(listOf("comercio"), listOf(listOf("Dijo \"hola\"")))

        assertEquals("\uFEFFcomercio\r\n\"Dijo \"\"hola\"\"\"\r\n", csv)
    }

    @Test
    fun `un salto de linea en el campo queda entre comillas`() {
        val csv = Csv.write(listOf("notas"), listOf(listOf("primera\nsegunda")))

        assertEquals("\uFEFFnotas\r\n\"primera\nsegunda\"\r\n", csv)
    }

    @Test
    fun `una formula se neutra con apostrofo`() {
        val csv = Csv.write(listOf("comercio"), listOf(listOf("=1+1")))

        assertEquals("\uFEFFcomercio\r\n'=1+1\r\n", csv)
    }

    @Test
    fun `sin filas queda solo la cabecera`() {
        assertEquals("\uFEFFid,fecha\r\n", Csv.write(listOf("id", "fecha"), emptyList()))
    }
}
