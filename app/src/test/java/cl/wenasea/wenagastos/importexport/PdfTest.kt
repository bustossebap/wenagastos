package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.Direction
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cartola que el banco entrega en PDF no se puede leer como texto: los caracteres están
 * dibujados en coordenadas y, en el archivo real de Santander, salen ordenados por columna y no
 * por fila. Estas pruebas arman un PDF con la misma geometría —la rejilla dibujada, la cabecera de
 * dos renglones, las celdas de cargo y abono vacías, el pie que repite un movimiento y un nombre
 * mal codificado por el banco— y comprueban que de ahí salgan los movimientos correctos.
 *
 * Los datos son inventados a propósito: la prueba se queda en el repo y la cartola de una persona
 * no.
 */
class PdfTest {

    @Test
    fun `una cartola sin rejilla se lee por donde terminan los montos`() {
        val sheet = Pdf.sheet(sinRejilla())!!

        assertEquals(
            listOf("Fecha", "Oficina Nro Doc", "Descripción", "Cargo", "", "Abono", "", "Saldo"),
            sheet.rows.first(),
        )

        // Los montos vienen alineados a la derecha: el de siete cifras empieza más a la izquierda
        // que el de cuatro y aun así caen en la misma columna.
        val rows = StatementImport.read(sheet)!!
        assertEquals(4, rows.rows.size)
        assertEquals("Cargo", rows.template.debitColumn)
        assertEquals("Abono", rows.template.creditColumn)
        assertEquals("Saldo", rows.template.balanceColumn)

        assertEquals(Direction.DEBIT, rows.rows[0].direction)
        assertEquals(30_600L, rows.rows[0].amount)
        assertEquals("Transf. para Kaizen", rows.rows[0].merchant)
        assertEquals(Direction.CREDIT, rows.rows[1].direction)
        assertEquals(30_600L, rows.rows[1].amount)
        assertEquals(Direction.DEBIT, rows.rows[2].direction)
        assertEquals(208_960L, rows.rows[2].amount)
        assertEquals(2_600L, rows.rows[2].balance)
        assertEquals(Direction.CREDIT, rows.rows[3].direction)
        assertEquals(211_560L, rows.rows[3].balance)
        // El corte es el saldo del movimiento más nuevo: la cartola viene con lo nuevo arriba, y
        // ese movimiento dejó la cuenta en cero.
        assertEquals(0L, rows.balance)
    }

    @Test
    fun `las lineas de otro cuadro no son las columnas de la tabla`() {
        // Como el estado de cuenta de la tarjeta: la página trae la rejilla del cuadro de cupos,
        // que no tiene nada que ver con el detalle, y el detalle está dibujado sin rejilla.
        val page = Page()
        page.text(40.0, 719.0, "ESTADO DE CUENTA")
        for (x in listOf(37.0, 161.5, 250.5, 448.5)) page.rule(x, 700.0, 740.0)
        page.text(39.0, 730.0, "CUPO TOTAL")
        page.text(163.0, 730.0, "UTILIZADO")
        page.text(252.0, 730.0, "DISPONIBLE")
        page.text(390.0, 730.0, "CAE")

        page.text(118.76, 500.0, "FECHA DE OPERACIÓN")
        page.text(149.0, 500.0, "DESCRIPCIÓN")
        page.text(right(447.0, "MONTO"), 500.0, "MONTO")
        page.text(118.76, 487.0, "21/10/2026")
        page.text(149.0, 487.0, "Transf. de Ana Perez")
        page.text(right(447.0, "$ 30.600"), 487.0, "$ 30.600")
        page.text(118.76, 474.0, "22/10/2026")
        page.text(149.0, 474.0, "Transf. de Bruno Diaz")
        page.text(right(447.0, "$ 1.400"), 474.0, "$ 1.400")

        val sheet = Pdf.sheet(page.build())!!
        val rows = StatementImport.read(sheet)!!

        assertEquals(2, rows.rows.size)
        assertEquals(30_600L, rows.rows[0].amount)
        assertEquals(Direction.DEBIT, rows.rows[0].direction)
        assertEquals("Transf. de Bruno Diaz", rows.rows[1].merchant)
        assertEquals(1_400L, rows.rows[1].amount)
    }

