package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.capture.CategoryPick
import cl.wenasea.wenagastos.capture.MerchantQuality
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.Money
import cl.wenasea.wenagastos.importexport.StatementImport
import cl.wenasea.wenagastos.importexport.StatementSummary
import java.time.LocalDate

/**
 * Lo que dejó una importación, con los números que hacen falta para confiar en ella: cuántos
 * movimientos se anotaron, cuántos ya estaban y qué pasó con el saldo.
 */
data class StatementReport(
    val added: Int,
    /** Filas que ya estaban anotadas, por aviso o por una importación anterior. */
    val repeated: Int,
    /** De las nuevas, las que quedaron "Por revisar" porque la cartola no traía el comercio. */
    val pending: Int,
    /** Movimientos que existían a medias y la cartola completó con el nombre. */
    val completed: Int,
    val balance: Long?,
    val balanceDate: LocalDate?,
    val snapshotSaved: Boolean,
    /** Filas que no eran movimientos: títulos, totales, líneas en blanco. */
    val skipped: Int,
    /**
     * Filas que eran el pago de la tarjeta. Se anotan como traspaso de una sola pata: bajan la
     * deuda y no suman como ingreso (Fase 9.6).
     */
    val payments: Int = 0,
    /** El saldo del corte no vino de una columna del archivo sino del resumen de la tarjeta. */
    val balanceFromSummary: Boolean = false,
    /** Cifras del resumen que el archivo traía y la cuenta no tenía: quedaron guardadas. */
    val learnedLimit: Long? = null,
    val learnedStatementDay: Int? = null,
    val learnedPaymentDay: Int? = null,
    /**
     * La deuda que el resumen imprimía y no se anotó porque la cuenta no es una tarjeta. Se dice en
     * el reporte en vez de callarla: el archivo la traía.
     */
    val ignoredSummary: Long? = null,
) {

    /** El resumen que se muestra al terminar, en el mismo tono que el resto de la app. */
    fun describe(): String = buildString {
        when {
            added == 0 && repeated == 0 -> append("No encontré movimientos en esta cartola.")
            added == 0 -> append("Nada nuevo: los movimientos ya estaban anotados.")
            added == 1 -> append("Anoté 1 movimiento.")
            else -> append("Anoté $added movimientos.")
        }
        if (added > 0 && repeated > 0) {
            append(" ")
            append(if (repeated == 1) "Uno ya estaba anotado." else "$repeated ya estaban anotados.")
        }
        if (completed > 0) {
            append(" Completé el comercio de ")
            append(if (completed == 1) "1 que estaba a medias." else "$completed que estaban a medias.")
        }
        if (pending > 0) {
            append(" ")
            append(if (pending == 1) "1 quedó por revisar." else "$pending quedaron por revisar.")
        }
        if (payments > 0) {
            append(" ")
            append(
                if (payments == 1) {
                    "El pago de la tarjeta quedó como traspaso, no como ingreso."
                } else {
                    "Los $payments pagos de la tarjeta quedaron como traspaso, no como ingreso."
                },
            )
        }
        if (skipped > 0) {
            append(" Salteé ")
            append(if (skipped == 1) "1 fila" else "$skipped filas")
            append(" que no eran movimientos.")
        }
        append(" ")
        if (balanceFromSummary && balance != null) {
            // Una tarjeta no informa saldo: la deuda la dice el resumen del banco, y es la que
            // deja el corte.
            append("El banco dice que la tarjeta debe ${Money.format(-balance)}. ")
            append(
                if (snapshotSaved) {
                    "Quedó como corte de la cuenta."
                } else {
                    "Ese corte no es más nuevo que el que ya tenía."
                },
            )
        } else {
            append(
                when {
                    balance == null -> "La cartola no traía saldo."
                    snapshotSaved -> "El saldo que informa quedó como corte de la cuenta."
                    else -> "El saldo que informa no es más nuevo que el corte que ya tenía la cuenta."
                },
            )
        }
        if (ignoredSummary != null) {
            append(" El resumen dice que se deben ${Money.format(ignoredSummary)}")
            append(", pero esta cuenta no es una tarjeta: no lo anoté como su saldo.")
        }
        if (learnedLimit != null) {
            append(" El cupo de la tarjeta quedó en ${Money.format(learnedLimit)}.")
        }
        if (learnedStatementDay != null) {
            append(" Anoté que la facturación corta el día $learnedStatementDay.")
        }
        if (learnedPaymentDay != null) {
            append(" La factura vence el día $learnedPaymentDay.")
        }
    }
}

