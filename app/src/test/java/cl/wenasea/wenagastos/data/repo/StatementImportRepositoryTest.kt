package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.capture.fakes.FakeAccountSnapshotDao
import cl.wenasea.wenagastos.capture.fakes.FakeCategoryDao
import cl.wenasea.wenagastos.capture.fakes.FakeMerchantRuleDao
import cl.wenasea.wenagastos.capture.fakes.FakeTransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.importexport.Csv
import cl.wenasea.wenagastos.importexport.StatementImport
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
}