    @Test
    fun `un texto escrito con la fuente del banco se traduce con su tabla`() {
        // La CMR escribe cada letra como un número elegido por el programa que armó el PDF: el
        // flujo no dice nada por sí solo. La tabla `ToUnicode` de la fuente dice que el 0x35 es una
        // `S`; sin ella, la cartola no se puede leer.
        val rows = listOf(
            listOf("21/10/2026", "Transf. para Kaizen", "$ 30.600", ""),
            listOf("22/10/2026", "Transf. de Ana Perez", "", "$ 1.400"),
        )
        val headers = listOf("Fecha", "Descripción", "Cargo", "Abono")
        val table = (headers + rows.flatten()).joinToString("").toSet()
            .mapIndexed { index, character -> character to 0x21 + index }
            .toMap()
        fun codes(value: String): String = value.map { table.getValue(it).toChar() }.joinToString("")

        val page = Page()
        page.text(40.0, 633.64, codes(headers[0]))
        page.text(99.0, 633.64, codes(headers[1]))
        page.text(right(529.0, codes(headers[2])), 633.64, codes(headers[2]))
        page.text(right(602.0, codes(headers[3])), 633.64, codes(headers[3]))

        rows.forEachIndexed { index, row ->
            val y = 620.64 - index * 15.0
            page.text(40.0, y, codes(row[0]))
            page.text(99.0, y, codes(row[1]))
            if (row[2].isNotEmpty()) page.text(right(529.0, codes(row[2])), y, codes(row[2]))
            if (row[3].isNotEmpty()) page.text(right(602.0, codes(row[3])), y, codes(row[3]))
        }

        val sheet = Pdf.sheet(page.build(cMap = toUnicode(table)))!!
        assertEquals(headers[0], sheet.rows.first()[0])
        assertEquals(headers[1], sheet.rows.first()[1])

        val movements = StatementImport.read(sheet)!!
        assertEquals(2, movements.rows.size)
        assertEquals("Transf. para Kaizen", movements.rows[0].merchant)
        assertEquals(30_600L, movements.rows[0].amount)
        assertEquals(1_400L, movements.rows[1].amount)
    }

    @Test
    fun `un pdf de cartola se lee igual que un csv`() {
        val sheet = Pdf.sheet(cartola()).let { assertNotNull(it); it!! }

        assertEquals(
            listOf(
                "FECHA",
                "SUCURSAL",
                "DESCRIPCION",
                "Nº DCTO",
                "CHEQUES Y OTROS CARGOS",
                "DEPOSITOS Y OTROS ABONOS",
                "SALDO",
            ),
            sheet.rows.first(),
        )
        // El pie ("Resumen de Comisiones") repite el último movimiento: no es una fila más.
        assertEquals(7, sheet.rows.size)

        val movements = StatementImport.read(sheet)!!
        assertEquals(6, movements.rows.size)
        assertEquals(0, movements.skipped)

        val abono = movements.rows[0]
        assertEquals("2026-11-01", abono.date.toString())
        assertEquals(Direction.CREDIT, abono.direction)
        assertEquals(100_000L, abono.amount)
        assertEquals("0000000001 Transf. Ana Perez", abono.merchant)

        // "Transf a" es plata que sale; "Transf." es plata que entra.
        assertEquals(Direction.DEBIT, movements.rows[1].direction)
        assertEquals(100_000L, movements.rows[1].amount)

        // El nombre venía codificado tres veces en el archivo ("sebastiÃÂÂ¡n").
        assertEquals(Direction.CREDIT, movements.rows[2].direction)
        assertEquals("0000000003 Transf. sebastián bustos", movements.rows[2].merchant)

        // Un asterisco y una barra en la descripción no son una fórmula ni una columna.
        assertEquals("*/Giro Nacional VD", movements.rows[4].merchant)
        assertEquals(Direction.DEBIT, movements.rows[4].direction)
        assertEquals(5_000L, movements.rows[4].amount)

        // El corte es el saldo del último día, que en la cartola viene sólo en la última fila.
        assertEquals(41_012L, movements.balance)
        assertEquals("2026-11-06", movements.balanceDate.toString())
        assertEquals("FECHA", movements.template.dateColumn)
        assertEquals("DESCRIPCION", movements.template.merchantColumn)
    }