/**
 * Lo que quedó al deshacer una importación: cuántos movimientos se borraron, cuántos se dejaron
 * porque el usuario los había revisado, y si el corte de saldo de esa cartola también se olvidó.
 */
data class UndoReport(
    val removed: Int,
    /** Los que se quedan: la cartola los anotó, pero después alguien los corrigió. */
    val kept: Int,
    val balanceForgotten: Boolean,
) {

    fun describe(): String = buildString {
        when {
            removed == 0 -> append("No borré ningún movimiento.")
            removed == 1 -> append("Borré 1 movimiento de la última cartola.")
            else -> append("Borré $removed movimientos de la última cartola.")
        }
        if (kept > 0) {
            append(" ")
            append(if (kept == 1) "Dejé 1 que revisaste" else "Dejé $kept que revisaste")
            append(": eso ya no es lo que decía el archivo.")
        }
        append(if (balanceForgotten) " También olvidé el saldo de cierre que traía." else "")
    }
}

/**
 * Escribe en la base lo que [StatementImport] leyó de la cartola.
 *
 * Tres reglas, todas para que importar dos veces el mismo mes no ensucie nada:
 * 1. Un movimiento que ya está —porque llegó como aviso del banco— no se anota dos veces; la
 *    cartola solo lo completa si le faltaba el comercio.
 * 2. El mismo gasto repetido en el archivo (dos cafés de $2.500 el mismo día) se anota dos veces:
 *    cada fila busca su propio parecido y no reusa el que ya calzó con otra fila.
 * 3. El saldo del cierre entra como corte de la cuenta (`SnapshotSource.CARTOLA`), pero solo si es
 *    más nuevo que el que la cuenta ya tenía: un corte viejo no puede pisar uno nuevo.
 *
 * La Fase 9.6 le agregó lo propio de una **tarjeta**: el estado de cuenta no informa saldo, pero sí
 * cuánto se debe y de cuánto es el cupo. La deuda del resumen entra como corte de la tarjeta, las
 * cifras que la cuenta no tenía se guardan en ella, y el pago de la tarjeta se anota como traspaso
 * en vez de ingreso.
 */
