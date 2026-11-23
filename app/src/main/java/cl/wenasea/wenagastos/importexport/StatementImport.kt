package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.Installments
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
        /** Cuota de una compra en cuotas (`3` de `12`); `null` cuando no va en cuotas. */
        val installmentNumber: Int? = null,
        /** Cuántas cuotas tiene la compra; `null` cuando no va en cuotas. */
        val installmentTotal: Int? = null,
        /** Precio de la compra completa, cuando la cartola lo informa (`MONTO ORIGEN`). */
        val originAmount: Long? = null,
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
        val header = findHeader(csv.rows, remembered)
        if (header != null) {
            val (template, index) = header
            val columns = Columns.of(template, csv.rows[index])
            if (columns != null) {
                val sheet = sheetOf(csv, template, columns, index)
                if (sheet.rows.isNotEmpty()) return sheet
                // La cabecera puede mentir: "CARGO DEL MES" titula dos columnas y la de abajo trae
                // la cuota, no el monto. Si las columnas que promete ni siquiera traen números, se
                // deduce mirando los datos.
                if (hasNumbers(csv.rows.drop(index + 1), columns)) return sheet
                return byValues(csv) ?: sheet
            }
        }
        return byValues(csv)
    }

    /** Los movimientos de la cartola leída con esta plantilla. */
    private fun sheetOf(
        csv: Csv.Sheet,
        template: StatementTemplate,
        columns: Columns,
        headerIndex: Int,
    ): Sheet {
        val (rows, skipped) = rowsOf(csv.rows.drop(headerIndex + 1), columns)
        return sheet(rows, skipped, template)
    }

    /**
     * Deduce las columnas mirando los datos y no la cabecera. Sirve cuando el banco titula un grupo
     * de columnas con un solo texto —"CARGO DEL MES" arriba de la cuota y del valor de la cuota— o
     * cuando el título no está sobre los datos sino centrado entre dos columnas.
     *
     * La fecha es la columna que trae fechas, los montos son los que traen montos, y cargo y abono
     * se reconocen porque nunca vienen llenos en la misma fila. El comercio es la columna de texto
     * más larga: la descripción, no la sucursal.
     */
    private fun byValues(csv: Csv.Sheet): Sheet? {
        val rows = csv.rows
        fun cell(line: List<String>, index: Int): String = line.getOrNull(index).orEmpty()
        fun isMovement(line: List<String>): Boolean =
            line.any { StatementDates.parse(it) != null } && line.any { isMoney(it) }

        val first = rows.indexOfFirst { isMovement(it) }
        if (first < 0) return null
        val movements = rows.drop(first).filter { isMovement(it) }
        val width = movements.maxOf { it.size }

        val date = (0 until width)
            .maxByOrNull { index -> movements.count { StatementDates.parse(cell(it, index)) != null } }
            ?: return null
        if (movements.none { StatementDates.parse(cell(it, date)) != null }) return null

        // Una columna es de plata si en al menos un tercio de las filas hay un monto: el cargo y el
        // abono vienen llenos en la mitad cada uno.
        val fewest = maxOf(1, (movements.size + 2) / 3)
        val money = (0 until width).filter { index ->
            index != date && movements.count { isMoney(cell(it, index)) } >= fewest
        }

        // La columna de las cuotas no es plata ni una descripción: se reconoce porque sus celdas se
        // leen como cuotas (`05/06`) y nunca como montos.
        val installment = (0 until width).firstOrNull { index ->
            index != date && index !in money &&
                movements.count { Installments.parseCell(cell(it, index)) != null } >= fewest
        } ?: -1

        // Cargo y abono: dos columnas que nunca vienen llenas en la misma fila.
        val pair = money.indices
            .flatMap { left -> (left + 1 until money.size).map { right -> money[left] to money[right] } }
            .firstOrNull { (debit, credit) ->
                movements.none { isMoney(cell(it, debit)) && isMoney(cell(it, credit)) }
            }
        val debit = pair?.first ?: -1
        val credit = pair?.second ?: -1
        // Con una sola columna de monto —la tarjeta— se toma la que trae monto en más filas: la
        // cartola de tarjeta puede traer el total de la compra sólo en las que van en cuotas, y el
        // cargo del mes en todas. Si empatan, la primera: el total antes que la cuota.
        val amount = if (debit < 0) {
            money.maxByOrNull { index -> movements.count { isMoney(cell(it, index)) } } ?: return null
        } else {
            -1
        }

        // Los nombres salen de la cabecera que está sobre la tabla, para que la plantilla que se
        // recuerde después también calce con este archivo.
        val names = rows.take(first).lastOrNull { line -> cell(line, date).isNotBlank() } ?: emptyList()
        fun name(index: Int): String {
            val text = names.getOrNull(index)?.trim().orEmpty()
            val unique = text.isNotEmpty() && names.count { it.trim().equals(text, ignoreCase = true) } == 1
            return if (unique) text else "Columna ${index + 1}"
        }

        val balance = money.firstOrNull { name(it).lowercase().startsWith("saldo") } ?: -1
        // Con una columna de cuotas y dos de plata, una es la cuota del mes y la otra el precio de
        // la compra. Si el monto elegido ya es la cuota, la otra columna de plata es el precio.
        val monthly = amount >= 0 && name(amount).lowercase().contains("cuota")
        val installmentAmount = if (installment < 0 || monthly) {
            -1
        } else {
            money.firstOrNull { index ->
                index != amount && index != balance &&
                    name(index).lowercase().let { it.contains("cuota") || it.startsWith("valor") }
            } ?: -1
        }
        val origin = when {
            installment < 0 || amount < 0 -> -1
            monthly -> money.firstOrNull { it != amount && it != balance } ?: -1
            else -> money.firstOrNull { index ->
                index != amount && index != balance && index != installmentAmount &&
                    name(index).lowercase().contains("origen")
            } ?: -1
        }
        // El comercio tiene que venir en casi todas las filas: una columna que se repite vacía no
        // es la descripción, y un movimiento sin comercio no se puede anotar.
        val filled = maxOf(1, movements.size / 2)
        val merchant = (0 until width)
            .filter { it != date && it != installment && it !in money }
            .filter { index -> movements.count { cell(it, index).isNotBlank() } >= filled }
            .maxByOrNull { index -> movements.sumOf { cell(it, index).trim().length } }
            ?: return null

        val template = StatementTemplate(
            dateColumn = name(date),
            merchantColumn = name(merchant),
            debitColumn = if (debit >= 0) name(debit) else null,
            creditColumn = if (credit >= 0) name(credit) else null,
            amountColumn = if (amount >= 0) name(amount) else null,
            balanceColumn = if (balance >= 0) name(balance) else null,
            installmentColumn = if (installment >= 0) name(installment) else null,
            installmentAmountColumn = if (installmentAmount >= 0) name(installmentAmount) else null,
            originColumn = if (origin >= 0) name(origin) else null,
        )
        val columns = Columns(
            date = date,
            merchant = merchant,
            debit = debit,
            credit = credit,
            amount = amount,
            balance = balance,
            installment = installment,
            installmentAmount = installmentAmount,
            origin = origin,
        )
        val (parsed, skipped) = rowsOf(rows.drop(first), columns)
        return sheet(parsed, skipped, template)
    }

    /** Un renglón a movimiento; los que no lo son se cuentan como salteados. */
    private fun rowsOf(lines: List<List<String>>, columns: Columns): Pair<List<Row>, Int> {
        val rows = ArrayList<Row>()
        var skipped = 0
        for (line in lines) {
            val row = parseRow(line, columns)
            if (row == null) skipped++ else rows.add(row)
        }
        return rows to skipped
    }

    /** La hoja con lo que se pudo leer, con el corte en la fila más nueva que trae saldo. */
    private fun sheet(rows: List<Row>, skipped: Int, template: StatementTemplate): Sheet {
        // La cartola puede venir en cualquier orden: el corte es el de la fecha más nueva. Si ese
        // último día trae varios movimientos, el saldo de cierre es el del último renglón cuando el
        // archivo va de viejo a nuevo (el PDF del banco) y el del primero cuando va al revés (su
        // Excel).
        val withBalance = rows.filter { it.balance != null }
        val newest = withBalance.maxOfOrNull { it.date }
        val closing = withBalance.filter { it.date == newest }.let { day ->
            if (day.size < 2 || !newestFirst(rows)) day.lastOrNull() else day.firstOrNull()
        }
        return Sheet(
            rows = rows,
            skipped = skipped,
            balance = closing?.balance,
            balanceDate = closing?.date,
            template = template,
        )
    }

    /**
     * `true` si la cartola viene de lo más nuevo a lo más viejo, como el Excel de Falabella. Lo
     * dicen las fechas: en un archivo ordenado, cada renglón es más viejo que el de arriba. Cuando
     * todas las filas son del mismo día —o las fechas vienen revueltas— las fechas no dicen nada y
     * manda el saldo corrido; sin ninguna de las dos señales se asume de viejo a nuevo, como la
     * mayoría de las cartolas.
     */
    private fun newestFirst(rows: List<Row>): Boolean {
        for (index in 1 until rows.size) {
            val previous = rows[index - 1].date
            val current = rows[index].date
            if (previous != current) return current < previous
        }
        return newestFirstByBalance(rows)
    }

    /**
     * El orden según los saldos: en un archivo de viejo a nuevo, el saldo del de abajo es el de
     * arriba más su propio movimiento; al revés, es el de arriba el que cierra con el suyo. Un par
     * simétrico —lo que entra es lo que sale— calza en los dos sentidos y no dice nada.
     */
    private fun newestFirstByBalance(rows: List<Row>): Boolean {
        for (index in 1 until rows.size) {
            val before = rows[index - 1].balance ?: continue
            val after = rows[index].balance ?: continue
            val forward = after == before + movement(rows[index])
            val backward = before == after + movement(rows[index - 1])
            if (forward != backward) return backward
        }
        return false
    }

    /** El monto con su signo: lo que el movimiento le suma o le resta al saldo. */
    private fun movement(row: Row): Long =
        if (row.direction == Direction.CREDIT) row.amount else -row.amount

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
        // La cuota de la compra: `05/06` en Santander, cuántas faltan en Falabella. No es plata y
        // hay que leerla antes que los montos.
        val installment = Installments.parseCell(columns.text(line, columns.installment))
        val monthly = StatementNumbers.parse(columns.text(line, columns.installmentAmount))
        val monto = StatementNumbers.parse(columns.text(line, columns.amount))
        // En una compra en cuotas, «valor cuota» es la plata de este mes y la columna del monto trae
        // el precio de la compra entera: anotar el precio cada mes es pagar la compra doce veces.
        // Y en la cartola de la tarjeta el monto viene vacío en las compras sin cuotas —ahí la plata
        // del mes está solo en «valor cuota»—, así que esa columna es también el respaldo del monto.
        val perCharge = installment != null && monthly != null && monthly.value != 0L
        val charge = if (perCharge) monthly else monto ?: monthly
        // El precio de la compra: la columna que lo dice, o la del monto cuando la del monto quedó
        // como precio.
        val origin = when {
            perCharge -> StatementNumbers.parse(columns.text(line, columns.origin))?.value
                ?: monto?.value
            else -> StatementNumbers.parse(columns.text(line, columns.origin))?.value
        }
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
            if (charge == null || charge.value == 0L) return null
            direction = when {
                charge.value < 0 -> Direction.DEBIT
                // Con el signo escrito, el "+" es plata que entra.
                charge.signed -> Direction.CREDIT
                // Falabella escribe el pago de la tarjeta en positivo y pone el signo en otra columna
                // de la misma fila (`MONTO 208.960` con `VALOR CUOTA -208.960`). Sin mirar ahí, pagar
                // la tarjeta se anotaría como un gasto más.
                paid(line, columns, charge.value) -> Direction.CREDIT
                // Sin signo es una compra: así vienen las cartolas de tarjeta.
                else -> Direction.DEBIT
            }
            amount = abs(charge.value)
        }
        val balance = StatementNumbers.parse(columns.text(line, columns.balance))?.value
        // Una cuota sólo se anota en las compras: pagar la tarjeta no va en cuotas.
        val cuota = if (direction == Direction.DEBIT) {
            Installments.resolve(installment, amount, origin?.let(::abs))
        } else {
            null
        }
        return Row(
            date = date,
            amount = amount,
            direction = direction,
            merchant = describe(columns.text(line, columns.merchant)),
            balance = balance,
            installmentNumber = cuota?.first,
            installmentTotal = cuota?.second,
            // Un precio menor que la cuota de un mes es una columna mal leída, no una compra.
            originAmount = origin?.let(::abs)?.takeIf { cuota != null && it >= amount },
            rawText = line.joinToString(" · ") { it.trim() }.take(MAX_RAW_TEXT),
        )
    }

    /** El comercio sin los espacios de relleno que mete la planilla. */
    private fun describe(text: String): String =
        text.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim().take(MAX_MERCHANT)

    /**
     * `true` si la celda es un monto y nada más: sirve para reconocer las columnas de plata sin
     * cabecera. Es más estricto que [StatementNumbers.parse], que saca los dígitos de donde estén
     * —"MERCADO PAGO 4 TCOM" tiene un 4, pero no es plata—.
     */
    private fun isMoney(text: String): Boolean {
        val clean = text.replace('\u00A0', ' ').trim()
        return clean.isNotEmpty() && MONEY.matches(clean)
    }

    /**
     * `true` cuando la plantilla apunta a columnas que de verdad traen montos. Una cabecera partida
     * en varios renglones —"CARGO DEL MES" sobre la cuota y el valor de la cuota— deja la plantilla
     * apuntando a la columna de las cuotas, donde no hay ni un número.
     */
    private fun hasNumbers(lines: List<List<String>>, columns: Columns): Boolean {
        // Las columnas que la fila va a mirar: cargo y abono si están, y si no el monto.
        val money = (if (columns.debit >= 0 || columns.credit >= 0) {
            listOf(columns.debit, columns.credit)
        } else {
            listOf(columns.amount)
        }).filter { it >= 0 }
        if (money.isEmpty()) return true
        val withNumber = lines.count { line -> money.any { isMoney(line.getOrNull(it).orEmpty()) } }
        return withNumber * 2 >= lines.size
    }

    /**
     * `true` cuando otra celda de la misma fila trae exactamente el negativo del monto. Es como una
     * cartola de tarjeta marca un pago cuando prefiere no escribir el monto en negativo: el monto va
     * en la columna de la compra y el signo, en la de la cuota.
     */
    private fun paid(line: List<String>, columns: Columns, amount: Long): Boolean =
        line.indices.any { index ->
            index != columns.amount && index != columns.balance &&
                StatementNumbers.parse(line[index])?.value == -amount
        }

    /** Dónde queda cada columna en las filas de esta cartola; `-1` cuando no existe. */
    private class Columns(
        val date: Int,
        val merchant: Int,
        val debit: Int,
        val credit: Int,
        val amount: Int,
        val balance: Int,
        /** La celda de la cuota (`05/06`, `9`, `1/1`); no trae plata. */
        val installment: Int = -1,
        /** La plata que se cobra cada mes, cuando el banco la separa del monto. */
        val installmentAmount: Int = -1,
        /** El precio de la compra completa. */
        val origin: Int = -1,
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
                    installment = index(template.installmentColumn),
                    installmentAmount = index(template.installmentAmountColumn),
                    origin = index(template.originColumn),
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
            // Una cuota ("05/06") o una fecha también son puros dígitos, y no son plata.
            if (raw.contains('/')) return null
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

    /** Un monto tal como lo escribe el banco: `$ 30.600`, `-775.655`, `(500)`. Sin letras ni barras. */
    private val MONEY = Regex("""^\(?[\s$]*[+-]?[\s$]*\d[\d.,]*\)?$""")
}
