package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El ciclo de una tarjeta de crédito (Fase 7.1): dónde empieza, cuándo cierra, qué compras cuentan
 * y cuánto cupo queda. Las fechas de las pruebas son fijas para que el resultado no dependa del día
 * en que se corran.
 */
class CreditTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun card(
        statementDay: Int? = 25,
        paymentDay: Int? = null,
        limit: Long? = null,
        kind: AccountKind = AccountKind.CREDIT,
    ) = AccountEntity(
        id = 1,
        name = "CMR",
        institution = Institution.FALABELLA,
        kind = kind,
        colorHex = "#7CB342",
        creditLimit = limit,
        statementDay = statementDay,
        paymentDay = paymentDay,
    )

    private fun movement(
        amount: Long,
        date: LocalDate,
        direction: Direction = Direction.DEBIT,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        transfer: Boolean = false,
        accountId: Long = 1,
    ) = TransactionEntity(
        accountId = accountId,
        occurredAt = Dates.startOfDay(date),
        amount = amount,
        direction = direction,
        merchantRaw = "Tienda",
        merchantNorm = "tienda",
        status = status,
        isTransfer = transfer,
    )

    @Test
    fun `el ciclo empieza en el ultimo dia de corte ya pasado`() {
        val cycle = Credit.cycle(card(statementDay = 25), today)!!

        assertEquals(Dates.startOfDay(LocalDate.of(2026, 10, 25)), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 25), cycle.closing)
        assertNull(cycle.due)
    }

    @Test
    fun `el dia del corte ya cuenta como ciclo nuevo`() {
        val cycle = Credit.cycle(card(statementDay = 25), LocalDate.of(2026, 11, 25))!!

        assertEquals(Dates.startOfDay(LocalDate.of(2026, 11, 25)), cycle.start)
        assertEquals(LocalDate.of(2026, 12, 25), cycle.closing)
    }

    @Test
    fun `un dia de corte que no existe en el mes se recorta`() {
        val cycle = Credit.cycle(card(statementDay = 31), LocalDate.of(2026, 2, 10))!!

        assertEquals(Dates.startOfDay(LocalDate.of(2026, 1, 31)), cycle.start)
        assertEquals(LocalDate.of(2026, 2, 28), cycle.closing)
    }

    @Test
    fun `el vencimiento cae despues del corte`() {
        // Corte el 25 y pago el 5: la factura de noviembre se paga en diciembre.
        val after = Credit.cycle(card(statementDay = 25, paymentDay = 5), today)!!
        assertEquals(LocalDate.of(2026, 12, 5), after.due)

        // Pago el 30, después del corte: vence ese mismo mes.
        val sameMonth = Credit.cycle(card(statementDay = 25, paymentDay = 30), today)!!
        assertEquals(LocalDate.of(2026, 11, 30), sameMonth.due)
    }

    @Test
    fun `sin dia de corte no hay ciclo`() {
        assertNull(Credit.cycle(card(statementDay = null), today))
        assertNull(Credit.cycle(card(statementDay = 25, kind = AccountKind.CHECKING), today))
    }

    @Test
    fun `solo cuentan las compras confirmadas del ciclo`() {
        val movements = listOf(
            movement(10_000, LocalDate.of(2026, 10, 24)), // antes del corte
            movement(20_000, LocalDate.of(2026, 10, 25)), // justo en el corte: cuenta
            movement(30_000, LocalDate.of(2026, 11, 1)), // compra normal
            movement(40_000, LocalDate.of(2026, 11, 10), status = TransactionStatus.PENDING),
            movement(50_000, LocalDate.of(2026, 11, 11), status = TransactionStatus.IGNORED),
            movement(60_000, LocalDate.of(2026, 11, 12), direction = Direction.CREDIT),
            movement(70_000, LocalDate.of(2026, 11, 13), transfer = true),
            movement(80_000, LocalDate.of(2026, 11, 14), accountId = 2),
        )

        val usage = Credit.usage(card(), null, movements, today)

        assertEquals(50_000L, usage.spent)
    }

    @Test
    fun `el disponible es el cupo menos la deuda`() {
        val usage = Credit.usage(card(limit = 1_000_000), -300_000, emptyList(), today)

        assertEquals(300_000L, usage.debt)
        assertEquals(700_000L, usage.available)
    }

    @Test
    fun `un saldo a favor deja el cupo completo`() {
        val usage = Credit.usage(card(limit = 1_000_000), 50_000, emptyList(), today)

        assertEquals(0L, usage.debt)
        assertEquals(1_000_000L, usage.available)
    }

    @Test
    fun `sin saldo o sin cupo no se inventa el disponible`() {
        val withoutBalance = Credit.usage(card(limit = 1_000_000), null, emptyList(), today)
        assertNull(withoutBalance.debt)
        assertNull(withoutBalance.available)

        val withoutLimit = Credit.usage(card(limit = null), -300_000, emptyList(), today)
        assertEquals(300_000L, withoutLimit.debt)
        assertNull(withoutLimit.available)
    }

    @Test
    fun `una cuenta que no es de credito no tiene cupo`() {
        val usage = Credit.usage(
            card(kind = AccountKind.CHECKING, limit = 1_000_000),
            -1L,
            emptyList(),
            today,
        )

        assertEquals(0L, usage.spent)
        assertNull(usage.cycle)
        assertNull(usage.debt)
        assertNull(usage.available)
    }
}