class StatementImportRepository(
    private val runner: TransactionRunner,
    private val transactions: TransactionRepository,
    private val snapshots: SnapshotRepository,
    private val rules: MerchantRuleRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {

    /**
     * @param origin de dónde salió la cartola (el nombre del archivo), para que el corte de saldo
     * diga de dónde vino. Opcional.
     * @param now el instante de la importación, para recortar el corte de un archivo del día en
     * curso: el cierre de hoy todavía no llegó.
     * @param summary lo que el estado de cuenta imprime fuera de la tabla (cupo, facturado, deuda).
     * Sólo lo trae el PDF de una tarjeta, y sólo se usa si la cuenta es de crédito: en otra cuenta
     * el dato se ignora y el reporte lo dice.
     */
    suspend fun import(
        accountId: Long,
        sheet: StatementImport.Sheet,
        origin: String? = null,
        now: Long = System.currentTimeMillis(),
        summary: StatementSummary? = null,
    ): StatementReport = runner.run {
        // El resumen de la tarjeta sólo sirve en una tarjeta: en una cuenta corriente el saldo es
        // el de su columna, y un cupo no dice nada de la plata que hay.
        val account = accounts.get(accountId)
        val card = summary?.takeIf { account?.kind == AccountKind.CREDIT && !it.isEmpty }
        // El resumen de una tarjeta en una cuenta que no es tarjeta: el archivo lo traía, así que se
        // dice en el reporte en vez de callarlo (una cuenta corriente no tiene cupo ni facturación).
        val ignoredSummary = summary
            ?.let { it.debt ?: it.billed }
            ?.takeIf { account?.kind != AccountKind.CREDIT }
        var added = 0
        var repeated = 0
        var pending = 0
        var completed = 0
        var payments = 0
        val claimed = mutableSetOf<Long>()
        val pools = mutableMapOf<PoolKey, List<TransactionEntity>>()

        // En orden: la fila más vieja se queda con el movimiento más viejo. Si el archivo viniera
        // al revés, la de hoy podría reclamar el aviso de ayer y dejar a la de ayer sin pareja.
        for (row in sheet.rows.sortedBy { it.date }) {
            // El pago de la tarjeta no es un ingreso: es plata que llega para bajar la deuda. Se
            // anota como traspaso de una sola pata —la mecánica que la app ya tiene para «esto vino
            // de otra cuenta»—, así mueve el saldo y no suma en gastos ni en ingresos. Sin esto,
            // pagar la tarjeta aparecía como ingreso del mes y el gasto original seguía contado: el
            // mes decía que se había ganado plata que sólo cambió de lugar.
            val payment = card != null && row.isPayment
            val at = Dates.startOfDay(row.date)
            val key = PoolKey(at, row.amount, row.direction)
            val pool = pools.getOrPut(key) {
                transactions.findAllNearbyIn(accountId, row.amount, row.direction, at, CARTOLA_WINDOW)
            }
            val existing = pool.firstOrNull { it.id !in claimed }
            if (existing != null) {
                claimed += existing.id
                repeated++
                val filled = complete(existing, row, payment)
                if (filled != null) {
                    transactions.save(filled)
                    // Un pago que se corrige no es un comercio que se completa: el reporte los
                    // cuenta aparte para poder decir que esa plata no era un ingreso.
                    if (payment && !existing.isTransfer) payments++ else completed++
                }
                continue
            }
            val known = row.merchant.isNotEmpty() && !MerchantQuality.isGeneric(row.merchant)
            transactions.save(
                TransactionEntity(
                    accountId = accountId,
                    occurredAt = at,
                    amount = row.amount,
                    direction = row.direction,
                    merchantRaw = row.merchant,
                    merchantNorm = row.merchant.lowercase(),
                    categoryId = if (payment) {
                        null
                    } else {
                        CategoryPick.resolve(
                            rules = rules,
                            categories = categories,
                            merchant = row.merchant,
                            direction = row.direction,
                            rawText = row.rawText,
                            merchantKnown = known,
                        )
                    },
                    status = if (known) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
                    isExpense = row.direction == Direction.DEBIT && !payment,
                    // Traspaso con una sola pata: la cuenta de la que salió el pago no está en este
                    // archivo, y `transferAccountId` queda en `null` hasta que alguien la diga.
                    isTransfer = payment,
                    source = TransactionSource.CSV,
                    // Fase 9.3: la cuota y el precio de la compra, para saber cuánto falta pagar.
                    installmentNumber = row.installmentNumber,
                    installmentTotal = row.installmentTotal,
                    originAmount = row.originAmount,
                    rawText = row.rawText,
                    notes = if (known) null else MISSING_MERCHANT,
                ),
            )
            added++
            if (payment) payments++
            if (!known) pending++
        }

        var snapshotSaved = false
        // Una tarjeta no informa saldo: lo que debe lo dice su resumen —el cupo utilizado, que ya
        // incluye las cuotas que todavía no se facturan— y, si el banco no lo imprime, la factura
        // del mes. El corte va a la fecha de facturación, que es a la que corresponden esas cifras.
        val cardDebt = card?.let { it.debt ?: it.billed }
        val balance = sheet.balance ?: cardDebt?.let { -it }
        val balanceFromSummary = sheet.balance == null && cardDebt != null
        val balanceDate = sheet.balanceDate
            ?: card?.asOf
            ?: if (cardDebt != null) sheet.rows.maxOfOrNull { it.date } else null
        if (balance != null && balanceDate != null) {
            // El saldo es del cierre del día, no del comienzo: un corte a las 00:00 dejaría afuera
            // los movimientos de esa misma jornada. Pero el cierre de hoy todavía no llegó: la
            // cartola del día en curso se clava en este instante, así lo que se compre más tarde
            // ese día sí mueve el saldo (y un saldo escrito a mano después no queda tapado por una
            // fecha futura).
            val asOf = minOf(Dates.endOfDay(balanceDate), now)
            val latest = snapshots.latest(accountId)
            // Volver a importar el mismo archivo de hoy no puede dejar dos cortes del mismo día:
            // el primero quedó clavado en la hora en que se importó, y el segundo caería más
            // adelante con el mismo saldo. Con el mismo saldo y el mismo día, el corte ya está.
            val already = latest != null &&
                latest.source == SnapshotSource.CARTOLA &&
                latest.balance == balance &&
                Dates.toLocalDate(latest.asOf) == balanceDate
            if (!already && (latest == null || latest.asOf < asOf)) {
                snapshots.add(
                    AccountSnapshotEntity(
                        accountId = accountId,
                        asOf = asOf,
                        balance = balance,
                        source = SnapshotSource.CARTOLA,
                        note = buildString {
                            // Una tarjeta no tiene «cierre de saldo»: lo que queda anotado es lo
                            // que el banco dice que se debe a esa fecha.
                            append(if (balanceFromSummary) "Cupo utilizado al " else "Cierre del ")
                            append(Dates.fullDateLabel(asOf))
                            if (!origin.isNullOrBlank()) {
                                append(" · ")
                                append(origin.take(60))
                            }
                        },
                    ),
                )
                snapshotSaved = true
            }
        }

        // El resumen trae también el cupo y las fechas de facturación y de pago. Se guardan sólo si
        // la cuenta no los tenía: lo que el usuario escribió en el diálogo manda.
        var learnedLimit: Long? = null
        var learnedStatementDay: Int? = null
        var learnedPaymentDay: Int? = null
        if (card != null && account != null) {
            val limit = card.limit?.takeIf { it > 0 }
            val statementDay = card.asOf?.dayOfMonth
            val paymentDay = card.due?.dayOfMonth
            val updated = account.copy(
                creditLimit = account.creditLimit ?: limit,
                statementDay = account.statementDay ?: statementDay,
                paymentDay = account.paymentDay ?: paymentDay,
            )
            if (updated != account) {
                accounts.update(updated)
                if (account.creditLimit == null) learnedLimit = limit
                if (account.statementDay == null) learnedStatementDay = statementDay
                if (account.paymentDay == null) learnedPaymentDay = paymentDay
            }
        }

        StatementReport(
            added = added,
            repeated = repeated,
            pending = pending,
            completed = completed,
            balance = balance,
            balanceDate = balanceDate,
            snapshotSaved = snapshotSaved,
            skipped = sheet.skipped,
            payments = payments,
            balanceFromSummary = balanceFromSummary,
            learnedLimit = learnedLimit,
            learnedStatementDay = learnedStatementDay,
            learnedPaymentDay = learnedPaymentDay,
            ignoredSummary = ignoredSummary,
        )
    }

    /**
     * Completa un movimiento que quedó a medias: el aviso del banco dijo "te transfirieron
     * $12.000" y la cartola dice quién fue. Nunca pisa un nombre que ya estaba, y a un traspaso no
     * lo toca: esos no llevan comercio.
     *
     * El segundo arreglo no es del comercio sino de la plata: un pago de la tarjeta que quedó
     * anotado como ingreso —lo escribió el aviso del banco, o una importación de antes de la Fase
     * 9.6— se convierte en traspaso aunque no haya nada más que completar. Reimportar el estado de
     * cuenta es justamente el momento de corregirlo: esa plata no se ganó, cambió de lugar.
     */
    private suspend fun complete(
        existing: TransactionEntity,
        row: StatementImport.Row,
        payment: Boolean,
    ): TransactionEntity? {
        if (existing.isTransfer) return null
        // Un pago se corrige siempre —arriba ya se vio que este movimiento no es traspaso—; el
        // comercio, sólo si al movimiento le faltaba uno.
        val merchant = row.merchant.takeIf { it.isNotEmpty() && !MerchantQuality.isGeneric(it) }
        val learned = merchant?.takeIf { MerchantQuality.isGeneric(existing.merchantRaw) }
        if (!payment && learned == null) return null
        return existing.copy(
            merchantRaw = learned ?: existing.merchantRaw,
            merchantNorm = learned?.lowercase() ?: existing.merchantNorm,
            // La cuota la sabe la cartola y el aviso del banco no: si la fila la trae, se anota.
            installmentNumber = row.installmentNumber ?: existing.installmentNumber,
            installmentTotal = row.installmentTotal ?: existing.installmentTotal,
            originAmount = row.originAmount ?: existing.originAmount,
            // El pago de la tarjeta queda como traspaso y sin categoría: lo que dice la cartola es
            // que esa plata entró a la tarjeta, no que fuera un gasto ni un ingreso.
            categoryId = if (payment) {
                null
            } else {
                existing.categoryId ?: CategoryPick.resolve(
                    rules = rules,
                    categories = categories,
                    merchant = row.merchant,
                    direction = row.direction,
                    rawText = row.rawText,
                )
            },
            isTransfer = payment,
            isExpense = existing.isExpense && !payment,
            // Lo que no se aprendió se deja como estaba: un pago que sólo se convierte en traspaso
            // no gana un comercio que el banco no escribió, ni borra la nota de que falta.
            status = if (learned != null) TransactionStatus.CONFIRMED else existing.status,
            notes = if (learned != null) null else existing.notes,
            rawText = existing.rawText ?: row.rawText,
        )
    }

    /**
     * Deshace la última cartola importada: borra los movimientos que anotó y olvida el corte de
     * saldo que dejó, todo dentro de una transacción para que no quede medio deshecho.
     *
     * La importación no se recuerda como tal, así que se reconoce por el momento en que escribió:
     * todas sus filas comparten `createdAt` (se insertan de corrido) y el corte se guarda al final,
     * en el mismo instante. Por eso alcanza con mirar hacia atrás [IMPORT_WINDOW] desde la última
     * fila de cartola.
     *
     * Devuelve `null` cuando no hay ninguna importación que deshacer.
     */
    suspend fun undoLast(): UndoReport? = runner.run {
        val movement = transactions.lastImported()
        val snapshot = snapshots.lastImported()
        val lastMovementAt = movement?.createdAt ?: 0L
        val lastSnapshotAt = snapshot?.createdAt ?: 0L
        val anchor = maxOf(lastMovementAt, lastSnapshotAt)
        if (anchor == 0L) return@run null

        val accountId = if (lastSnapshotAt > lastMovementAt) {
            snapshot!!.accountId
        } else {
            movement!!.accountId
        }
        val since = anchor - IMPORT_WINDOW

        val removed = transactions.deleteImportedSince(accountId, since)
        val kept = transactions.countEditedSince(accountId, since)
        var balanceForgotten = false
        if (snapshot != null && snapshot.accountId == accountId && snapshot.createdAt >= since) {
            snapshots.delete(snapshot.id)
            balanceForgotten = true
        }
        if (removed == 0 && !balanceForgotten) null else UndoReport(removed, kept, balanceForgotten)
    }

    /** Una fila y los movimientos que compiten por ella: mismo día, monto y sentido. */
    private data class PoolKey(val dayStart: Long, val amount: Long, val direction: Direction)

    private companion object {
        /**
         * Las filas de una importación se escriben una detrás de otra: un minuto de margen cubre
         * una cartola de cientos de movimientos sin alcanzar la importación del mes anterior.
         */
        const val IMPORT_WINDOW = 60 * 1000L

        /**
         * La cartola dice el día; el aviso del banco trae la hora del aviso, que puede ser la tarde
         * anterior o la mañana siguiente (compras que el banco registra al día siguiente). Una
         * ventana de día y medio alrededor del comienzo del día cubre los dos casos sin alcanzar la
         * compra de la semana pasada, que es lo que importa: un parecido de más se lleva una fila
         * que había que anotar.
         */
        const val CARTOLA_WINDOW = 36 * 60 * 60_000L

        const val MISSING_MERCHANT = "La cartola no traía el comercio. Revisa y confirma."
    }
}
