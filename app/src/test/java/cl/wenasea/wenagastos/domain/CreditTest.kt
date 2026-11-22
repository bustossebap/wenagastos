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
 * El ciclo de una tarjeta de crédito (Fase 7.1): dónde empieza, cuándo cierra, qué compras cuentan,
 * cuánto cupo queda y cómo se leen los tres campos del diálogo. Las fechas de las pruebas son fijas
 * para que el resultado no dependa del día en que se corran.
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
    fun `la deuda escrita en la tarjeta se guarda como saldo negativo`() {
        assertEquals(-200_000L, Credit.balanceFromDebt("200000"))
        assertEquals(-200_000L, Credit.balanceFromDebt("200.000"))
        // El diálogo viejo pedía el signo: el monto no cambia y no se resta dos veces.
        assertEquals(-200_000L, Credit.balanceFromDebt("-200.000"))
        assertEquals(0L, Credit.balanceFromDebt("0"))
        assertNull(Credit.balanceFromDebt(""))
        assertNull(Credit.balanceFromDebt("abc"))
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

    @Test
    fun `lee el cupo y los dias de la tarjeta`() {
        val parsed = Credit.parse(AccountKind.CREDIT, "1.500.000", "25", "5")

        assertNull(parsed.error)
        assertEquals(1_500_000L, parsed.fields.limit)
        assertEquals(25, parsed.fields.statementDay)
        assertEquals(5, parsed.fields.paymentDay)
    }

    @Test
    fun `los campos vacios quedan sin configurar y no son cero`() {
        val parsed = Credit.parse(AccountKind.CREDIT, "", "  ", "")

        assertNull(parsed.error)
        assertNull(parsed.fields.limit)
        assertNull(parsed.fields.statementDay)
        assertNull(parsed.fields.paymentDay)
    }

    @Test
    fun `un cupo de cero o negativo no se acepta`() {
        assertEquals(
            "El cupo va en pesos y es mayor que cero",
            Credit.parse(AccountKind.CREDIT, "0", "", "").error,
        )
        assertEquals(
            "El cupo no puede ser negativo",
            Credit.parse(AccountKind.CREDIT, "-50.000", "", "").error,
        )
        // Un texto sin dígitos no configura nada: no es un cupo de cero.
        assertNull(Credit.parse(AccountKind.CREDIT, "abc", "", "").error)
    }

    @Test
    fun `los dias van del 1 al 31`() {
        assertEquals(
            "El día de corte va del 1 al 31",
            Credit.parse(AccountKind.CREDIT, "", "32", "").error,
        )
        assertEquals(
            "El día de pago va del 1 al 31",
            Credit.parse(AccountKind.CREDIT, "", "25", "0").error,
        )
        // El 31 existe aunque no todos los meses lo tengan: el ciclo lo recorta al usarlo.
        val last = Credit.parse(AccountKind.CREDIT, "", "31", "31")
        assertNull(last.error)
        assertEquals(31, last.fields.statementDay)
    }

    @Test
    fun `una cuenta que no es de credito ignora el cupo y el ciclo`() {
        val parsed = Credit.parse(AccountKind.CHECKING, "1.500.000", "25", "5")

        assertNull(parsed.error)
        assertNull(parsed.fields.limit)
        assertNull(parsed.fields.statementDay)
        assertNull(parsed.fields.paymentDay)
    }
}
