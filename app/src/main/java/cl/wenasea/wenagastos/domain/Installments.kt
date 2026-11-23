package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.TransactionEntity

/**
 * Las compras en cuotas (Fase 9.3).
 *
 * Una compra en 12 cuotas no es un gasto de $600.000 el día que se compra: es un gasto de $50.000
 * cada mes durante un año. La cartola de la tarjeta cobra una cuota por mes y esa cuota es la que
 * se anota como movimiento; el precio de la compra viaja aparte, en
 * [TransactionEntity.originAmount], porque es lo que hace falta para responder la única pregunta
 * que importa: **cuánto falta pagar**.
 *
 * La cuota se guarda como número y total (`3` de `12`), no como el texto `3/12`, para que ordenar,
 * comparar y agrupar no dependa de cómo la escribió el banco.
 */
object Installments {

    /** Tope de cuotas: en Chile lo normal va de 1 a 48, y más que esto es una celda mal leída. */
    const val MAX = 60

    /** Lo que dice la celda de cuotas de una cartola. */
    sealed interface Cell {
        /** `3/12`: en qué cuota va y cuántas son. Es lo que escribe Santander. */
        data class Progress(val number: Int, val total: Int) : Cell

        /**
         * `9`: cuántas cuotas quedan **después** de esta. Es lo que escribe Falabella: la columna
         * `CUOTAS PENDIENTES` no dice en qué cuota va la compra, solo cuántas faltan.
         */
        data class Pending(val pending: Int) : Cell
    }

    /** Una compra en cuotas que todavía se está pagando, tal como sale en «Cuotas por pagar». */
    data class Plan(
        /** El comercio de la compra, como lo escribió el banco. */
        val merchant: String,
        /** Cuántas cuotas tiene la compra. */
        val total: Int,
        /** Cuántas van pagadas, contando la del mes. */
        val paid: Int,
        /** Lo que se cobra cada mes. */
        val perCharge: Long,
        /** Lo que falta pagar: las cuotas que todavía no llegaron. */
        val pending: Long,
    ) {
        /** Cuotas que faltan después de esta. */
        val remaining: Int get() = (total - paid).coerceAtLeast(0)

        /** `3/12`, el mismo texto que la cartola. */
        val label: String get() = "$paid/$total"
    }

    /**
     * `3/12`, o `null` cuando el movimiento no es una compra en cuotas. Sirve para la lista de
     * movimientos y para el editor, donde el texto se vuelve a leer.
     */
    fun label(transaction: TransactionEntity): String? {
        val total = transaction.installmentTotal?.takeIf { it > 1 } ?: return null
        val number = transaction.installmentNumber ?: return null
        return "$number/$total"
    }

    /**
     * Cómo se lee la cuota debajo del nombre del movimiento: «cuota 3/12», «en 12 cuotas» cuando
     * el banco no dijo en cuál va, o `null` cuando la compra no va en cuotas.
     */
    fun shortLabel(transaction: TransactionEntity): String? {
        val total = transaction.installmentTotal?.takeIf { it > 1 } ?: return null
        val number = transaction.installmentNumber
        return if (number == null) "en $total cuotas" else "cuota $number/$total"
    }

    /** Cuántas cuotas faltan después de esta; `null` si la cartola no lo dijo. */
    fun remaining(transaction: TransactionEntity): Int? {
        val total = transaction.installmentTotal?.takeIf { it > 1 } ?: return null
        val number = transaction.installmentNumber ?: return null
        return (total - number).coerceAtLeast(0)
    }

    /** Lo que falta pagar después de este movimiento, si se sabe en qué cuota va. */
    fun pendingAmount(transaction: TransactionEntity): Long? =
        remaining(transaction)?.let { it * transaction.amount }

    /**
     * Lee la celda de cuotas de una cartola: `05/06`, `5/6`, `5 DE 12` o un número suelto —que es
     * «cuántas faltan»— y devuelve `null` para la compra pagada de una vez (`0`, `1/1`, vacío).
     *
     * Un `1/1` no es una compra en cuotas y no se guarda: llenar la ficha de «cuota» con todas las
     * compras normales estorbaría más de lo que ayuda.
     */
    fun parseCell(text: String): Cell? {
        val clean = text.replace('\u00A0', ' ').trim()
        if (clean.isEmpty()) return null
        RATIO.matchEntire(clean)?.let { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return null
            val total = match.groupValues[2].toIntOrNull() ?: return null
            return when {
                total < 2 || total > MAX -> null
                number < 1 || number > total -> null
                else -> Cell.Progress(number, total)
            }
        }
        // Solo dígitos: cuántas cuotas faltan. Un texto con letras no es una cuota.
        if (!BARE.matches(clean)) return null
        val pending = clean.toIntOrNull() ?: return null
        return if (pending in 1 until MAX) Cell.Pending(pending) else null
    }

