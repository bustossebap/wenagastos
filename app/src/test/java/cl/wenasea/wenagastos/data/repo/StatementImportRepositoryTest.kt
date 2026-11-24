package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.capture.fakes.FakeAccountDao
import cl.wenasea.wenagastos.capture.fakes.FakeAccountSnapshotDao
import cl.wenasea.wenagastos.capture.fakes.FakeCategoryDao
import cl.wenasea.wenagastos.capture.fakes.FakeMerchantRuleDao
import cl.wenasea.wenagastos.capture.fakes.FakeTransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Installments
import cl.wenasea.wenagastos.domain.Reports
import cl.wenasea.wenagastos.importexport.Csv
import cl.wenasea.wenagastos.importexport.StatementImport
import cl.wenasea.wenagastos.importexport.StatementSummary
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Importar la cartola es el camino que trae los gastos que el aviso del banco no alcanzó a contar.
 * Lo que se prueba acá es lo que no puede fallar: que no se anote dos veces lo que ya está, que un
 * gasto repetido de verdad quede dos veces y que el saldo del cierre no pise un corte más nuevo.
 */
class StatementImportRepositoryTest {

    private val movements = FakeTransactionDao()
    private val snapshots = FakeAccountSnapshotDao()
    private val accounts = AccountRepository(FakeAccountDao())
    private val categories = CategoryRepository(FakeCategoryDao())
    private val rules = MerchantRuleRepository(FakeMerchantRuleDao(), categories)

