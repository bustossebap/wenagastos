package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.domain.TextNorm

/**
 * Cómo leer las filas de una cartola: en qué columna está la fecha, cuál trae el comercio y cómo
 * viene el monto.
 *
 * La primera importación de cada banco deduce la plantilla de la cabecera del archivo y la
 * recuerda (`Prefs`, por institución), para que la cartola del mes siguiente —que trae las mismas
 * columnas— no dependa de que la heurística acierte otra vez. Si el banco cambia el formato, la
 * plantilla no calza con ninguna fila y se vuelve a deducir de cero.
 *
 * Las cartolas chilenas vienen de dos maneras: con `Cargo` y `Abono` en columnas separadas (la
 * cuenta corriente) o con un solo `Monto`, que en la tarjeta de crédito es siempre una compra y por
 * eso se lee como gasto salvo que venga con signo menos.
 */
data class StatementTemplate(
    /** Nombre de la columna de la fecha, tal como vino en la cabecera. */
    val dateColumn: String,
    /** Nombre de la columna con el comercio o la descripción del movimiento. */
    val merchantColumn: String,
    /** Columna de los cargos (lo que sale). */
    val debitColumn: String? = null,
    /** Columna de los abonos (lo que entra). */
    val creditColumn: String? = null,
    /** Una sola columna de monto, cuando el banco no separa cargo de abono. */
    val amountColumn: String? = null,
    /** Columna del saldo que va dejando la cartola. Opcional: la tarjeta no lo trae. */
    val balanceColumn: String? = null,
) {

    /** `true` cuando el monto viene en una sola columna, con signo o sin él. */
    val hasSingleAmount: Boolean get() = amountColumn != null

    /** `true` cuando el archivo trae el monto repartido en dos columnas. */
    val hasTwoAmountColumns: Boolean get() = debitColumn != null || creditColumn != null

    /** La fila es la cabecera que esta plantilla espera. */
    fun matches(row: List<String>): Boolean {
        val cells = row.map { TextNorm.plain(it) }
        return TextNorm.plain(dateColumn) in cells && TextNorm.plain(merchantColumn) in cells
    }

    fun toJson(): String = Json.write(
        mapOf(
            "fecha" to dateColumn,
            "comercio" to merchantColumn,
            "cargo" to debitColumn,
            "abono" to creditColumn,
            "monto" to amountColumn,
            "saldo" to balanceColumn,
        ),
    )

    companion object {

        /** Cuántas filas se revisan buscando la cabecera: algunos bancos ponen un título arriba. */
        const val HEADER_SCAN = 10

        /**
         * La plantilla que corresponde a esta cabecera, o `null` si no se reconoce. Sin fecha o sin
         * descripción no hay movimiento que anotar; y sin ninguna forma de leer el monto, tampoco.
         */
        fun detect(headers: List<String>): StatementTemplate? {
            val columns = headers.map { TextNorm.plain(it) }
            val claimed = mutableSetOf<Int>()

            fun first(predicate: (String) -> Boolean): Int {
                val index = columns.indices.firstOrNull { it !in claimed && predicate(columns[it]) } ?: -1
                if (index >= 0) claimed += index
                return index
            }

            val date = first(::isDate)
            // El cargo y el abono se reclaman antes que el monto: "Monto cargo" es un cargo.
            val credit = first(::isCredit)
            val debit = first(::isDebit)
            val amount = first(::isAmount)
            val merchant = first(::isMerchant)
            val balance = first(::isBalance)
            if (date < 0 || merchant < 0) return null

            val template = StatementTemplate(
                dateColumn = headers[date].trim(),
                merchantColumn = headers[merchant].trim(),
                debitColumn = if (debit >= 0) headers[debit].trim() else null,
                creditColumn = if (credit >= 0) headers[credit].trim() else null,
                amountColumn = if (amount >= 0) headers[amount].trim() else null,
                balanceColumn = if (balance >= 0) headers[balance].trim() else null,
            )
            return template.takeIf { it.hasSingleAmount || it.hasTwoAmountColumns }
        }

        /** Lee la plantilla guardada. Un JSON viejo o dañado se ignora y se vuelve a deducir. */
        fun fromJson(text: String): StatementTemplate? {
            val map = try {
                Json.read(text)
            } catch (e: Json.JsonFormatException) {
                return null
            } as? Map<*, *> ?: return null
            val date = map["fecha"] as? String ?: return null
            val merchant = map["comercio"] as? String ?: return null
            return StatementTemplate(
                dateColumn = date,
                merchantColumn = merchant,
                debitColumn = map["cargo"] as? String,
                creditColumn = map["abono"] as? String,
                amountColumn = map["monto"] as? String,
                balanceColumn = map["saldo"] as? String,
            ).takeIf { it.hasSingleAmount || it.hasTwoAmountColumns }
        }

        private fun isDate(column: String): Boolean =
            column.startsWith("fecha") || column == "date" || column.contains("date") || column == "dia"

        private fun isMerchant(column: String): Boolean =
            column.startsWith("descripcion") || column.startsWith("detalle") ||
                column.startsWith("glosa") || column.startsWith("concepto") ||
                column == "comercio" || column == "nombre" ||
                column.contains("description") || column == "merchant"

        private fun isDebit(column: String): Boolean =
            column.contains("cargo") || column.contains("giro") || column.contains("retiro") ||
                column.contains("egreso") || column.contains("debit") || column.startsWith("debito")

        private fun isCredit(column: String): Boolean =
            column.contains("abono") || column.contains("deposito") || column.contains("ingreso") ||
                column.contains("credit") || column.startsWith("credito")

        private fun isAmount(column: String): Boolean =
            column.startsWith("monto") || column == "importe" || column == "valor" || column == "amount"

        private fun isBalance(column: String): Boolean =
            column.startsWith("saldo") || column == "balance"
    }
}