    @Test
    fun `el anio sale del periodo aunque la cartola cruce diciembre`() {
        val rows = Pdf.sheet(cartola(period = "20/12/2026" to "20/01/2027", rows = listOf(
            listOf("02/01", "Agustinas", "Transf. Ana Perez", "1", "", "10.000", "10.000"),
        )))!!.rows.drop(1)

        assertEquals("02/01/2027", rows.single()[0])
    }

    @Test
    fun `un pdf sin tabla no inventa movimientos`() {
        assertTrue(Pdf.looksLikePdf(cartola()))
        assertNull(Pdf.sheet(sinTabla()))
        assertTrue(!Pdf.looksLikePdf("Fecha;Monto\n1;2\n".toByteArray()))
    }

    @Test
    fun `el estado de cuenta de la tarjeta sin cabecera se lee por sus columnas`() {
        // La CMR dibuja el detalle sin títulos —arriba de las filas va el título de la sección y
        // nada más—, con la fecha en su propia columna y la cuota entre el monto y lo que se cobra
        // este mes. Las columnas sólo se reconocen por lo que trae cada renglón.
        val sheet = Pdf.sheet(estadoDeCuenta())!!
        assertEquals(
            listOf("Fecha", "Descripción", "Monto", "Cuota", "Valor cuota"),
            sheet.rows.first(),
        )

        val rows = StatementImport.read(sheet)!!.rows
        assertEquals(4, rows.size)

        // La compra en cuotas se anota por lo que se cobra este mes, con el precio de la compra y
        // la cuota aparte: anotar los $86.980 cada mes es pagar el sillón tres veces.
        assertEquals(28_993L, rows[0].amount)
        assertEquals(86_980L, rows[0].originAmount)
        assertEquals(1, rows[0].installmentNumber)
        assertEquals(3, rows[0].installmentTotal)
        assertEquals(Direction.DEBIT, rows[0].direction)

        // `01/01` es una compra pagada de una vez, no una compra en cuotas.
        assertEquals(23_532L, rows[1].amount)
        assertNull(rows[1].installmentTotal)
        assertTrue(!rows[1].isPayment)

        // El pago de la tarjeta viene con el signo en el monto: es plata que entra a la tarjeta.
        assertEquals(Direction.CREDIT, rows[2].direction)
        assertEquals(208_960L, rows[2].amount)
        assertNull(rows[2].originAmount)
        // Y la fila queda marcada como el pago de la tarjeta: en una tarjeta eso no es un ingreso.
        assertTrue(rows[2].isPayment)

        // Una fila que sólo trae el valor de la cuota —el impuesto de timbres— también es un
        // movimiento: la columna del monto viene vacía.
        assertEquals(Direction.DEBIT, rows[3].direction)
        assertEquals(172L, rows[3].amount)
        assertTrue(!rows[3].isPayment)
    }

    @Test
    fun `el resumen de la tarjeta se lee de su cupon y de su cuadro de cupos`() {
        // Geometría copiada del estado de cuenta de la CMR: el cupón de pago escribe el valor a la
        // derecha de su etiqueta y el cuadro de cupos lo escribe debajo, centrado en su columna.
        // Las cifras son inventadas: lo que se prueba es dónde mirar.
        val file = Pdf.statement(estadoDeCuenta(withSummary = true))!!

        val summary = file.summary!!
        assertEquals(700_000L, summary.debt)
        assertEquals(650_000L, summary.billed)
        assertEquals(1_500_000L, summary.limit)
        assertEquals("2026-11-04", summary.asOf.toString())
        assertEquals("2026-11-20", summary.due.toString())

        // El resumen no se cuela como movimiento: la tabla sigue teniendo sus cuatro renglones.
        assertEquals(4, StatementImport.read(file.sheet)!!.rows.size)
    }

    @Test
    fun `una cartola de cuenta corriente no trae resumen`() {
        // El saldo de una cuenta viene en su columna; no hay cupo que leer y no se inventa.
        val file = Pdf.statement(cartola())!!
        assertNull(file.summary)
        assertEquals(6, StatementImport.read(file.sheet)!!.rows.size)
    }