    private val repository = StatementImportRepository(
        runner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        },
        transactions = TransactionRepository(movements),
        snapshots = SnapshotRepository(snapshots),
        rules = rules,
        categories = categories,
        accounts = accounts,
    )

    private val accountId = 1L

    private fun sheet(text: String): StatementImport.Sheet =
        StatementImport.read(Csv.split(text))!!

    private fun stored() = movements.stored

    private fun push(amount: Long, direction: Direction, merchant: String, hour: Int, pending: Boolean) =
        TransactionEntity(
            accountId = accountId,
            occurredAt = Dates.startOfDay(LocalDate.of(2026, 11, 18)) + hour * 3_600_000L,
            amount = amount,
            direction = direction,
            merchantRaw = merchant,
            merchantNorm = merchant.lowercase(),
            status = if (pending) TransactionStatus.PENDING else TransactionStatus.CONFIRMED,
            isExpense = direction == Direction.DEBIT,
        )

    @Test
    fun `anota los movimientos y deja el saldo del cierre como corte`() = runBlocking {
        val report = repository.import(
            accountId,
            sheet(
                "Fecha;Descripción;Cargo;Abono;Saldo\n" +
                    "18/11/2026;LIDER EXPRESS;12.345;;1.234.567\n" +
                    "19/11/2026;SUELDO;;900.000;2.134.567\n",
            ),
            origin = "cartola-noviembre.csv",
            // Un instante después del cierre, para que la prueba no dependa del reloj del equipo.
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )

        assertEquals(2, report.added)
        assertEquals(0, report.repeated)
        assertEquals(0, report.pending)
        assertTrue(report.snapshotSaved)
        assertEquals(2_134_567L, report.balance)
        assertEquals(LocalDate.of(2026, 11, 19), report.balanceDate)

        val gasto = stored().single { it.direction == Direction.DEBIT }
        assertEquals(12_345L, gasto.amount)
        assertEquals(TransactionSource.CSV, gasto.source)
        assertEquals(TransactionStatus.CONFIRMED, gasto.status)
        assertEquals(Dates.startOfDay(LocalDate.of(2026, 11, 18)), gasto.occurredAt)
        assertNull(gasto.notes)

        val corte = snapshots.stored.single()
        assertEquals(accountId, corte.accountId)
        assertEquals(SnapshotSource.CARTOLA, corte.source)
        assertEquals(2_134_567L, corte.balance)
        assertEquals(Dates.endOfDay(LocalDate.of(2026, 11, 19)), corte.asOf)
        assertTrue(corte.note!!.contains("cartola-noviembre.csv"))
    }

    @Test
    fun `importar dos veces la misma cartola no duplica nada`() = runBlocking {
        val text = "Fecha;Descripción;Monto;Saldo\n18/11/2026;LIDER;12.345;100.000\n"
        val after = Dates.endOfDay(LocalDate.of(2026, 12, 1))
        repository.import(accountId, sheet(text), now = after)
        val second = repository.import(accountId, sheet(text), now = after)

        assertEquals(0, second.added)
        assertEquals(1, second.repeated)
        assertEquals(1, stored().size)
        assertEquals(1, snapshots.stored.size)
        assertTrue(second.describe().contains("Nada nuevo"))
    }

    @Test
    fun `la cartola completa el movimiento que el aviso dejo sin comercio`() = runBlocking {
        movements.seed(push(12_000L, Direction.CREDIT, "Transferencia recibida", hour = 20, pending = true))

        val report = repository.import(
            accountId,
            sheet("Fecha;Descripción;Monto\n18/11/2026;JUAN PEREZ;+12.000\n"),
        )

        assertEquals(0, report.added)
        assertEquals(1, report.repeated)
        assertEquals(1, report.completed)
        val completed = stored().single()
        assertEquals("JUAN PEREZ", completed.merchantRaw)
        assertEquals(TransactionStatus.CONFIRMED, completed.status)
        assertNull(completed.notes)
    }

    @Test
    fun `dos compras iguales del mismo dia quedan dos veces`() = runBlocking {
        movements.seed(push(2_500L, Direction.DEBIT, "CAFE", hour = 9, pending = false))

        val report = repository.import(
            accountId,
            sheet(
                "Fecha;Descripción;Monto\n" +
                    "18/11/2026;CAFE;2.500\n" +
                    "18/11/2026;CAFE;2.500\n",
            ),
        )

        assertEquals(1, report.added)
        assertEquals(1, report.repeated)
        assertEquals(2, stored().size)
    }

    @Test
    fun `un corte mas nuevo no se pisa con el saldo de una cartola vieja`() = runBlocking {
        snapshots.seed(
            AccountSnapshotEntity(
                accountId = accountId,
                asOf = Dates.endOfDay(LocalDate.of(2026, 11, 30)),
                balance = 999_999L,
            ),
        )

        val report = repository.import(
            accountId,
            sheet("Fecha;Descripción;Monto;Saldo\n18/11/2026;LIDER;1.000;50.000\n"),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 31)),
        )

        assertEquals(false, report.snapshotSaved)
        assertEquals(50_000L, report.balance)
        assertEquals(1, snapshots.stored.size)
        assertEquals(999_999L, snapshots.stored.single().balance)
    }

    @Test
    fun `la cartola de hoy no deja el corte en la noche`() = runBlocking {
        val at15 = Dates.startOfDay(LocalDate.of(2026, 11, 20)) + 15 * 3_600_000L

        val report = repository.import(
            accountId,
            sheet("Fecha;Descripción;Monto;Saldo\n20/11/2026;LIDER;1.000;50.000\n"),
            now = at15,
        )

        assertTrue(report.snapshotSaved)
        val corte = snapshots.stored.single()
        // Las filas de la cartola quedan dentro del corte...
        assertTrue(stored().single().occurredAt < corte.asOf)
        // ...y lo que se compre después de importarla todavía mueve el saldo.
        assertEquals(at15, corte.asOf)
    }

    @Test
    fun `reimportar la cartola del dia no deja dos cortes`() = runBlocking {
        val text = "Fecha;Descripción;Monto;Saldo\n20/11/2026;LIDER;1.000;50.000\n"
        val day = Dates.startOfDay(LocalDate.of(2026, 11, 20))

        repository.import(accountId, sheet(text), now = day + 15 * 3_600_000L)
        val second = repository.import(accountId, sheet(text), now = day + 18 * 3_600_000L)

        // El corte del día no puede correrse con cada importación: el saldo es el mismo.
        assertEquals(1, snapshots.stored.size)
        assertEquals(day + 15 * 3_600_000L, snapshots.stored.single().asOf)
        assertEquals(false, second.snapshotSaved)
        assertTrue(second.describe().contains("Nada nuevo"))
    }

    @Test
    fun `una fila sin comercio queda por revisar`() = runBlocking {
        val report = repository.import(
            accountId,
            sheet("Fecha;Descripción;Monto\n18/11/2026;;7.000\n"),
        )

        assertEquals(1, report.added)
        assertEquals(1, report.pending)
        val row = stored().single()
        assertEquals(TransactionStatus.PENDING, row.status)
        assertTrue(row.notes!!.contains("no traía el comercio"))
    }

    /** Un movimiento de la cartola del mes pasado: se quedó ahí, con su propio momento de entrada. */
    private fun previousImport(at: Long) = TransactionEntity(
        accountId = accountId,
        occurredAt = Dates.startOfDay(LocalDate.of(2026, 10, 20)),
        amount = 9_990L,
        direction = Direction.DEBIT,
        merchantRaw = "UNIMARC OCTUBRE",
        merchantNorm = "unimarc octubre",
        source = TransactionSource.CSV,
        createdAt = at,
        updatedAt = at,
    )

    @Test
    fun `deshacer la ultima cartola borra lo que anoto y el saldo que dejo`() = runBlocking {
        val monthAgo = System.currentTimeMillis() - 30 * 86_400_000L
        movements.seed(previousImport(monthAgo))
        snapshots.seed(
            AccountSnapshotEntity(
                accountId = accountId,
                asOf = monthAgo,
                balance = 1_500_000L,
                source = SnapshotSource.CARTOLA,
                createdAt = monthAgo,
            ),
        )

        repository.import(
            accountId,
            sheet(
                "Fecha;Descripción;Cargo;Abono;Saldo\n" +
                    "18/11/2026;LIDER EXPRESS;12.345;;1.234.567\n" +
                    "19/11/2026;SUELDO;;900.000;2.134.567\n",
            ),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )
        assertEquals(3, stored().size)

        val report = repository.undoLast()!!

        assertEquals(2, report.removed)
        assertEquals(0, report.kept)
        assertTrue(report.balanceForgotten)
        // La cartola del mes pasado no se toca: es otra importación.
        assertEquals("UNIMARC OCTUBRE", stored().single().merchantRaw)
        assertEquals(monthAgo, snapshots.stored.single().createdAt)
        assertTrue(report.describe().contains("Borré 2 movimientos"))
    }

    @Test
    fun `deshacer no se lleva el movimiento que se reviso despues`() = runBlocking {
        repository.import(
            accountId,
            sheet(
                "Fecha;Descripción;Monto\n" +
                    "18/11/2026;LIDER;12.345\n" +
                    "18/11/2026;JUMBO;8.000\n",
            ),
        )
        val edited = stored().first { it.merchantRaw == "JUMBO" }
        movements.update(edited.copy(merchantRaw = "Jumbo Costanera", updatedAt = edited.createdAt + 5_000))

        val report = repository.undoLast()!!

        assertEquals(1, report.removed)
        assertEquals(1, report.kept)
        assertEquals("Jumbo Costanera", stored().single().merchantRaw)
        assertTrue(report.describe().contains("Dejé 1"))
    }

    @Test
    fun `sin cartolas importadas no hay nada que deshacer`() = runBlocking {
        assertNull(repository.undoLast())
    }

    /** Una cuenta de Falabella, como la tarjeta y la cuenta corriente que comparten institución. */
    private suspend fun account(kind: AccountKind): Long = accounts.add(
        AccountEntity(
            name = if (kind == AccountKind.CREDIT) "Falabella CMR" else "Falabella Cta. Cte.",
            institution = Institution.FALABELLA,
            kind = kind,
            colorHex = Institution.FALABELLA.colorHex,
        ),
    )

    /** Lo que el estado de cuenta de una tarjeta imprime arriba, fuera de la tabla. */
    private val resumen = StatementSummary(
        debt = 700_000L,
        billed = 650_000L,
        limit = 1_500_000L,
        asOf = LocalDate.of(2026, 11, 4),
        due = LocalDate.of(2026, 11, 20),
    )

    /** El estado de cuenta de la tarjeta: una compra en cuotas y el pago, como los escribe la CMR. */
    private val estadoDeCuenta =
        "Fecha;Descripción;Monto;Cuota;Valor cuota\n" +
            "12/10/2026;Ikea;86.980;01/03;28.993\n" +
            "10/10/2026;Pago tarjeta cmr;208.960;;-208.960\n"

    /** El pago solo, como lo imprime la CMR: la plata en positivo y el signo en la columna de al lado. */
    private val pagoDelBanco =
        "Fecha;Descripción;Monto;Cuota;Valor cuota\n" +
            "10/10/2026;Pago tarjeta cmr;208.960;;-208.960\n"

    @Test
    fun `la deuda y el cupo del resumen quedan en la tarjeta`() = runBlocking {
        val id = account(AccountKind.CREDIT)

        val report = repository.import(
            id,
            sheet(estadoDeCuenta),
            origin = "estado-cuenta.pdf",
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen,
        )

        // Una tarjeta no informa saldo: el corte es lo que el banco dice que se debe, al día de
        // facturación —el cupo utilizado, que ya incluye las cuotas por facturar—.
        assertTrue(report.snapshotSaved)
        assertTrue(report.balanceFromSummary)
        assertEquals(-700_000L, report.balance)
        val corte = snapshots.stored.single()
        assertEquals(-700_000L, corte.balance)
        assertEquals(Dates.endOfDay(LocalDate.of(2026, 11, 4)), corte.asOf)
        assertEquals(SnapshotSource.CARTOLA, corte.source)
        assertTrue(corte.note!!.startsWith("Cupo utilizado al"))

        // El cupo y las fechas que la cuenta no tenía se guardan: los dijo el banco.
        val saved = accounts.get(id)!!
        assertEquals(1_500_000L, saved.creditLimit)
        assertEquals(4, saved.statementDay)
        assertEquals(20, saved.paymentDay)
        assertEquals(1_500_000L, report.learnedLimit)
        assertEquals(4, report.learnedStatementDay)
        assertEquals(20, report.learnedPaymentDay)
        // La cuenta sí es una tarjeta: el resumen se usó, no se ignora.
        assertNull(report.ignoredSummary)
    }

    @Test
    fun `el cupo y las fechas del resumen pisan lo que el usuario habia escrito`() = runBlocking {
        // El cupo lo dice el banco: el número que se escribió a mano en el diálogo es una estimación
        // y puede estar equivocada. Antes se conservaba el escrito y la tarjeta quedaba con un cupo
        // que no era el suyo, así que el disponible no calzaba con el de la página del banco.
        val id = accounts.add(
            AccountEntity(
                name = "Falabella CMR",
                institution = Institution.FALABELLA,
                kind = AccountKind.CREDIT,
                colorHex = Institution.FALABELLA.colorHex,
                creditLimit = 900_000L,
                statementDay = 15,
            ),
        )

        val report = repository.import(
            id,
            sheet(estadoDeCuenta),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen,
        )

        val saved = accounts.get(id)!!
        assertEquals(1_500_000L, saved.creditLimit)
        assertEquals(4, saved.statementDay)
        assertEquals(20, saved.paymentDay)
        assertEquals(1_500_000L, report.learnedLimit)
        assertEquals(900_000L, report.limitBefore)
        assertEquals(4, report.learnedStatementDay)
        assertEquals(15, report.statementDayBefore)
        assertEquals(20, report.learnedPaymentDay)
        assertNull(report.paymentDayBefore)
        // El reporte cuenta de cuánto a cuánto, para que el cambio no pase inadvertido.
        assertTrue(report.describe().contains("antes $900.000"))
    }

    @Test
    fun `sin cupo en el resumen se respeta el que habia escrito`() = runBlocking {
        // Un resumen sin cupo no dice nada del cupo: lo escrito se queda como estaba.
        val id = accounts.add(
            AccountEntity(
                name = "Falabella CMR",
                institution = Institution.FALABELLA,
                kind = AccountKind.CREDIT,
                colorHex = Institution.FALABELLA.colorHex,
                creditLimit = 900_000L,
            ),
        )

        val report = repository.import(
            id,
            sheet(estadoDeCuenta),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen.copy(limit = null, asOf = null, due = null),
        )

        assertEquals(900_000L, accounts.get(id)!!.creditLimit)
        assertNull(report.learnedLimit)
        assertNull(report.limitBefore)
    }

    @Test
    fun `el abono de la planilla de la tarjeta es el pago aunque no traiga resumen`() = runBlocking {
        // El Excel del banco (Monto cargo / Monto abono) no imprime cupo, así que llega sin resumen.
        // Aun así el abono de una tarjeta es el pago: si se anota como ingreso, el mes se infla con
        // plata que sólo cambió de lugar.
        val id = account(AccountKind.CREDIT)

        val report = repository.import(
            id,
            sheet(
                "Fecha;Tipo;Detalle;Monto cargo;Monto abono\n" +
                    "04/11/2026;;PAGO;;326.759\n" +
                    "07/11/2026;:COMIDAS;SUC LIRA 43;3.380;\n",
            ),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )

        assertEquals(1, report.payments)
        val pago = stored().single { it.direction == Direction.CREDIT }
        assertTrue(pago.isTransfer)
        assertNull(pago.categoryId)
        assertEquals(0L, Reports.income(stored()))
    }

    @Test
    fun `el pago de la tarjeta que viene en la cartola corriente se enlaza con el abono`() =
        runBlocking {
            // El pago se ve dos veces: sale de la cuenta corriente («Traspaso Internet a T. Crédito»)
            // y entra a la tarjeta como abono. Antes, la pata del banco quedaba como gasto del mes y
            // el abono como traspaso suelto, así que la misma plata se contaba dos veces.
            val card = account(AccountKind.CREDIT)
            val checking = account(AccountKind.CHECKING)
            val now = Dates.endOfDay(LocalDate.of(2026, 12, 1))
            repository.import(card, sheet(pagoDelBanco), now = now)
            assertNull(stored().single { it.accountId == card }.transferAccountId)

            val report = repository.import(
                checking,
                sheet(
                    "Fecha;Descripción;Cargo;Abono\n" +
                        "11/10/2026;Traspaso Internet a T. Crédito;208.960;\n",
                ),
                now = now,
            )

            assertEquals(1, report.transfers)
            assertEquals(1, report.linked)
            val pata = stored().single { it.accountId == checking }
            assertTrue(pata.isTransfer)
            assertTrue(!pata.isExpense)
            assertNull(pata.categoryId)
            // Las dos patas se apuntan y comparten grupo: la lista las lee como «cuenta → tarjeta».
            assertEquals(card, pata.transferAccountId)
            val abono = stored().single { it.accountId == card }
            assertEquals(checking, abono.transferAccountId)
            assertEquals(pata.transferGroupId, abono.transferGroupId)
            // Ni gasto ni ingreso: la plata cambió de bolsillo.
            assertEquals(0L, Reports.expenses(stored()))
            assertEquals(0L, Reports.income(stored()))
            assertTrue(report.describe().contains("Enlacé el traspaso con su otra pata"))
        }

    @Test
    fun `las dos patas del traspaso se enlazan aunque vengan en cartolas distintas`() = runBlocking {
        val ahorro = account(AccountKind.SAVINGS)
        val checking = account(AccountKind.CHECKING)
        val now = Dates.endOfDay(LocalDate.of(2026, 12, 1))

        // Primero entra una sola pata: la otra cuenta no se ha importado, así que queda suelta.
        val first = repository.import(
            checking,
            sheet(
                "Fecha;Descripción;Cargo;Abono\n" +
                    "20/11/2026;Traspaso Internet a Cta. Ahorro;40.000;\n",
            ),
            now = now,
        )
        assertEquals(1, first.transfers)
        assertEquals(0, first.linked)
        assertNull(stored().single().transferAccountId)

        // Al día siguiente se importa la otra cartola: la pata que ya estaba en la base se enlaza.
        val second = repository.import(
            ahorro,
            sheet(
                "Fecha;Descripción;Cargo;Abono\n" +
                    "21/11/2026;Traspaso Internet de Cta.Cte.;;40.000\n",
            ),
            now = now,
        )
        assertEquals(1, second.transfers)
        assertEquals(1, second.linked)
        val salida = stored().single { it.accountId == checking }
        val entrada = stored().single { it.accountId == ahorro }
        assertEquals(ahorro, salida.transferAccountId)
        assertEquals(checking, entrada.transferAccountId)
        assertEquals(salida.transferGroupId, entrada.transferGroupId)
    }

    @Test
    fun `un traspaso no se enlaza con un movimiento normal del mismo monto`() = runBlocking {
        // Una compra de $40.000 en otra cuenta no es la otra pata de nada: sólo se enlazan traspasos.
        val card = account(AccountKind.CREDIT)
        val checking = account(AccountKind.CHECKING)
        movements.seed(
            TransactionEntity(
                accountId = card,
                occurredAt = Dates.startOfDay(LocalDate.of(2026, 11, 20)),
                amount = 40_000L,
                direction = Direction.CREDIT,
                merchantRaw = "LIDER",
                merchantNorm = "lider",
                status = TransactionStatus.CONFIRMED,
                isExpense = false,
            ),
        )

        val report = repository.import(
            checking,
            sheet("Fecha;Descripción;Cargo;Abono\n20/11/2026;Traspaso Internet a Cta. Cte.;40.000;\n"),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )

        assertEquals(1, report.transfers)
        assertEquals(0, report.linked)
        assertNull(stored().single { it.accountId == checking }.transferAccountId)
        assertNull(stored().single { it.accountId == card }.transferAccountId)
    }

    @Test
    fun `una transferencia a un nombre sigue siendo un gasto`() = runBlocking {
        // «TRANSF A …» nombra a alguien: puede ser un tercero, y adivinar ahí esconde un gasto real.
        val id = account(AccountKind.CHECKING)

        val report = repository.import(
            id,
            sheet("Fecha;Descripción;Cargo;Abono\n20/11/2026;TRANSF A GASTON MENESES;50.000;\n"),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )

        assertEquals(0, report.transfers)
        val gasto = stored().single()
        assertTrue(!gasto.isTransfer)
        assertTrue(gasto.isExpense)
        assertEquals(50_000L, Reports.expenses(stored()))
    }

    @Test
    fun `un traspaso que la cartola vieja anoto como gasto se corrige al reimportar`() = runBlocking {
        val id = account(AccountKind.CHECKING)
        // Como lo dejaba la importación de antes de la Fase 9.7: un traspaso entre cuentas propias
        // anotado como gasto, con categoría y todo.
        movements.seed(
            TransactionEntity(
                accountId = id,
                occurredAt = Dates.startOfDay(LocalDate.of(2026, 11, 18)),
                amount = 675_109L,
                direction = Direction.DEBIT,
                merchantRaw = "Traspaso Internet de Cta.Cte.",
                merchantNorm = "traspaso internet de cta.cte.",
                status = TransactionStatus.CONFIRMED,
                isExpense = true,
            ),
        )
        assertEquals(675_109L, Reports.expenses(stored()))

        val report = repository.import(
            id,
            sheet("Fecha;Descripción;Cargo;Abono\n18/11/2026;Traspaso Internet de Cta.Cte.;675.109;\n"),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )

        assertEquals(0, report.added)
        assertEquals(1, report.repeated)
        assertEquals(1, report.transfers)
        val corregido = stored().single()
        assertTrue(corregido.isTransfer)
        assertTrue(!corregido.isExpense)
        assertNull(corregido.categoryId)
        // El nombre que ya estaba no se pierde.
        assertEquals("Traspaso Internet de Cta.Cte.", corregido.merchantRaw)
        assertEquals(0L, Reports.expenses(stored()))
        assertTrue(report.describe().contains("traspaso entre tus cuentas"))
    }

    @Test
    fun `el abono de una cuenta corriente sigue siendo plata que entra`() = runBlocking {
        // La misma columna, en una cuenta corriente, es un depósito: no se convierte en traspaso.
        val id = account(AccountKind.CHECKING)

        val report = repository.import(
            id,
            sheet(
                "Fecha;Descripción;Cargo;Abono\n" +
                    "18/11/2026;TRANSFERENCIA DE JUAN;;50.000\n",
            ),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
        )

        assertEquals(0, report.payments)
        val abono = stored().single()
        assertTrue(!abono.isTransfer)
        assertEquals(50_000L, Reports.income(stored()))
    }

    @Test
    fun `el pago de la tarjeta entra como traspaso y no como ingreso`() = runBlocking {
        val id = account(AccountKind.CREDIT)

        val report = repository.import(
            id,
            sheet(estadoDeCuenta),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen,
        )
        assertEquals(1, report.payments)

        val pago = stored().single { it.direction == Direction.CREDIT }
        assertEquals(208_960L, pago.amount)
        assertTrue(pago.isTransfer)
        assertNull(pago.transferAccountId)
        assertNull(pago.categoryId)
        assertTrue(!pago.isExpense)

        // El mes no se infla: la plata no se ganó, se movió de cuenta.
        assertEquals(0L, Reports.income(stored()))
        assertTrue(report.describe().contains("traspaso, no como ingreso"))

        // La compra en cuotas sigue siendo un gasto, con su cuota y su precio.
        val compra = stored().single { it.direction == Direction.DEBIT }
        assertEquals(28_993L, compra.amount)
        assertEquals(1, compra.installmentNumber)
        assertEquals(3, compra.installmentTotal)
        assertEquals(86_980L, compra.originAmount)
        assertTrue(!compra.isTransfer)
    }

    @Test
    fun `las cuotas de la cartola llegan a Cuotas por pagar`() = runBlocking {
        val id = account(AccountKind.CREDIT)

        repository.import(
            id,
            sheet(
                "Fecha;Descripción;Monto;Cuota;Valor cuota\n" +
                    "12/10/2026;Ikea;86.980;01/03;28.993\n" +
                    "02/10/2026;Protector;54.990;01/06;9.165\n",
            ),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen,
        )

        val plans = Installments.plans(stored())
        assertEquals(2, plans.size)
        // Falta lo que todavía no se facturó: dos cuotas de la de Ikea y cinco del protector.
        assertEquals(57_986L + 45_825L, plans.sumOf { it.pending })
        assertEquals(2, plans.first { it.merchant == "Ikea" }.remaining)
    }

    @Test
    fun `el resumen de una tarjeta no se aplica a una cuenta corriente`() = runBlocking {
        val id = account(AccountKind.CHECKING)

        val report = repository.import(
            id,
            sheet("Fecha;Descripción;Monto;Saldo\n18/11/2026;LIDER;12.345;100.000\n"),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen,
        )

        // El saldo de una cuenta corriente es el de su columna: el cupo de una tarjeta no dice nada
        // de la plata que hay, y sus fechas tampoco.
        assertEquals(false, report.balanceFromSummary)
        assertEquals(100_000L, report.balance)
        assertEquals(100_000L, snapshots.stored.single().balance)
        val saved = accounts.get(id)!!
        assertNull(saved.creditLimit)
        assertNull(saved.statementDay)
        assertNull(report.learnedLimit)
        assertEquals(0, report.payments)
        // El archivo imprimía la deuda: se avisa que no se anotó, en vez de callar el dato y dejar
        // al usuario con un saldo que no es el del banco.
        assertEquals(700_000L, report.ignoredSummary)
        assertTrue(report.describe().contains("no es una tarjeta"))
    }

    @Test
    fun `un pago que entro como ingreso se corrige al reimportar`() = runBlocking {
        val id = account(AccountKind.CREDIT)
        // Como lo dejaba la importación de antes de la Fase 9.6: el pago del banco entró como un
        // abono más, sin traspaso y sumando al ingreso del mes.
        movements.seed(
            TransactionEntity(
                accountId = id,
                occurredAt = Dates.startOfDay(LocalDate.of(2026, 10, 10)),
                amount = 208_960L,
                direction = Direction.CREDIT,
                merchantRaw = "Pago tarjeta cmr",
                merchantNorm = "pago tarjeta cmr",
                status = TransactionStatus.CONFIRMED,
            ),
        )
        val antes = stored().single()
        assertTrue(!antes.isTransfer)
        assertEquals(208_960L, Reports.income(stored()))

        // El estado de cuenta trae el mismo pago con el signo en el monto: se reconoce como el mismo
        // movimiento y se corrige, aunque el comercio ya estuviera puesto.
        val report = repository.import(
            id,
            sheet(pagoDelBanco),
            now = Dates.endOfDay(LocalDate.of(2026, 12, 1)),
            summary = resumen,
        )

        assertEquals(1, report.payments)
        assertEquals(0, report.completed)
        val pago = stored().single()
        assertTrue(pago.isTransfer)
        assertNull(pago.categoryId)
        assertTrue(!pago.isExpense)
        // El comercio que ya tenía no se pierde.
        assertEquals("Pago tarjeta cmr", pago.merchantRaw)
        assertEquals(0L, Reports.income(stored()))
        assertTrue(report.describe().contains("traspaso, no como ingreso"))
        // Y el corte de la tarjeta se guarda igual: la reimportación sirve para las dos cosas.
        assertTrue(report.snapshotSaved)
        assertEquals(-700_000L, report.balance)
    }
}
