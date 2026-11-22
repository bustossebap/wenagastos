package cl.wenasea.wenagastos.importexport

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El CSV lo abre Excel, que en configuración chilena espera coma y BOM. Las comillas son lo que
 * evita que un nombre de comercio con coma corra las columnas, así que se prueban una por una.
 *
 * Al revés, cuando el archivo lo escribe el banco: la cartola llega separada por `;`, con montos
 * que traen puntos de mil y con el nombre del comercio que puede traer comas. El lector tiene que
 * sacar las columnas de ahí sin que nadie le diga cuál es el separador.
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

    @Test
    fun `una cartola del excel chileno se lee con punto y coma`() {
        val text = "FECHA;DESCRIPCION;CARGO;ABONO;SALDO\r\n" +
            "18/11/2026;COMPRA LIDER EXPRESS;12.345;;1.234.567\r\n"

        val sheet = Csv.split(text)

        assertEquals(';', sheet.delimiter)
        assertEquals(2, sheet.rows.size)
        assertEquals(
            listOf("18/11/2026", "COMPRA LIDER EXPRESS", "12.345", "", "1.234.567"),
            sheet.rows[1],
        )
    }

    @Test
    fun `las comas de la descripcion no confunden al separador`() {
        val text = "FECHA;DESCRIPCION;MONTO\n" +
            "18/11/2026;PAGO, COMISION Y OTROS;1000\n" +
            "19/11/2026;TRANSFERENCIA, JUAN;2000\n"

        assertEquals(';', Csv.detect(text))
    }

    @Test
    fun `lo que se escribe se puede volver a leer`() {
        val headers = listOf("comercio", "notas")
        val rows = listOf(
            listOf("Lider, Express", "primera\nsegunda"),
            listOf("Dijo \"hola\"", ""),
        )

        assertEquals(listOf(headers) + rows, Csv.read(Csv.write(headers, rows)))
    }

    @Test
    fun `un campo entre comillas conserva separador y salto de linea`() {
        val rows = Csv.read("desc;nota\n\"Lider; Express\";\"linea 1\nlinea 2\"\n")

        assertEquals(2, rows.size)
        assertEquals(listOf("Lider; Express", "linea 1\nlinea 2"), rows[1])
    }

    @Test
    fun `las comillas duplicadas son una comilla`() {
        assertEquals("Dijo \"hola\"", Csv.read("comercio\n\"Dijo \"\"hola\"\"\"\n")[1][0])
    }

    @Test
    fun `el salto de linea final no es una fila`() {
        assertEquals(2, Csv.read("a,b\r\n1,2\r\n").size)
        assertEquals(2, Csv.read("a,b\n1,2").size)
        assertEquals(2, Csv.read("a,b\r1,2\r").size)
    }

    @Test
    fun `las columnas vacias del final se conservan`() {
        val rows = Csv.read("a,b,c\n1,2,\n")

        assertEquals(listOf("1", "2", ""), rows[1])
    }

    @Test
    fun `el bom no se cuela en el primer campo`() {
        assertEquals("fecha", Csv.read(Csv.BOM + "fecha,monto\n")[0][0])
    }

    @Test
    fun `una tabulacion tambien separa`() {
        val sheet = Csv.split("fecha\tmonto\n18/11/2026\t12345\n")

        assertEquals('\t', sheet.delimiter)
        assertEquals(listOf("18/11/2026", "12345"), sheet.rows[1])
    }

    @Test
    fun `un texto vacio no tiene filas`() {
        assertEquals(0, Csv.read("").size)
        assertEquals(0, Csv.read(Csv.BOM).size)
    }
}