    @Test
    fun `la plantilla que el telefono recuerda del banco no se come la tarjeta`() {
        // El teléfono no lee la cartola de cero: le pasa la plantilla que guardó del banco
        // (`SettingsViewModel.importStatement`), y Falabella tiene la cuenta corriente y la tarjeta
        // bajo la misma institución. La plantilla de la cuenta corriente —la que quedó guardada en
        // el A34— trae `Cargo` y `Abono` y ningún `Monto`, así que sus 101 filas quedaban sin leer.
        val cuentaCorriente = StatementTemplate(
            dateColumn = "Fecha",
            merchantColumn = "Descripcion",
            debitColumn = "Cargo",
            creditColumn = "Abono",
            balanceColumn = "Saldo",
        )

        val sheet = Pdf.sheet(estadoDeCuenta())!!

        assertEquals(4, StatementImport.read(sheet)!!.rows.size)
        assertEquals(4, StatementImport.read(sheet, cuentaCorriente)!!.rows.size)
    }

    @Test
    fun `una tabla sin cabecera y con dos filas no se inventa`() {
        // Sin títulos, dos renglones no son una tabla: puede ser cualquier cosa de la hoja.
        assertNull(Pdf.sheet(estadoDeCuenta(rows = ESTADO_ROWS.take(2))))
    }


    @Test
    fun `una cartola de dos paginas no mezcla las filas`() {
        val sheet = Pdf.sheet(
            cartola(
                secondPage = listOf(
                    listOf("07/11", "Agustinas", "0000000004 Transf. Diego Soto", "", "", "5.000", "46.012"),
                ),
            ),
        )!!

        val rows = sheet.rows.drop(1)
        assertEquals(7, rows.size)
        // La segunda página también tiene filas de la misma altura: no se pisaron, y el año se
        // completó con el período de la primera.
        assertEquals("07/11/2026", rows.last()[0])
        assertEquals("0000000004 Transf. Diego Soto", rows.last()[2])
        assertEquals("46.012", rows.last()[6])
    }

    /**
     * Una cartola con la geometría de la de Santander: la rejilla dibujada, la cabecera en dos
     * renglones ("CHEQUES Y OTROS" arriba y "CARGOS" abajo), las filas sin año y el pie que repite
     * un movimiento.
     */
    private fun cartola(
        period: Pair<String, String> = "30/09/2026" to "30/11/2026",
        rows: List<List<String>> = MOVEMENTS,
        secondPage: List<List<String>> = emptyList(),
    ): ByteArray {
        val page = Page()
        page.text(32.0, 717.0, "CARTOLA")
        page.text(300.0, 717.0, "DESDE")
        page.text(380.0, 717.0, "HASTA")
        page.text(300.0, 705.0, period.first)
        page.text(380.0, 705.0, period.second)
        page.text(32.0, 658.0, "DETALLE DE MOVIMIENTOS")
        page.grid(631.5, 213.5)
        page.header(641.33)
        rows.forEachIndexed { index, row -> page.row(620.38 - index * 10.0, row) }

        // El pie: un título y la misma fila del último movimiento, sin la columna de saldo.
        val bottom = 620.38 - rows.size * 10.0
        page.text(32.0, bottom, "Resumen de Comisiones")
        page.text(32.0, bottom - 10, "*********************")
        page.row(bottom - 20, rows.last().mapIndexed { index, cell -> if (index == 6) "" else cell })

        if (secondPage.isNotEmpty()) {
            page.pageBreak()
            page.grid(631.5, 213.5)
            page.header(641.33)
            secondPage.forEachIndexed { index, row -> page.row(620.38 - index * 10.0, row) }
        }
        return page.build()
    }

