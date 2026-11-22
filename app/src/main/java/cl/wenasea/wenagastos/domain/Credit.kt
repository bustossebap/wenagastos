package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * El ciclo de facturación que está corriendo en una tarjeta de crédito (Fase 7.1).
 *
 * [start] es el día de corte ya pasado, a las 00:00 de Chile: las compras desde ahí son las del
 * ciclo que todavía no se factura. [closing] es el corte que viene y [due] el vencimiento del pago
 * de esa factura, si la cuenta tiene día de pago configurado.
 */
data class CreditCycle(
    val start: Long,
    val closing: LocalDate,
    val due: LocalDate?,
)

/**
 * Lo que se sabe de una tarjeta: el ciclo en curso, lo gastado dentro de él y cuánto cupo queda.
 *
 * Los tres números tienen dueños distintos y por eso se muestran por separado: la **deuda** sale
 * del corte del banco (Fase 7, es exacta), las **compras del ciclo** salen de los movimientos que
 * llegaron a la app (pueden faltar avisos) y el **disponible** es una resta entre el cupo que el
 * usuario escribió y la deuda. Ninguno se guarda: se calculan.
 */
data class CreditUsage(
    /** `null` si la cuenta no tiene día de corte configurado. */
    val cycle: CreditCycle?,
    /** Compras confirmadas del ciclo en curso, sin traspasos. */
    val spent: Long,
    /** Lo que se debe hoy, en positivo; `null` si la cuenta no tiene saldo escrito. */
    val debt: Long?,
    /** Cupo menos deuda; `null` si falta el cupo o falta la deuda. Puede ser negativo. */
    val available: Long?,
)

/**
 * Cupo, ciclo y compras del ciclo de una tarjeta de crédito.
 *
 * El día de corte se toma como un instante a las 00:00: el ciclo va de un día de corte al
 * siguiente. El banco cierra en algún momento de ese día y no lo dice, así que una compra hecha el
 * mismo día del corte puede caer en el ciclo nuevo cuando el banco la pone en el viejo; se eligió
 * el borde simple y predecible, igual que el corte de saldo incluye su propio instante.
 */
object Credit {

    /** El día pedido, recortado al último del mes (el 31 en febrero es el 28 o el 29). */
    fun dayIn(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** El primer vencimiento que cae **después** del corte: la factura se paga después de cerrar. */
    fun dueAfter(closing: LocalDate, paymentDay: Int): LocalDate {
        val month = YearMonth.from(closing)
        val sameMonth = dayIn(month, paymentDay)
        return if (sameMonth > closing) sameMonth else dayIn(month.plusMonths(1), paymentDay)
    }

    /**
     * El ciclo en curso de la cuenta, o `null` si no es de crédito o no tiene día de corte.
     * [today] se puede pasar para las pruebas; en la app es hoy en Chile.
     */
    fun cycle(account: AccountEntity, today: LocalDate = Dates.today()): CreditCycle? {
        if (account.kind != AccountKind.CREDIT) return null
        val day = account.statementDay ?: return null
        val month = YearMonth.from(today)
        val candidate = dayIn(month, day)
        val startDate = if (candidate <= today) candidate else dayIn(month.minusMonths(1), day)
        val closing = dayIn(YearMonth.from(startDate).plusMonths(1), day)
        return CreditCycle(
            start = Dates.startOfDay(startDate),
            closing = closing,
            due = account.paymentDay?.let { dueAfter(closing, it) },
        )
    }

    /**
     * Compras del ciclo: débitos confirmados que no son traspaso.
     *
     * No es "lo facturado": la factura la emite el banco y este número sale solo de los avisos que
     * llegaron a la app. Sirve para ver cómo va el ciclo, y la deuda exacta es la del saldo.
     */
    fun spent(
        transactions: List<TransactionEntity>,
        accountId: Long,
        cycle: CreditCycle?,
    ): Long {
        if (cycle == null) return 0
        return transactions
            .filter {
                it.accountId == accountId &&
                    it.status == TransactionStatus.CONFIRMED &&
                    !it.isTransfer &&
                    it.direction == Direction.DEBIT &&
                    it.occurredAt >= cycle.start
            }
            .sumOf { it.amount }
    }

    /** Lo que la pantalla necesita de una tarjeta, con el saldo ya calculado por `Balances`. */
    fun usage(
        account: AccountEntity,
        balance: Long?,
        transactions: List<TransactionEntity>,
        today: LocalDate = Dates.today(),
    ): CreditUsage {
        if (account.kind != AccountKind.CREDIT) return CreditUsage(null, 0L, null, null)
        val cycle = cycle(account, today)
        // Un saldo a favor (positivo) no es deuda; el cupo está completo.
        val debt = balance?.let { if (it < 0) -it else 0L }
        val limit = account.creditLimit
        return CreditUsage(
            cycle = cycle,
            spent = spent(transactions, account.id, cycle),
            debt = debt,
            available = if (limit != null && debt != null) limit - debt else null,
        )
    }
}
