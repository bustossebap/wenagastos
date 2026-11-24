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
    fun `la planilla de la tarjeta no anota el saldo inicial y el pago entra como abono`() {
        // El Excel del banco lista primero el saldo con el que abre el período —la deuda de la
        // factura anterior, con el monto en la columna del cargo— y después los movimientos.
        // Anotarlo duplicaba la deuda de la tarjeta, y el pago, que viene en la columna del abono,
        // quedaba como plata que entra a la cuenta.
        val sheet = read(
            "Fecha;Tipo;Detalle;Monto cargo;Monto abono\n" +
                "26/10/2026;;SALDO INICIAL;$-326.759;\n" +
                "04/11/2026;;PAGO;;$326.759\n" +
                "7/11/2026;:COMIDAS DE AUTOSERVICIO;SUC LIRA 43;$-3.380;\n",
        )

        assertEquals(2, sheet.rows.size)
        assertEquals(1, sheet.skipped)

        val pago = sheet.rows[0]
        assertEquals(Direction.CREDIT, pago.direction)
        assertEquals(326_759L, pago.amount)
        assertTrue(pago.isPayment)

        val compra = sheet.rows[1]
        assertEquals(date(2026, 11, 7), compra.date)
        assertEquals(Direction.DEBIT, compra.direction)
        assertEquals(3_380L, compra.amount)
        assertEquals("SUC LIRA 43", compra.merchant)
        assertTrue(!compra.isPayment)
    }

    @Test
    fun `una fecha con el mes sin cero adelante tambien se lee`() {
        // La planilla exportada de la tarjeta escribe `11/7/2026`: con el formato estricto julio
        // tenía que ser `07` y la fila se caía sin decir nada.
        val sheet = read("Fecha;Detalle;Monto cargo;Monto abono\n11/7/2026;SUC LIRA;$-3.380;\n")

        assertEquals(date(2026, 7, 11), sheet.rows[0].date)
        assertEquals(3_380L, sheet.rows[0].amount)
        assertEquals(Direction.DEBIT, sheet.rows[0].direction)
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
    fun `la plantilla de la cuenta corriente no se come la tabla de la tarjeta`() {
        // Falabella guarda una sola plantilla por institución: la de la cuenta corriente `8777`
        // —Cargo y Abono, sin columna de monto— quedó recordada al importar esa cartola. Su fecha y
        // su descripción son las mismas que las del estado de cuenta de la tarjeta, así que
        // «calzaba» con su cabecera y dejaba todas las filas sin leer.
        val remembered = StatementTemplate(
            dateColumn = "Fecha",
            merchantColumn = "Descripcion",
            debitColumn = "Cargo",
            creditColumn = "Abono",
            balanceColumn = "Saldo",
        )

        val sheet = read(
            "Fecha;Descripción;Monto;Cuota;Valor cuota\n" +
                "12/10/2026;Ikea;86.980;01/03;28.993\n" +
                "13/10/2026;Lider;23.532;01/01;23.532\n" +
                "04/11/2026;Pago tarjeta;208.960;;-208.960\n",
            remembered,
        )

        assertEquals(3, sheet.rows.size)
        assertEquals(0, sheet.skipped)
        // La plantilla que vale es la que se deduce del archivo, no la que estaba guardada.
        assertNull(sheet.template.debitColumn)
        assertEquals("Monto", sheet.template.amountColumn)
        // La compra en cuotas se anota por lo que se cobra este mes, con el precio aparte.
        assertEquals(28_993L, sheet.rows[0].amount)
        assertEquals(86_980L, sheet.rows[0].originAmount)
        assertEquals(1, sheet.rows[0].installmentNumber)
        assertEquals(3, sheet.rows[0].installmentTotal)
        // El pago viene con el signo en la columna de la cuota: es plata que entra.
        assertEquals(Direction.CREDIT, sheet.rows[2].direction)
        assertEquals(208_960L, sheet.rows[2].amount)
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
    fun `cuando la cabecera titula dos columnas manda el contenido`() {
        // Como el estado de cuenta de la tarjeta: "CARGO DEL MES" está escrito sobre la columna de
        // la cuota, no sobre la del monto.
        val sheet = read(
            "LUGAR DE OPERACIÓN;FECHA DE OPERACIÓN;DESCRIPCIÓN OPERACIÓN O COBRO;" +
                "MONTO;VALOR CUOTA MENSUAL;CARGO DEL MES NºCUOTA\r\n" +
                "TGR;09/06/26;MERCADO PAGO 4 TCOM;26.990;4.498;05/06\r\n" +
                "SANTIAGO;24/09/26;ALTAMAR SPA;;7.500;1/1\r\n",
        )

        assertEquals(2, sheet.rows.size)
        assertEquals(4_498L, sheet.rows[0].amount)
        assertEquals(date(2026, 6, 9), sheet.rows[0].date)
        assertEquals("MERCADO PAGO 4 TCOM", sheet.rows[0].merchant)
        assertEquals(Direction.DEBIT, sheet.rows[0].direction)
        assertEquals(7_500L, sheet.rows[1].amount)
        assertEquals(0, sheet.skipped)
    }

    @Test
    fun `una cuota no es un monto`() {
        // "05/06" son puros dígitos, pero no son plata: sin esto, el pago se leería como un cargo
        // de 506 pesos y el abono de la misma fila quedaría sin anotar.
        val sheet = read(
            "Fecha;Descripción;Cargo;Abono\n" +
                "18/11/2026;PAGO TARJETA;05/06;10.000\n",
        )

        assertEquals(1, sheet.rows.size)
        assertEquals(Direction.CREDIT, sheet.rows[0].direction)
        assertEquals(10_000L, sheet.rows[0].amount)
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

    @Test
    fun `una compra en cuotas guarda la cuota del mes y el precio de la compra`() {
        // Santander: `NºCUOTA` dice en cuál va (`05/06`), `VALOR CUOTA MENSUAL` es la plata del mes
        // y `MONTO` el precio de la compra. Anotar el monto cada mes es pagar la compra seis veces.
        val sheet = read(
            "FECHA;DESCRIPCION;MONTO;VALOR CUOTA MENSUAL;NºCUOTA\r\n" +
                "09/06/2026;MERCADO PAGO 4 TCOM;26.990;4.498;05/06\r\n" +
                "10/06/2026;LIDER EXPRESS;12.345;;\r\n",
        )

        val cuota = sheet.rows[0]
        assertEquals(4_498L, cuota.amount)
        assertEquals(5, cuota.installmentNumber)
        assertEquals(6, cuota.installmentTotal)
        assertEquals(26_990L, cuota.originAmount)

        // La compra pagada de una vez no queda marcada como cuota.
        assertNull(sheet.rows[1].installmentNumber)
        assertNull(sheet.rows[1].installmentTotal)
        assertNull(sheet.rows[1].originAmount)
    }

    @Test
    fun `las cuotas pendientes de falabella se leen con el precio de la compra`() {
        // Falabella no dice en qué cuota va: dice cuántas faltan. Con el precio de la compra y la
        // cuota del mes se deduce igual (`MONTO 600.000` / `VALOR CUOTA 50.000` = 12 cuotas).
        val sheet = read(
            "FECHA;DESCRIPCION;MONTO;CUOTAS PENDIENTES;VALOR CUOTA\r\n" +
                "24/09/2026;ALTAMAR SPA;600.000;9;50.000\r\n",
        )

        val cuota = sheet.rows[0]
        assertEquals(50_000L, cuota.amount)
        assertEquals(3, cuota.installmentNumber)
        assertEquals(12, cuota.installmentTotal)
        assertEquals(600_000L, cuota.originAmount)
        assertEquals(Direction.DEBIT, cuota.direction)
    }

    @Test
    fun `pagar la tarjeta no es una compra en cuotas`() {
        // El pago viene con la misma columna de cuotas llena (`0`), pero la plata entra a la
        // tarjeta: no hay compra que pagar en cuotas.
        val sheet = read(
            "FECHA;DESCRIPCION;MONTO;CUOTAS PENDIENTES;VALOR CUOTA\r\n" +
                "18/11/2026;PAGO TARJETA;208.960;0;-208.960\r\n",
        )

        val pago = sheet.rows[0]
        assertEquals(Direction.CREDIT, pago.direction)
        assertEquals(208_960L, pago.amount)
        assertNull(pago.installmentNumber)
        assertNull(pago.installmentTotal)
        assertNull(pago.originAmount)
    }

    @Test
    fun `un precio menor que la cuota no se guarda`() {
        // Una columna mal leída no puede dejar una compra con precio menor que su propia cuota.
        val sheet = read(
            "FECHA;DESCRIPCION;MONTO;VALOR CUOTA;NºCUOTA\r\n" +
                "09/06/2026;LIDER;1.000;4.498;05/06\r\n",
        )

        assertEquals(4_498L, sheet.rows[0].amount)
        assertEquals(5, sheet.rows[0].installmentNumber)
        assertNull(sheet.rows[0].originAmount)
    }

    @Test
    fun `el corte de un ultimo dia con varios movimientos es el del cierre`() {
        // El saldo se guarda como cierre del dia: si el ultimo dia trae varios movimientos, vale el
        // del ultimo renglon, no el del primero. Con la cartola de viejo a nuevo, el primero de ese
        // dia es el que abre la jornada.
        val sheet = read(
            "Fecha;Descripción;Cargo;Abono;Saldo\r\n" +
                "22/11/2026;TRANSFERENCIA DE JUAN;;100.000;100.000\r\n" +
                "23/11/2026;GIRO CAJERO;5.000;;95.000\r\n" +
                "23/11/2026;TRANSFERENCIA PARA JUAN;2.000;;93.000\r\n",
        )

        assertEquals(3, sheet.rows.size)
        assertEquals(93_000L, sheet.balance)
        assertEquals(date(2026, 11, 23), sheet.balanceDate)
    }

    @Test
    fun `el corte del excel del banco, que viene al reves, es el del primer renglon`() {
        // El Excel de Falabella lista de lo mas nuevo a lo mas viejo: ahi el cierre del dia es el
        // primer renglon de ese dia.
        val sheet = read(
            "Fecha;Descripción;Cargo;Abono;Saldo\r\n" +
                "23/11/2026;TRANSFERENCIA PARA JUAN;2.000;;93.000\r\n" +
                "23/11/2026;GIRO CAJERO;5.000;;95.000\r\n" +
                "22/11/2026;TRANSFERENCIA DE JUAN;;100.000;100.000\r\n",
        )

        assertEquals(3, sheet.rows.size)
        assertEquals(93_000L, sheet.balance)
        assertEquals(date(2026, 11, 23), sheet.balanceDate)
    }

    @Test
    fun `una fila de traspaso se marca, pero una transferencia a un nombre no`() {
        // El banco llama «traspaso» a mover plata entre cuentas del mismo dueño; una transferencia a
        // un tercero lleva el nombre de quien la recibe. Son dos cosas distintas y sólo la primera se
        // anota como movimiento entre cuentas propias (Fase 9.6).
        val sheet = read(
            "Fecha;Descripción;Cargo;Abono\r\n" +
                "18/11/2026;Traspaso Internet de Cta.Cte.;;675.109\r\n" +
                "19/11/2026;Traspaso Internet a T. Crédito;326.759;\r\n" +
                "20/11/2026;TRANSF A SEBASTIAN BUSTOS;50.000;\r\n" +
                "21/11/2026;Transf para Pago Tarjeta CMR;214.084;\r\n" +
                "22/11/2026;MERCADO PAGO 4 TCOM;9.990;\r\n",
        )

        assertEquals(5, sheet.rows.size)
        assertTrue(sheet.rows[0].isTransfer)
        assertTrue(sheet.rows[1].isTransfer)
        // Una transferencia a un nombre puede ser un tercero: sigue siendo gasto.
        assertTrue(!sheet.rows[2].isTransfer)
        // El pago de la tarjeta desde la cuenta con la que se paga tampoco es un gasto.
        assertTrue(sheet.rows[3].isTransfer)
        // «Mercado Pago» es un comercio: la palabra «pago» sola no marca nada.
        assertTrue(!sheet.rows[4].isTransfer)
        // El traspaso no es el abono de una tarjeta: la columna de abono se marca como pago —en una
        // tarjeta lo sería— y quién tiene la cuenta decide. Lo que dice el banco con la palabra
        // «traspaso» es otra cosa, y se marca aparte.
        assertTrue(!sheet.rows[1].isPayment)
    }
}