    /**
     * Una cartola como las de Falabella: sin una sola línea de rejilla, con los montos alineados a
     * la derecha y la cabecera escrita celda contra celda. Las columnas hay que sacarlas del texto.
     */
    private fun sinRejilla(): ByteArray {
        val page = Page()
        page.text(40.0, 719.0, "Cuenta Corriente / Cartola Histórica")
        page.text(40.0, 633.64, "Fecha")
        page.text(99.0, 633.64, "Oficina")
        page.text(201.0, 633.64, "Nro Doc")
        page.text(246.0, 633.64, "Descripción")
        page.text(right(CARGO, "Cargo"), 633.64, "Cargo")
        page.text(right(ABONO, "Abono"), 633.64, "Abono")
        page.text(right(SALDO, "Saldo"), 633.64, "Saldo")

        val rows = listOf(
            listOf("21/10/2026", "Transf. para Kaizen", "$ 30.600", "", "$ 0"),
            listOf("21/10/2026", "Transf. de Ana Perez", "", "$ 30.600", "$ 30.600"),
            listOf("13/10/2026", "Transf para Pago Tarjeta CMR", "$ 208.960", "", "$ 2.600"),
            listOf("13/10/2026", "Transf. de Diego Soto", "", "$ 208.960", "$ 211.560"),
        )
        rows.forEachIndexed { index, row ->
            val y = 620.64 - index * 15.0
            page.text(40.0, y, row[0])
            page.text(99.0, y, "Sucursal Virtual 0")
            page.text(246.0, y, row[1])
            if (row[2].isNotEmpty()) page.text(right(CARGO, row[2]), y, row[2])
            if (row[3].isNotEmpty()) page.text(right(ABONO, row[3]), y, row[3])
            page.text(right(SALDO, row[4]), y, row[4])
        }
        return page.build()
    }

    /**
     * Un estado de cuenta de tarjeta como el de la CMR: sin una línea de rejilla, sin cabecera de
     * tabla —arriba de las filas va el título de la sección— y con la fecha en su propia columna,
     * la marca de titular (`T`), el monto, la cuota (`01/01`) y lo que se cobra este mes, en ese
     * orden de izquierda a derecha.
     */
    private fun estadoDeCuenta(
        rows: List<List<String>> = ESTADO_ROWS,
        withSummary: Boolean = false,
    ): ByteArray {
        val page = Page()
        page.text(36.67, 780.0, "ESTADO DE CUENTA")
        if (withSummary) {
            // El cupón de pago: la etiqueta a la izquierda y el valor en la misma línea, a la derecha.
            page.text(38.67, 684.4, "Fecha Facturación Estado de Cuenta:")
            page.text(182.61, 684.4, "04/11/2026")
            page.text(109.26, 628.74, "• Pagar Hasta")
            page.text(273.96, 628.74, "20/11/2026")
            page.text(109.26, 614.74, "• Monto Total Facturado a Pagar")
            page.text(273.96, 614.74, "$650.000")
            page.text(109.26, 600.74, "• Monto mínimo a pagar")
            page.text(273.96, 600.74, "$98.520")
            // El cuadro de cupos: los títulos en un renglón y los números debajo, en su columna.
            page.text(161.73, 485.04, "Cupo Total")
            page.text(223.85, 485.04, "Cupo Utilizado")
            page.text(289.73, 485.04, "Cupo Disponible")
            page.text(38.67, 471.54, "Cupo Compras*")
            page.text(167.96, 472.04, "1.500.000")
            page.text(239.81, 472.04, "700.000")
            page.text(341.76, 472.04, "800.000")
        }
        page.text(36.67, 570.0, "COMPRAS NACIONALES")
        rows.forEachIndexed { index, row ->
            val y = 548.0 - index * 13.0
            page.text(36.67, y, row[0])
            page.text(101.22, y, row[1])
            page.text(150.12, y, row[2])
            page.text(301.39, y, "T")
            if (row[3].isNotEmpty()) page.text(331.6, y, row[3])
            if (row[4].isNotEmpty()) page.text(392.11, y, row[4])
            if (row[5].isNotEmpty()) page.text(432.25, y, row[5])
            page.text(471.23, y, row[6])
            if (row[7].isNotEmpty()) page.text(537.0, y, row[7])
        }
        return page.build()
    }

    /** Dónde empieza un monto que el banco alinea a la derecha en [edge]. */
    private fun right(edge: Double, value: String): Double {
        val units = value.sumOf { if (it == '.' || it == ' ') NARROW.toDouble() else WIDE.toDouble() }
        return edge - units * FONT_SIZE / 1000.0
    }

    /** La tabla `ToUnicode` que traduce los códigos de la fuente propia a letras. */
    private fun toUnicode(table: Map<Char, Int>): String = buildString {
        append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
        append("1 begincodespacerange\n<0000><FFFF>\nendcodespacerange\n")
        append(table.size).append(" beginbfrange\n")
        for ((character, code) in table) {
            val from = code.toString(16).padStart(4, '0')
            val to = character.code.toString(16).padStart(4, '0')
            append("<$from><$from><$to>\n")
        }
        append("endbfrange\nendcmap\nCMapName currentdict /CMap defineresource pop\nend end\n")
    }

