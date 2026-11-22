package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.TextNorm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * La cartola no se pudo leer. El mensaje está escrito para mostrarse tal cual: dice qué le faltó
 * al archivo, no cómo falló el programa.
 */
class StatementException(message: String) : Exception(message)

/**
 * Traduce la cartola de un banco a movimientos.
 *
 * Es puro a propósito: no toca la base ni conoce Android, así las reglas de lectura —qué es un
 * movimiento, qué es un total, de qué lado va el monto— se prueban en la JVM con el archivo de
 * verdad. Quien escribe es [cl.wenasea.wenagastos.data.repo.StatementImportRepository].
 */
object StatementImport {

    /** Una fila de la cartola ya traducida a movimiento, antes de tocar la base. */
    data class Row(
        val date: LocalDate,
        val amount: Long,
        val direction: Direction,
        /** El comercio tal como lo escribe el banco; vacío si la fila no lo traía. */
        val merchant: String,
        /** Saldo que deja la cartola en esa fila, cuando el banco lo informa. */
        val balance: Long?,
        /** El renglón completo, para poder revisarlo después desde el editor. */
        val rawText: String,
    )

    /** Lo que se pudo leer de un archivo. */
    data class Sheet(
        val rows: List<Row>,
        /** Filas que no eran movimientos: títulos, totales, renglones en blanco. */
        val skipped: Int,
        /** Saldo de la fila más nueva que traía uno: el corte que informa el banco. */
        val balance: Long?,
        val balanceDate: LocalDate?,
        /** La plantilla con la que se leyó, para recordarla por institución. */
        val template: StatementTemplate,
    )

    /**
     * Traduce la hoja a movimientos. Con [remembered] se usa esa plantilla —la del banco,
     * guardada de una importación anterior—; si no calza con ninguna fila, o no hay, se deduce de
     * la cabecera del archivo.
     *
     * `null` cuando no se encontró la cabecera: sin saber qué columna es la fecha no hay forma de
     * adivinar, y anotar montos en el día equivocado es peor que no importar nada.
     */
    fun read(csv: Csv.Sheet, remembered: StatementTemplate? = null): Sheet? {
        val header = findHeader(csv.rows, remembered) ?: return null
        val (template, index) = header
        val columns = Columns.of(template, csv.rows[index]) ?: return null

        val rows = ArrayList<Row>()
        var skipped = 0
        for (line in csv.rows.drop(index + 1)) {
            val row = parseRow(line, columns)
            if (row == null) skipped++ else rows.add(row)
        }
        // La cartola puede venir en cualquier orden: el corte es el de la fecha más nueva.
        val newest = rows.filter { it.balance != null }.maxByOrNull { it.date }
        return Sheet(
            rows = rows,
            skipped = skipped,
            balance = newest?.balance,
            balanceDate = newest?.date,
            template = template,
        )
    }

    /**
     * La cabecera del archivo. La plantilla recordada manda; si el banco cambió las columnas, se
     * busca una cabecera reconocible en las primeras filas, porque varios bancos escriben el
     * nombre de la cartola y el número de cuenta antes de la tabla.
     */
    private fun findHeader(
        rows: List<List<String>>,
        remembered: StatementTemplate?,
    ): Pair<StatementTemplate, Int>? {
        if (remembered != null) {
            val index = rows.indexOfFirst { remembered.matches(it) }
            if (index >= 0) return remembered to index
        }
        for (index in rows.indices.take(StatementTemplate.HEADER_SCAN)) {
            val detected = StatementTemplate.detect(rows[index]) ?: continue
            return detected to index
        }
        return null
    }

    /**
     * Un renglón a movimiento. Devuelve `null` cuando no lo es: sin fecha no hay movimiento, y sin
     * monto tampoco (las columnas de cargo y abono vienen vacías en la mitad de las filas).
     */
    private fun parseRow(line: List<String>, columns: Columns): Row? {
        val date = StatementDates.parse(columns.text(line, columns.date)) ?: return null
        val direction: Direction
        val amount: Long
        if (columns.debit >= 0 || columns.credit >= 0) {
            val out = StatementNumbers.parse(columns.text(line, columns.debit))?.value ?: 0L
            val into = StatementNumbers.parse(columns.text(line, columns.credit))?.value ?: 0L
            when {
                out != 0L && into == 0L -> {
                    direction = Direction.DEBIT
                    amount = abs(out)
                }

                into != 0L && out == 0L -> {
                    direction = Direction.CREDIT
                    amount = abs(into)
                }

                // Con las dos columnas llenas, o con ninguna, la fila no es un movimiento.
                else -> return null
            }
        } else {
            val monto = StatementNumbers.parse(columns.text(line, columns.amount)) ?: return null
            if (monto.value == 0L) return null
            direction = when {
                monto.value < 0 -> Direction.DEBIT
                // Con el signo escrito, el "+" es plata que entra.
                monto.signed -> Direction.CREDIT
                // Sin signo es una compra: así vienen las cartolas de tarjeta.
                else -> Direction.DEBIT
            }
            amount = abs(monto.value)
        }
        val balance = StatementNumbers.parse(columns.text(line, columns.balance))?.value
        return Row(
            date = date,
            amount = amount,
            direction = direction,
            merchant = describe(columns.text(line, columns.merchant)),
            balance = balance,
            rawText = line.joinToString(" · ") { it.trim() }.take(MAX_RAW_TEXT),
        )
    }

