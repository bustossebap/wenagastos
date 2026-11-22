package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.capture.CategoryPick
import cl.wenasea.wenagastos.capture.MerchantQuality
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.importexport.StatementImport
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
        if (skipped > 0) {
            append(" Salteé ")
            append(if (skipped == 1) "1 fila" else "$skipped filas")
            append(" que no eran movimientos.")
        }
        append(" ")
        append(
            when {
                balance == null -> "La cartola no traía saldo."
                snapshotSaved -> "El saldo que informa quedó como corte de la cuenta."
                else -> "El saldo que informa no es más nuevo que el corte que ya tenía la cuenta."
            },
        )
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
 */
class StatementImportRepository(
    private val runner: TransactionRunner,
    private val transactions: TransactionRepository,
    private val snapshots: SnapshotRepository,
    private val rules: MerchantRuleRepository,
    private val categories: CategoryRepository,
) {

    /**
     * @param origin de dónde salió la cartola (el nombre del archivo), para que el corte de saldo
     * diga de dónde vino. Opcional.
     * @param now el instante de la importación, para recortar el corte de un archivo del día en
     * curso: el cierre de hoy todavía no llegó.
     */
    suspend fun import(
        accountId: Long,
        sheet: StatementImport.Sheet,
        origin: String? = null,
        now: Long = System.currentTimeMillis(),
    ): StatementReport = runner.run {
        var added = 0
        var repeated = 0
        var pending = 0
        var completed = 0
        val claimed = mutableSetOf<Long>()
        val pools = mutableMapOf<PoolKey, List<TransactionEntity>>()

        // En orden: la fila más vieja se queda con el movimiento más viejo. Si el archivo viniera
        // al revés, la de hoy podría reclamar el aviso de ayer y dejar a la de ayer sin pareja.
        for (row in sheet.rows.sortedBy { it.date }) {
            val at = Dates.startOfDay(row.date)
            val key = PoolKey(at, row.amount, row.direction)
            val pool = pools.getOrPut(key) {
                transactions.findAllNearbyIn(accountId, row.amount, row.direction, at, CARTOLA_WINDOW)
            }
            val existing = pool.firstOrNull { it.id !in claimed }
            if (existing != null) {
                claimed += existing.id
                repeated++
                val filled = complete(existing, row)
                if (filled != null) {
                    transactions.save(filled)
                    completed++
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
                    categoryId = CategoryPick.resolve(
                        rules = rules,
                        categories = categories,
                        merchant = row.merchant,
                        direction = row.direction,
                        rawText = row.rawText,
                        merchantKnown = known,
                    ),
                    status = if (known) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
                    isExpense = row.direction == Direction.DEBIT,
                    source = TransactionSource.CSV,
                    rawText = row.rawText,
                    notes = if (known) null else MISSING_MERCHANT,
                ),
            )
            added++
            if (!known) pending++
        }

        var snapshotSaved = false
        val balance = sheet.balance
        val balanceDate = sheet.balanceDate
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
                            append("Cierre del ")
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

        StatementReport(
            added = added,
            repeated = repeated,
            pending = pending,
            completed = completed,
            balance = balance,
            balanceDate = balanceDate,
            snapshotSaved = snapshotSaved,
            skipped = sheet.skipped,
        )
    }

    /**
     * Completa un movimiento que quedó a medias: el aviso del banco dijo "te transfirieron
     * $12.000" y la cartola dice quién fue. Nunca pisa un nombre que ya estaba, y a un traspaso no
     * lo toca: esos no llevan comercio.
     */
    private suspend fun complete(
        existing: TransactionEntity,
        row: StatementImport.Row,
    ): TransactionEntity? {
        if (existing.isTransfer) return null
        if (!MerchantQuality.isGeneric(existing.merchantRaw)) return null
        if (row.merchant.isEmpty() || MerchantQuality.isGeneric(row.merchant)) return null
        return existing.copy(
            merchantRaw = row.merchant,
            merchantNorm = row.merchant.lowercase(),
            categoryId = existing.categoryId ?: CategoryPick.resolve(
                rules = rules,
                categories = categories,
                merchant = row.merchant,
                direction = row.direction,
                rawText = row.rawText,
            ),
            status = TransactionStatus.CONFIRMED,
            notes = null,
            rawText = existing.rawText ?: row.rawText,
        )
    }

    /** Una fila y los movimientos que compiten por ella: mismo día, monto y sentido. */
    private data class PoolKey(val dayStart: Long, val amount: Long, val direction: Direction)

    private companion object {
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