    /** Un PDF con texto y sin rejilla: no es una tabla y no se puede inventar. */
    private fun sinTabla(): ByteArray = Page().apply {
        text(32.0, 700.0, "Hola")
        text(32.0, 690.0, "01/11/2026 10.000")
    }.build()

    /**
     * Escribe una página de PDF. El texto va en coordenadas absolutas (`Tm`), que es como lo
     * escriben los generadores de cartolas: cada trozo sabe dónde está aunque en el archivo venga
     * ordenado por columna.
     */
    private class Page {
        private val pages = arrayListOf(StringBuilder())
        private val content: StringBuilder get() = pages.last()

        /** Empieza otra página: la cartola de un mes puede no caber en una. */
        fun pageBreak() {
            pages.add(StringBuilder())
        }

        fun text(x: Double, y: Double, value: String) {
            content.append("BT 1 0 0 1 $x $y Tm /F1 8 Tf (").append(escape(value)).append(")Tj ET\n")
        }

        fun rule(x: Double, from: Double, to: Double) {
            content.append("$x $from m $x $to l S\n")
        }

        /** Una fila de la tabla: los montos van alineados a la derecha, como en el banco. */
        fun row(y: Double, cells: List<String>) {
            text(32.0, y, cells[0])
            text(59.0, y, cells[1])
            text(112.0, y, cells[2])
            text(327.2, y, cells[3])
            right(441.0, y, cells[4])
            right(523.0, y, cells[5])
            right(603.0, y, cells[6])
        }

        /** La cabecera de la tabla, que el banco repite en cada página. */
        fun header(y: Double) {
            text(32.0, y, "FECHA")
            text(59.0, y, "SUCURSAL")
            text(112.0, y, "DESCRIPCION")
            text(327.0, y, "Nº DCTO")
            text(365.0, y, "CHEQUES Y OTROS")
            text(450.0, y, "DEPOSITOS Y OTROS")
            text(574.0, y, "SALDO")
            text(365.0, y - 8.14, "CARGOS")
            text(450.0, y - 8.14, "ABONOS")
        }

        /** La rejilla de la tabla, que es de donde salen las fronteras de las columnas. */
        fun grid(top: Double, bottom: Double) {
            for (x in COLUMN_EDGES) rule(x, bottom, top)
            // Un divisor de una sola celda de la cabecera: no es un borde de columna.
            rule(337.5, top, top + 18)
        }

        private fun right(edge: Double, y: Double, value: String) {
            if (value.isEmpty()) return
            text(edge - value.length * 8 * 0.6, y, value)
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

        /** El archivo PDF, con su xref y su trailer, para que además sea un PDF de verdad. */
        fun build(cMap: String? = null): ByteArray {
            val streams = pages.map { deflate(it.toString().toByteArray(Charsets.ISO_8859_1)) }
            val out = ByteArrayOutputStream()
            val offsets = ArrayList<Int>()

            fun write(text: String) {
                out.write(text.toByteArray(Charsets.ISO_8859_1))
            }

            fun objectAt(number: Int, body: String) {
                offsets.add(out.size())
                write("$number 0 obj\n$body\nendobj\n")
            }

            fun streamAt(number: Int, body: String, stream: ByteArray) {
                offsets.add(out.size())
                write("$number 0 obj\n<< /Length ${stream.size} $body >>\nstream\n")
                out.write(stream)
                write("\nendstream\nendobj\n")
            }

            // 1 catálogo, 2 páginas, 3 fuente y después un objeto de página y su contenido por hoja.
            val kids = streams.indices.joinToString(" ") { "${PAGE + it * 2} 0 R" }
            val custom = cmapObject(streams.size)
            write("%PDF-1.4\n")
            objectAt(1, "<< /Type /Catalog /Pages 2 0 R >>")
            objectAt(2, "<< /Type /Pages /Kids [$kids] /Count ${streams.size} >>")
            if (cMap == null) {
                objectAt(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
            } else {
                // Una fuente como la de la CMR: el texto son códigos y la tabla dice qué letra es
                // cada uno. Al lector solo le importa `/ToUnicode`; los demás datos sobran.
                objectAt(
                    3,
                    "<< /Type /Font /Subtype /Type0 /BaseFont /Custom /Encoding /Identity-H " +
                        "/ToUnicode $custom 0 R >>",
                )
            }
            streams.forEachIndexed { index, stream ->
                val page = PAGE + index * 2
                val contents = page + 1
                objectAt(
                    page,
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 905] " +
                        "/Resources << /Font << /F1 3 0 R >> >> /Contents $contents 0 R >>",
                )
                streamAt(contents, "/Filter /FlateDecode", stream)
            }
            if (cMap != null) {
                streamAt(custom, "/Filter /FlateDecode", deflate(cMap.toByteArray(Charsets.ISO_8859_1)))
            }

            val count = if (cMap == null) 4 + streams.size * 2 else custom + 1
            val xref = out.size()
            write("xref\n0 $count\n0000000000 65535 f \n")
            for (offset in offsets) write("%010d 00000 n \n".format(offset))
            write("trailer\n<< /Size $count /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
            return out.toByteArray()
        }

        /** El número del objeto de la tabla, después de las páginas. */
        private fun cmapObject(pages: Int): Int = PAGE + pages * 2

        private fun deflate(bytes: ByteArray): ByteArray {
            val deflater = Deflater()
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size)
            val buffer = ByteArray(8 * 1024)
            while (!deflater.finished()) {
                val read = deflater.deflate(buffer)
                out.write(buffer, 0, read)
            }
            deflater.end()
            return out.toByteArray()
        }

        private companion object {
            /** El primer objeto de página; cada hoja ocupa dos objetos (página y contenido). */
            const val PAGE = 4
        }
    }

