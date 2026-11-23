package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.Direction
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La planilla de Excel no se lee como se ve: las fechas son números de días, los textos están
 * guardados una sola vez y citados por número, y las celdas vacías no existen. Estas pruebas arman
 * un .xlsx de verdad —un ZIP con los mismos XML que exporta el banco— y comprueban que de ahí salga
 * la misma cartola que de un CSV.
 *
 * Los datos son inventados a propósito: la prueba se queda en el repo y la cartola de una persona
 * no.
 */
class XlsxTest {

    @Test
    fun `la planilla de la tarjeta se lee como una cartola`() {
        val sheet = Xlsx.sheet(
            book(
                sheet = rows(
                    row(
                        1,
                        shared("A1", 0), shared("B1", 1), shared("C1", 2),
                        shared("D1", 3), shared("E1", 4), shared("F1", 5),
                    ),
                    row(
                        2,
                        number("A2", "46330", DATE), shared("B2", 6), shared("C2", 7),
                        number("D2", "9079", MONEY), number("E2", "0"),
                        number("F2", "9079", MONEY),
                    ),
                    row(
                        3,
                        number("A3", "46329", DATE), shared("B3", 8), shared("C3", 7),
                        number("D3", "208960", MONEY), number("E3", "0"),
                        number("F3", "-208960", MONEY),
                    ),
                ),
                strings = listOf(
                    "FECHA",
                    "DESCRIPCION",
                    "TITULAR/ADICIONAL",
                    // El banco deja un espacio delante del título del monto.
                    " MONTO",
                    "CUOTAS PENDIENTES",
                    "VALOR CUOTA",
                    "COMPRA LIDER",
                    "Titular",
                    "PAGO TARJETA CMR",
                ),
                styles = styles(),
            ),
        )!!

        // La fecha era el número 46330 y el monto no tenía formato: se entregan como los escribe
        // el banco, que es lo que la lectura de movimientos sabe entender.
        assertEquals(
            listOf("04/11/2026", "COMPRA LIDER", "Titular", "9.079", "0", "9.079"),
            sheet.rows[1],
        )

        val movements = StatementImport.read(sheet)!!
        assertEquals(2, movements.rows.size)
        assertEquals(0, movements.skipped)
        assertEquals(Direction.DEBIT, movements.rows[0].direction)
        assertEquals(9_079L, movements.rows[0].amount)
        assertEquals("COMPRA LIDER", movements.rows[0].merchant)

        // El pago viene en positivo y el signo está en la columna de la cuota: es plata que entró.
        assertEquals(Direction.CREDIT, movements.rows[1].direction)
        assertEquals(208_960L, movements.rows[1].amount)
        assertEquals("PAGO TARJETA CMR", movements.rows[1].merchant)
        // La planilla de la tarjeta no trae saldo, así que tampoco inventa un corte.
        assertNull(movements.balance)
    }

    @Test
    fun `una celda vacia no corre a las que siguen`() {
        val sheet = Xlsx.sheet(
            book(
                sheet = rows(
                    row(1, shared("A1", 0), shared("B1", 1), shared("C1", 2), shared("D1", 3)),
                    // Sin `C2`: la columna de las cuotas viene vacía y las demás no se corren.
                    row(
                        2,
                        number("A2", "46330", DATE), shared("B2", 4),
                        number("D2", "1234.56", MONEY),
                    ),
                ),
                strings = listOf("Fecha", "Descripción", "Cuotas", "Monto", "LIDER"),
                styles = styles(),
            ),
        )!!

        assertEquals(listOf("04/11/2026", "LIDER", "", "1.234,56"), sheet.rows[1])
        val movements = StatementImport.read(sheet)!!
        assertEquals(1_234L, movements.rows[0].amount)
    }

    @Test
    fun `un numero sin estilo de fecha no se toma como fecha`() {
        val sheet = Xlsx.sheet(
            book(
                sheet = rows(
                    row(1, shared("A1", 0), shared("B1", 1), shared("C1", 2)),
                    // El mismo 46330, pero sin el formato que dice que ese número es una fecha.
                    row(2, number("A2", "46330"), shared("B2", 3), number("C2", "9.990")),
                ),
                strings = listOf("Fecha", "Descripción", "Monto", "LIDER"),
                styles = styles(),
            ),
        )!!

        assertEquals("46.330", sheet.rows[1][0])
        // Sin fecha no hay movimiento: anotar montos en el día equivocado es peor que no anotar.
        assertEquals(0, StatementImport.read(sheet)!!.rows.size)
    }

    @Test
    fun `los textos compartidos llegan con sus simbolos y sus trozos juntos`() {
        val sheet = Xlsx.sheet(
            book(
                sheet = rows(
                    row(1, shared("A1", 0), shared("B1", 1), shared("C1", 2)),
                    row(2, number("A2", "46330", DATE), shared("B2", 3), number("C2", "1000")),
                    row(3, number("A3", "46330", DATE), shared("B3", 4), number("C3", "2000")),
                ),
                strings = listOf("Fecha", "Descripción", "Monto"),
                stringsXml = """<si><t>Fecha</t></si><si><t>Descripción</t></si>""" +
                    """<si><t>Monto</t></si>""" +
                    """<si><t>COMPRA &amp; VUELTA &#209;U&#209;OA</t></si>""" +
                    """<si><r><t>SUPER</t></r><r><t>MERCADO</t></r></si>""",
                styles = styles(),
            ),
        )!!

        assertEquals("COMPRA & VUELTA ÑUÑOA", sheet.rows[1][1])
        // Un nombre partido en dos trozos por la negrita es un solo comercio.
        assertEquals("SUPERMERCADO", sheet.rows[2][1])
    }

