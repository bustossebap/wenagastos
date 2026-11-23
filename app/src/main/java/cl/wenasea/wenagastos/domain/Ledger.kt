package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity

/**
 * Mirar el mes a través de una cuenta (Fase 9.2).
 *
 * Es el mismo mes de siempre, recortado: no hay una pantalla aparte para una cuenta, porque la
 * pregunta es la misma («¿qué pasó este mes?») y lo único que cambia es el dueño de la respuesta.
 * El recorte es en memoria a propósito: el mes ya llegó entero con una sola consulta y así el
 * hero, la lista y el vacío no pueden decir cosas distintas.
 */
object Ledger {

    /** Los movimientos de [accountId], o todos cuando es `null`. Conserva el orden que traían. */
    fun scope(transactions: List<TransactionEntity>, accountId: Long?): List<TransactionEntity> =
        if (accountId == null) transactions else transactions.filter { it.accountId == accountId }

    /**
     * Las cuentas que se pueden elegir en el filtro. Una archivada queda fuera: ya no recibe
     * movimientos nuevos, así que su chip solo llevaría a una lista vieja.
     */
    fun selectable(accounts: Collection<AccountEntity>): List<AccountEntity> =
        accounts.filter { it.isActive }.sortedBy { it.id }
}