    private companion object {

        /** Los bordes verticales que dibuja la cartola: fecha, sucursal, descripción y montos. */
        val COLUMN_EDGES = listOf(30.5, 58.5, 109.5, 324.5, 362.5, 443.5, 525.5, 605.5)

        /** Dónde termina cada columna de plata en la cartola sin rejilla, como en el banco. */
        const val CARGO = 529.0
        const val ABONO = 602.0
        const val SALDO = 693.0

        /** El tamaño de letra de los montos y los anchos de Helvetica que usa el banco. */
        const val FONT_SIZE = 8.0
        const val NARROW = 278
        const val WIDE = 556

        /** Así viene el nombre en el archivo del banco: codificado tres veces. */
        const val MOJIBAKE = "sebasti\u00C3\u00C2\u00C2\u00A1n"

        /**
         * Las filas del estado de cuenta de la tarjeta, en el orden en que las dibuja el banco:
         * ciudad, fecha, comercio, monto, monto otra vez, cuota, mes de facturación y lo que se
         * cobra este mes. La tercera trae el pago con el signo en el monto y la última el impuesto,
         * que sólo trae la columna de la derecha.
         */
        val ESTADO_ROWS = listOf(
            listOf("S/I", "12/10/2026", "Ikea", "86.980", "86.980", "01/03", "nov-2026", "28.993"),
            listOf("Santiago", "05/10/2026", "Unired cl enel", "23.532", "23.532", "01/01", "oct-2026", "23.532"),
            listOf("S/I", "10/10/2026", "Pago tarjeta cmr", "-208.960", "-208.960", "01/01", "oct-2026", ""),
            listOf("Santiago", "04/11/2026", "Impuesto ite", "", "", "", "nov-2026", "172"),
        )

        /** Seis movimientos; el primero y el último de cada día son los que traen saldo. */
        val MOVEMENTS = listOf(
            listOf("01/11", "Agustinas", "0000000001 Transf. Ana Perez", "5000001", "", "100.000", ""),
            listOf("01/11", "O.Gerencia", "0000000002 Transf a Bruno Diaz", "", "100.000", "", "0"),
            listOf("02/11", "Agustinas", "0000000003 Transf. $MOJIBAKE bustos", "", "", "30.000", "30.000"),
            listOf("05/11", "Agustinas", "Traspaso Internet de Cta.Cte.", "", "", "20.000", ""),
            listOf("05/11", "Agustinas", "*/Giro Nacional VD", "2752040", "5.000", "", "45.000"),
            listOf("06/11", "Agustinas", "COM.MANTENCION PLAN", "", "3.988", "", "41.012"),
        )
    }
}