    @Test
    fun `la cartola de la cuenta corriente trae el titulo y el numero de cuenta arriba`() {
        val sheet = Xlsx.sheet(
            book(
                sheet = rows(
                    row(1, shared("A1", 0)),
                    row(2, shared("A2", 1)),
                    row(
                        3,
                        shared("A3", 2), shared("B3", 3), shared("C3", 4), shared("D3", 5),
                        shared("E3", 6),
                    ),
                    row(
                        4,
                        shared("A4", 7), shared("B4", 8), shared("C4", 9), shared("E4", 10),
                    ),
                    row(
                        5,
                        shared("A5", 7), shared("B5", 12), shared("D5", 11),
                    ),
                ),
                strings = listOf(
                    "Sebastian Bustos",
                    "Cuenta Corriente: 0-000-95-44148-3",
                    "Fecha", "Detalle", "Monto cargo ($)", "Monto abono ($)", "Saldo ($)",
                    "20-11-2026",
                    "0195264708 TRANSF A ANA PEREZ",
                    "22.000", "18.000", "40.000",
                    "Traspaso Internet de Cta.Cte.",
                ),
                styles = styles(),
            ),
        )!!

        assertEquals("Fecha", sheet.rows[2][0])
        val movements = StatementImport.read(sheet)!!
        assertEquals(2, movements.rows.size)
        assertEquals(Direction.DEBIT, movements.rows[0].direction)
        assertEquals(22_000L, movements.rows[0].amount)
        assertEquals(Direction.CREDIT, movements.rows[1].direction)
        assertEquals(40_000L, movements.rows[1].amount)
        // El corte es el saldo de la fila más nueva que lo trae.
        assertEquals(18_000L, movements.balance)
    }

    @Test
    fun `un zip que no es un libro y un excel viejo no devuelven filas`() {
        val other = ByteArrayOutputStream()
        ZipOutputStream(other).use { zip ->
            zip.putNextEntry(ZipEntry("hola.txt"))
            zip.write("nada que ver".toByteArray())
            zip.closeEntry()
        }
        assertNull(Xlsx.sheet(other.toByteArray()))

        assertFalse(Xlsx.looksLikeZip("Fecha;Monto\n18/11/2026;9.990\n".toByteArray()))
        assertTrue(Xlsx.looksLikeZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)))
        assertTrue(Xlsx.looksLikeLegacy(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0)))
        assertFalse(Xlsx.looksLikeLegacy(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)))
    }

    // --- el .xlsx de mentira --------------------------------------------------------------

    /** Las filas de la hoja, como las guarda Excel. */
    private fun rows(vararg rows: String): String =
        """<worksheet><sheetData>${rows.joinToString("")}</sheetData></worksheet>"""

    private fun row(index: Int, vararg cells: String): String =
        """<row r="$index">${cells.joinToString("")}</row>"""

    /** Una celda de texto compartido: Excel guarda el texto una vez y la celda cita su número. */
    private fun shared(ref: String, index: Int): String = """<c r="$ref" t="s"><v>$index</v></c>"""

    /** Una celda con un número; con estilo puede ser un monto ([MONEY]) o una fecha ([DATE]). */
    private fun number(ref: String, value: String, style: Int? = null): String =
        if (style == null) """<c r="$ref"><v>$value</v></c>"""
        else """<c r="$ref" s="$style"><v>$value</v></c>"""

    /**
     * Un libro de una sola hoja. Es lo que el banco entrega con la extensión .xlsx: un ZIP con el
     * libro, el archivo que traduce la relación a la ruta de la hoja, los textos y los estilos.
     */
    private fun book(
        sheet: String,
        strings: List<String> = emptyList(),
        stringsXml: String? = null,
        styles: String? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry(WORKBOOK_PATH, WORKBOOK)
            zip.entry(RELS_PATH, RELS)
            zip.entry(SHEET_PATH, sheet)
            zip.entry(
                STRINGS_PATH,
                SST + (stringsXml ?: strings.joinToString("") { "<si><t>$it</t></si>" }) + "</sst>",
            )
            if (styles != null) zip.entry(STYLES_PATH, styles)
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /**
     * Los dos formatos que importan: el índice 1 es un monto y el 2 una fecha con formato propio,
     * que es lo único que distingue una fecha de un número en Excel.
     */
    private fun styles(): String =
        """<styleSheet><numFmts count="2">""" +
            """<numFmt numFmtId="164" formatCode="#,##0"/>""" +
            """<numFmt numFmtId="165" formatCode="dd-MM-yyyy"/></numFmts>""" +
            """<cellXfs count="3"><xf numFmtId="0"/><xf numFmtId="164"/>""" +
            """<xf numFmtId="165"/></cellXfs></styleSheet>"""

    private val MONEY = 1
    private val DATE = 2

    private val SHEET_PATH = "xl/worksheets/sheet1.xml"
    private val WORKBOOK_PATH = "xl/workbook.xml"
    private val RELS_PATH = "xl/_rels/workbook.xml.rels"
    private val STRINGS_PATH = "xl/sharedStrings.xml"
    private val STYLES_PATH = "xl/styles.xml"

    private val WORKBOOK = """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
        """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="Movimientos" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private val RELS = """<Relationships """ +
        """xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/""" +
        """relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""

    private val SST = """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">"""
}
