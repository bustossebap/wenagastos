package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus

/**
 * Saldo de una cuenta: el último corte que se conoce **más** lo confirmado después de ese corte.
 *
 * No se suma el historial completo a propósito. Los movimientos de la app empiezan el día que se
 * instaló (y los avisos que no llegaron nunca están), así que sumarlos daría un número que no es
 * la plata que hay. Un corte, en cambio, lo dice el banco: lo de antes ya está dentro.
 */
data class AccountBalance(
    val account: AccountEntity,
    /** El corte vigente; `null` si la cuenta nunca tuvo uno. */
    val snapshot: AccountSnapshotEntity?,
    /** Lo que se movió después del corte, con signo. Cero si no hay corte. */
    val delta: Long,
    /** Cuántos movimientos entraron en [delta]. */
    val movements: Int,
) {
    /** `null` cuando no hay ningún corte: el saldo no se sabe, no es cero. */
    val balance: Long? get() = snapshot?.balance?.plus(delta)

    val known: Boolean get() = snapshot != null
}

/** Las cuentas de un mismo banco, con su total. */
data class InstitutionBalance(
    val institution: Institution,
    val accounts: List<AccountBalance>,
) {
    /** Suma de los saldos conocidos (los traspasos entre bancos se compensan entre filas). */
    val total: Long get() = accounts.sumOf { it.balance ?: 0L }

    val known: Int get() = accounts.count { it.known }

    val unknown: Int get() = accounts.size - known
}

/**
 * Las cuentas por banco, para la tarjeta del inicio y el detalle de saldos.
 *
 * **Los traspasos sí cuentan acá.** En los gastos del mes no, porque la plata no se gastó; pero
 * para el saldo de una cuenta la plata se fue de verdad. Al sumar todos los bancos las dos patas
 * se anulan solas, siempre que las dos estén registradas en cuentas propias.
 */
object Balances {

    /** El corte más nuevo de cada cuenta: gana la fecha y, si empatan, el id más alto. */
    fun latestByAccount(snapshots: List<AccountSnapshotEntity>): Map<Long, AccountSnapshotEntity> =
        snapshots
            .groupBy { it.accountId }
            .mapValues { (_, list) -> list.maxWith(compareBy({ it.asOf }, { it.id })) }

    /** Débito resta, crédito suma. Es lo que hace el banco en la cuenta. */
    fun signed(transaction: TransactionEntity): Long =
        if (transaction.direction == Direction.CREDIT) transaction.amount else -transaction.amount

    /**
     * El saldo de una cuenta. Los movimientos *Por revisar* y los *Ignorados* no mueven nada: el
     * primero todavía no se sabe si ocurrió en esta cuenta y el segundo el usuario lo descartó.
     */
    fun accountBalance(
        account: AccountEntity,
        snapshot: AccountSnapshotEntity?,
        transactions: List<TransactionEntity>,
    ): AccountBalance {
        if (snapshot == null) return AccountBalance(account, null, 0L, 0)
        val after = transactions.filter {
            it.accountId == account.id &&
                it.status == TransactionStatus.CONFIRMED &&
                it.occurredAt > snapshot.asOf
        }
        return AccountBalance(
            account = account,
            snapshot = snapshot,
            delta = after.sumOf(::signed),
            movements = after.size,
        )
    }

    /** Una fila por cuenta activa, en el orden en que se crearon. Una archivada ya no suma. */
    fun accounts(
        accounts: List<AccountEntity>,
        snapshots: List<AccountSnapshotEntity>,
        transactions: List<TransactionEntity>,
    ): List<AccountBalance> {
        val latest = latestByAccount(snapshots)
        return accounts
            .filter { it.isActive }
            .sortedBy { it.id }
            .map { account -> accountBalance(account, latest[account.id], transactions) }
    }

    /** Todo agrupado por banco, en el orden del enum: el inicio no cambia de orden solo. */
    fun institutions(
        accounts: List<AccountEntity>,
        snapshots: List<AccountSnapshotEntity>,
        transactions: List<TransactionEntity>,
    ): List<InstitutionBalance> {
        val byInstitution = this.accounts(accounts, snapshots, transactions)
            .groupBy { it.account.institution }
        return Institution.entries.mapNotNull { institution ->
            val group = byInstitution[institution] ?: return@mapNotNull null
            InstitutionBalance(institution, group)
        }
    }

    /**
     * Gasto del mes de una cuenta: débitos confirmados que no son traspaso. Es el mismo criterio
     * de `Reports`, así el detalle de una cuenta no dice algo distinto que Reportes.
     */
    fun monthSpend(transactions: List<TransactionEntity>, accountId: Long): Long =
        transactions
            .filter {
                it.accountId == accountId &&
                    it.status == TransactionStatus.CONFIRMED &&
                    !it.isTransfer &&
                    it.direction == Direction.DEBIT
            }
            .sumOf { it.amount }

    /** Gasto del mes por cuenta, para no repetir el filtro en la vista. */
    fun monthSpendByAccount(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
    ): Map<Long, Long> = accounts.associate { it.id to monthSpend(transactions, it.id) }

    /**
     * Gasto fijo del mes por cuenta: las cuotas de tarjeta.
     *
     * Hoy «fijo» es la cuota, que es lo que se repite solo. La naturaleza FIJO de la categoría
     * (Fase 9.5) todavía no entra en este corte: estas funciones no reciben las categorías.
     */
    fun fixedExpensesByAccount(transactions: List<TransactionEntity>, accountId: Long): Long =
        transactions
            .filter { it.accountId == accountId && it.status == TransactionStatus.CONFIRMED }
            .filter { it.direction == Direction.DEBIT }
            .filter { it.installmentTotal != null }
            .sumOf { it.amount }

    /** Gasto variable del mes por cuenta: débitos sueltos, ni cuota ni traspaso. */
    fun variableExpensesByAccount(transactions: List<TransactionEntity>, accountId: Long): Long =
        transactions
            .filter { it.accountId == accountId && it.status == TransactionStatus.CONFIRMED }
            .filter { it.direction == Direction.DEBIT }
            .filter { it.installmentTotal == null }
            .filter { !it.isTransfer }
            .sumOf { it.amount }

    /**
     * Ahorro del mes por cuenta: la pata que **sale** de un traspaso a otra cuenta propia.
     *
     * Sólo el débito: el traspaso tiene dos patas y sumar las dos diría el doble de lo que se
     * movió. Limitación conocida: el pago de la tarjeta también es un traspaso y hoy cae acá,
     * porque separarlo pide mirar el tipo de la cuenta de destino.
     */
    fun savingsByAccount(transactions: List<TransactionEntity>, accountId: Long): Long =
        transactions
            .filter { it.accountId == accountId && it.status == TransactionStatus.CONFIRMED }
            .filter { it.direction == Direction.DEBIT }
            .filter { it.isTransfer }
            .sumOf { it.amount }
}
