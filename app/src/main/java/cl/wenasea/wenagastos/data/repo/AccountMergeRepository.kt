package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.data.local.dao.AccountDao
import cl.wenasea.wenagastos.data.local.dao.AccountSnapshotDao
import cl.wenasea.wenagastos.data.local.dao.TransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.domain.parseNumbers

/** Lo que la fusión va a tocar, para pedir confirmación con los números a la vista. */
data class MergePlan(
    val keep: AccountEntity,
    val merge: AccountEntity,
    /** Movimientos que pasan a [keep]. */
    val movements: Int,
    /** Traspasos entre las dos: se borran porque dejan de tener sentido. */
    val transfers: Int,
) {
    fun describe(): String = buildString {
        append("«${merge.name}» pasa a ser parte de «${keep.name}». ")
        if (movements > 0) {
            append("Se mueven ")
            append(if (movements == 1) "1 movimiento" else "$movements movimientos")
            append(" a la cuenta que se queda. ")
        } else {
            append("No tiene movimientos que mover. ")
        }
        if (transfers > 0) {
            append("Se borran ")
            append(if (transfers == 1) "1 traspaso interno" else "$transfers traspasos internos")
            append(" entre las dos, que al unirlas dejan de tener sentido. ")
        }
        if (parseNumbers(merge.cardLast4).isNotEmpty()) {
            append("Sus números de tarjeta pasan a la cuenta que se queda. ")
        }
        append("«${merge.name}» desaparece.")
    }
}

/**
 * Ejecuta un bloque como una sola transacción. En la app es `database.withTransaction`; en las
 * pruebas, un pasamanos. Existe para poder probar la fusión sin abrir SQLite.
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

/**
 * Une dos cuentas en una sola: la tarjeta de débito pasa a ser un número más de la cuenta
 * corriente, que es lo que de verdad ocurre en el banco (la plata es la misma).
 *
 * Todo ocurre dentro de una transacción de Room: o queda todo movido o no se toca nada. El orden
 * importa: primero se borran los traspasos entre ambas (si no, después de mover las patas
 * quedarían apuntándose a sí mismas), y recién entonces se mueven movimientos y patas.
 */
class AccountMergeRepository(
    private val runner: TransactionRunner,
    private val accounts: AccountDao,
    private val movements: TransactionDao,
    private val snapshots: AccountSnapshotDao,
) {

    /**
     * Comprueba que las dos cuentas puedan ser una y resume lo que cambiaría. Lanza
     * [IllegalArgumentException] con un mensaje pensado para mostrar tal cual.
     */
    suspend fun plan(keepId: Long, mergeId: Long): MergePlan {
        if (keepId == mergeId) throw IllegalArgumentException("Son la misma cuenta")
        val keep = accounts.getById(keepId)
            ?: throw IllegalArgumentException("La cuenta que se queda ya no existe")
        val merge = accounts.getById(mergeId)
            ?: throw IllegalArgumentException("La cuenta que se une ya no existe")
        if (keep.institution != merge.institution) {
            throw IllegalArgumentException("Solo se pueden unir cuentas del mismo banco")
        }
        if ((keep.kind == AccountKind.CREDIT) != (merge.kind == AccountKind.CREDIT)) {
            throw IllegalArgumentException("Una tarjeta de crédito no se une con una cuenta")
        }
        return MergePlan(
            keep = keep,
            merge = merge,
            movements = accounts.countMovements(mergeId),
            transfers = movements.countTransfersBetween(keepId, mergeId),
        )
    }

    /** Ejecuta la fusión descrita por [plan]. */
    suspend fun merge(plan: MergePlan) {
        val keepId = plan.keep.id
        val mergeId = plan.merge.id
        runner.run {
            movements.deleteTransfersBetween(keepId, mergeId)
            val now = System.currentTimeMillis()
            movements.moveMovements(mergeId, keepId, now)
            movements.moveTransferLegs(mergeId, keepId, now)
            // El saldo es de la cuenta: si la tarjeta tenía un corte propio, ahora es de la cuenta
            // que se queda. El corte más nuevo de las dos es el que manda.
            snapshots.moveAccount(mergeId, keepId)

            val cards = (parseNumbers(plan.keep.cardLast4) + parseNumbers(plan.merge.cardLast4))
                .distinct()
            accounts.update(
                plan.keep.copy(
                    accountLast4 = plan.keep.accountLast4 ?: plan.merge.accountLast4,
                    cardLast4 = cards.joinToString(",").takeIf { it.isNotEmpty() },
                    isActive = plan.keep.isActive || plan.merge.isActive,
                    // El cupo y el ciclo son de la tarjeta: si la que se queda no los tenía, se
                    // toman de la otra antes de que desaparezca.
                    creditLimit = plan.keep.creditLimit ?: plan.merge.creditLimit,
                    statementDay = plan.keep.statementDay ?: plan.merge.statementDay,
                    paymentDay = plan.keep.paymentDay ?: plan.merge.paymentDay,
                ),
            )
            accounts.deleteById(mergeId)
        }
    }
}