    /**
     * La cuota como número y total, con lo que dijo la cartola.
     *
     * [perCharge] es lo que se cobró este mes y [origin] el precio de la compra, cuando el archivo
     * lo trae: con los dos se puede saber en cuántas cuotas se compró aunque el banco no lo
     * escriba —`MONTO ORIGEN 600.000` y `VALOR CUOTA 50.000` son doce cuotas—, y de ahí sale la
     * cuota de hoy, que es la que deja `pending` cuotas por pagar.
     *
     * Devuelve `null` cuando no alcanza para decir nada: es mejor no saber la cuota que inventarla.
     */
    fun resolve(cell: Cell?, perCharge: Long, origin: Long?): Pair<Int, Int>? = when (cell) {
        null -> null
        is Cell.Progress -> cell.number to cell.total
        is Cell.Pending -> {
            val total = origin
                ?.takeIf { perCharge > 0L && it > perCharge && it % perCharge == 0L }
                ?.div(perCharge)
                ?.toInt()
            val number = total?.minus(cell.pending)
            if (total == null || number == null || total > MAX || number < 1 || number > total) {
                null
            } else {
                number to total
            }
        }
    }

    /**
     * Las compras en cuotas que todavía se están pagando, agrupadas por compra.
     *
     * Una compra se reconoce por el comercio, cuántas cuotas son y el precio original: la cartola
     * repite los tres en cada cuota. El comercio entra en la clave a propósito —dos compras
     * distintas en la misma tienda con el mismo número de cuotas son dos compras, y una de ellas
     * puede estar pagada— y la cuenta no, porque una compra no cambia de tarjeta a mitad de camino.
     *
     * Una compra ya pagada se cae de la lista: «Cuotas por pagar» es la parte del sueldo que está
     * comprometida, no un archivo de compras.
     */
    fun plans(transactions: List<TransactionEntity>): List<Plan> = transactions
        .filter { (it.installmentTotal ?: 0) > 1 }
        .groupBy { Key(TextNorm.key(it.merchantRaw), it.installmentTotal, it.originAmount) }
        .mapNotNull { (key, charges) -> planOf(key, charges) }
        .filter { it.remaining > 0 }
        .sortedWith(compareByDescending<Plan> { it.pending }.thenBy { it.merchant })

    private fun planOf(key: Key, charges: List<TransactionEntity>): Plan? {
        val total = key.total ?: return null
        val latest = charges
            .maxWithOrNull(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })
            ?: return null
        // La cuota más alta dice lo que se cobra cada mes: la última puede traer un ajuste de unos
        // pesos por redondeo, y ese peso no cambia lo que falta pagar.
        val perCharge = charges.maxOf { it.amount }
        // Cuántas van pagadas: el número más alto que dijo la cartola. Si ninguna lo dijo, se
        // cuentan las que hay anotadas, que es lo único que se sabe.
        val paid = (charges.mapNotNull { it.installmentNumber }.maxOrNull() ?: charges.size)
            .coerceIn(0, total)
        return Plan(
            merchant = latest.merchantRaw.ifBlank { "Compra en cuotas" },
            total = total,
            paid = paid,
            perCharge = perCharge,
            pending = perCharge * (total - paid),
        )
    }

    /** Lo que identifica a una compra en cuotas entre todas las filas que la cobran. */
    private data class Key(val merchant: String, val total: Int?, val origin: Long?)

    /** `05/06` y `5 DE 12`: la cuota y el total, con la barra o el «de» que use el banco. */
    private val RATIO = Regex("""^(\d{1,2})\s*(?:/|de)\s*(\d{1,2})$""", RegexOption.IGNORE_CASE)

    /** Un número suelto: cuántas cuotas faltan. */
    private val BARE = Regex("""^\d{1,2}$""")
}