    /** El comercio sin los espacios de relleno que mete la planilla. */
    private fun describe(text: String): String =
        text.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim().take(MAX_MERCHANT)

    /** Dónde queda cada columna en las filas de esta cartola; `-1` cuando no existe. */
    private class Columns(
        val date: Int,
        val merchant: Int,
        val debit: Int,
        val credit: Int,
        val amount: Int,
        val balance: Int,
    ) {
        fun text(row: List<String>, index: Int): String =
            if (index < 0) "" else row.getOrNull(index)?.trim().orEmpty()

        companion object {
            fun of(template: StatementTemplate, header: List<String>): Columns? {
                val cells = header.map { TextNorm.plain(it) }

                fun index(name: String?): Int =
                    name?.let { wanted -> cells.indexOf(TextNorm.plain(wanted)) } ?: -1

                val date = index(template.dateColumn)
                val merchant = index(template.merchantColumn)
                if (date < 0 || merchant < 0) return null
                return Columns(
                    date = date,
                    merchant = merchant,
                    debit = index(template.debitColumn),
                    credit = index(template.creditColumn),
                    amount = index(template.amountColumn),
                    balance = index(template.balanceColumn),
                )
            }
        }
    }

    /**
     * Los formatos de fecha que usan las cartolas: el día primero, como se escribe acá. El año de
     * dos dígitos lo completa `DateTimeFormatter` en este siglo, que es lo que corresponde.
     */
    private object StatementDates {

        private val PATTERNS = listOf(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "dd/MM/yy",
            "dd-MM-yy",
        )

        /** Un año fuera de este rango es una columna mal mapeada, no una cartola vieja. */
        private val YEARS = 1990..2100

        fun parse(text: String): LocalDate? {
            val clean = text.trim()
            if (clean.isEmpty()) return null
            // Algunos bancos agregan la hora: "18/11/2026 10:22" o "2026-11-18T10:22:00".
            val head = clean.substringBefore(' ').substringBefore('T')
            for (candidate in listOf(clean, head).distinct()) {
                for (pattern in PATTERNS) {
                    val parsed = runCatching {
                        LocalDate.parse(candidate, DateTimeFormatter.ofPattern(pattern, Locale.US))
                    }.getOrNull()
                    if (parsed != null && parsed.year in YEARS) return parsed
                }
            }
            return null
        }
    }

    /**
     * Un monto de la cartola. [signed] dice si el archivo traía el signo escrito, porque un monto
     * sin signo y uno con "+" no significan lo mismo en una cartola de tarjeta.
     */
    private data class Amount(val value: Long, val signed: Boolean)

    /**
     * Lee montos como los escribe el banco: `1.234.567`, `$-12.345`, `12.345,00`, `(500)` para un
     * negativo. El punto es de miles y la coma, decimal: si la coma lleva uno o dos dígitos detrás,
     * lo que sigue son centavos, que la app no guarda.
     */
    private object StatementNumbers {

        fun parse(text: String): Amount? {
            val raw = text.replace('\u00A0', ' ').trim()
            if (raw.isEmpty()) return null
            val negative = raw.startsWith("-") || (raw.startsWith("(") && raw.endsWith(")"))
            val positive = raw.startsWith("+")

            var whole = raw
            val comma = whole.lastIndexOf(',')
            if (comma >= 0 && whole.length - comma - 1 in 1..2) whole = whole.substring(0, comma)

            val digits = whole.filter { it.isDigit() }
            if (digits.isEmpty()) return null
            val value = digits.toLongOrNull() ?: return null
            return Amount(if (negative) -value else value, positive || negative)
        }
    }

    /** El renglón completo se guarda para poder revisarlo; no hace falta más que esto. */
    private const val MAX_RAW_TEXT = 300
    private const val MAX_MERCHANT = 120
}
