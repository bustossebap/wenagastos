package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.Direction
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cartola es el archivo que el banco sí escribe completo: no hay aviso que se pierda, pero
 * tampoco hay dos formatos iguales. Estas pruebas fijan las tres formas que aparecen en Chile —dos
 * columnas de cargo y abono, una sola de monto y una con signo— y lo que jamás puede pasar: que un
 * total o una fila en blanco entren como movimiento, o que un abono quede como gasto.
 */
class StatementImportTest {

    private fun read(text: String, remembered: StatementTemplate? = null): StatementImport.Sheet =
        StatementImport.read(Csv.split(text), remembered)!!

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    @Test
    fun `la cartola de cargo y abono separa cada lado`() {
        val sheet = read(
            "FECHA;DESCRIPCION;CARGO;ABONO;SALDO\r\n" +
                "18/11/2026;COMPRA LIDER EXPRESS;12.345;;1.234.567\r\n" +
                "19/11/2026;TRANSFERENCIA RECIBIDA;;50.000;1.284.567\r\n",
        )

        assertEquals(2, sheet.rows.size)
        assertEquals(0, sheet.skipped)
        val gasto = sheet.rows[0]
        assertEquals(date(2026, 11, 18), gasto.date)
        assertEquals(12_345L, gasto.amount)
        assertEquals(Direction.DEBIT, gasto.direction)
        assertEquals("COMPRA LIDER EXPRESS", gasto.merchant)
        assertEquals(Direction.CREDIT, sheet.rows[1].direction)
        assertEquals(50_000L, sheet.rows[1].amount)
        // El corte es el de la fila más nueva, no el de la última línea del archivo.
        assertEquals(1_284_567L, sheet.balance)
        assertEquals(date(2026, 11, 19), sheet.balanceDate)
    }

    @Test
    fun `la plantilla se deduce de la cabecera y se puede volver a leer`() {
        val template = read("Fecha;Descripción;Cargo;Abono\n18/11/2026;LIDER;1000;;").template

        assertEquals("Fecha", template.dateColumn)
        assertEquals("Descripción", template.merchantColumn)
        assertEquals("Cargo", template.debitColumn)
        assertEquals("Abono", template.creditColumn)
        assertEquals(template, StatementTemplate.fromJson(template.toJson()))
    }

    @Test
    fun `una cartola con titulo arriba encuentra la cabecera mas abajo`() {
        val sheet = read(
            "Cartola cuenta corriente\r\n" +
                "Cuenta 000123456\r\n" +
                "Fecha;Descripción;Monto\r\n" +
                "18-11-2026;PAGO TARJETA;19990\r\n",
        )

        assertEquals(1, sheet.rows.size)
        assertEquals(19_990L, sheet.rows[0].amount)
        assertEquals(date(2026, 11, 18), sheet.rows[0].date)
    }

    @Test
    fun `sin signo es una compra y con mas es plata que entra`() {
        val sheet = read(
            "Fecha;Descripción;Monto\n" +
                "18/11/2026;LIDER;9.990\n" +
                "19/11/2026;DEVOLUCION LIDER;+3.000\n" +
                "20/11/2026;PAGO;-25.000\n",
        )

        assertEquals(Direction.DEBIT, sheet.rows[0].direction)
        assertEquals(Direction.CREDIT, sheet.rows[1].direction)
        assertEquals(3_000L, sheet.rows[1].amount)
        assertEquals(Direction.DEBIT, sheet.rows[2].direction)
        assertEquals(25_000L, sheet.rows[2].amount)
    }

    @Test
    fun `los totales y las filas en blanco no son movimientos`() {
        val sheet = read(
            "Fecha;Descripción;Cargo;Abono;Saldo\n" +
                "18/11/2026;LIDER;1.000;;9.000\n" +
                ";;;;\n" +
                "TOTAL;TOTAL CARGO;1.000;;\n",
        )

        assertEquals(1, sheet.rows.size)
        assertEquals(2, sheet.skipped)
    }

    @Test
    fun `los centavos y el signo de miles se leen como pesos`() {
        val sheet = read("Fecha;Descripción;Monto\n18/11/2026;LIDER;12.345,00\n")

        assertEquals(12_345L, sheet.rows[0].amount)
    }

    @Test
    fun `un saldo negativo de la tarjeta se conserva`() {
        val sheet = read(
            "Fecha;Descripción;Monto;Saldo\n" +
                "18/11/2026;CUOTA CMR;45.000;-250.000\n",
        )

        assertEquals(-250_000L, sheet.balance)
    }

    @Test
    fun `la plantilla recordada se usa aunque cambie el orden de las columnas`() {
        val remembered = StatementTemplate(
            dateColumn = "Fecha",
            merchantColumn = "Descripción",
            amountColumn = "Monto",
        )

        val sheet = read("Monto;Descripción;Fecha\n9.900;LIDER;18/11/2026\n", remembered)

        assertEquals(9_900L, sheet.rows[0].amount)
        assertEquals("LIDER", sheet.rows[0].merchant)
        assertEquals(date(2026, 11, 18), sheet.rows[0].date)
    }

    @Test
    fun `una fila con cargo y abono a la vez no se anota`() {
        val sheet = read(
            "Fecha;Descripción;Cargo;Abono\n" +
                "18/11/2026;RARO;1.000;2.000\n",
        )

        assertEquals(0, sheet.rows.size)
        assertEquals(1, sheet.skipped)
    }

    @Test
    fun `sin cabecera reconocible no se inventa nada`() {
        assertNull(StatementImport.read(Csv.split("foo;bar\n1;2\n")))
        assertNull(StatementImport.read(Csv.split("")))
    }

    @Test
    fun `el detalle de la tarjeta trae fecha y descripcion en minusculas`() {
        val sheet = read(
            "fecha;descripcion;monto;saldo\n" +
                "18/11/2026 10:22;LIDER EXPRESS;12.345;1.234.567\n",
        )

        assertEquals(date(2026, 11, 18), sheet.rows[0].date)
        assertTrue(sheet.rows[0].rawText.contains("LIDER EXPRESS"))
    }

    @Test
    fun `un pago con el signo en otra columna es plata que entra`() {
        val sheet = read(
            "Fecha;Descripción;Monto;Cuotas;Valor cuota\n" +
                "18/11/2026;COMPRA LIDER;9.990;0;9.990\n" +
                "19/11/2026;PAGO TARJETA;208.960;0;-208.960\n",
        )

        assertEquals(Direction.DEBIT, sheet.rows[0].direction)
        assertEquals(9_990L, sheet.rows[0].amount)
        // El banco escribe el pago en positivo y el signo en la columna de la cuota.
        assertEquals(Direction.CREDIT, sheet.rows[1].direction)
        assertEquals(208_960L, sheet.rows[1].amount)
    }
}
