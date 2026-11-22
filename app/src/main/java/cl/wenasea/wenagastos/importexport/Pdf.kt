package cl.wenasea.wenagastos.importexport

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.round

/**
 * Saca la tabla de una cartola en PDF y la entrega como [Csv.Sheet], para que la lectura de
 * movimientos siga siendo una sola: [StatementImport] no sabe —ni tiene por qué— si las filas
 * vinieron de un texto separado por comas o de un PDF.
 *
 * Un PDF no se lee como un texto: los caracteres están dibujados en coordenadas. Por eso acá se
 * reconstruye la tabla, en este orden:
 *
 * 1. se descomprimen los flujos del archivo y se guarda cada trozo de texto con la posición donde
 *    el banco lo dibujó (`x`, `y`), el tamaño de letra y el texto;
 * 2. las líneas verticales largas que el banco dibuja como borde de la tabla son las fronteras de
 *    las columnas — es lo más fiel que hay, porque no depende de adivinar por los espacios;
 * 3. los trozos con la misma `y` son una fila, y cada trozo cae en la columna de su `x`;
 * 4. la cabecera puede ocupar dos líneas ("CHEQUES Y OTROS" arriba y "CARGOS" abajo), así que se
 *    juntan las líneas de arriba por columna;
 * 5. las filas traen día y mes pero no año: se completa con el período que el banco imprime en la
 *    cabecera ("DESDE 30/09/2026 HASTA 30/10/2026").
 *
 * Esto no es un lector de PDF completo. Sirve para cartolas —que las máquinas del banco generan
 * siempre igual— y devuelve `null` cuando el archivo no tiene la forma que sabe leer, en vez de
 * inventar filas.
 */
object Pdf {

    /** Los PDF empiezan con `%PDF-`, aunque algunos meten unos bytes antes. */
    fun looksLikePdf(bytes: ByteArray): Boolean =
        String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1).contains("%PDF-")

    /**
     * La cartola como hoja, o `null` si no se le pudo sacar una tabla con fecha y monto. Nunca
     * lanza: un PDF que no se entiende es un archivo que el usuario eligió mal, no una falla del
     * programa.
     */
    fun sheet(bytes: ByteArray): Csv.Sheet? {
        // Cada página es su propia tabla: su rejilla, su cabecera y sus filas. Si se juntaran
        // todas las líneas del archivo, dos páginas con la misma altura se pisarían.
        val pages = streams(bytes)
            .filter { it.contains("Tj") || it.contains("TJ") }
            .mapNotNull { page(it) }
        if (pages.isEmpty()) return null

        val header = pages.map { it.header }
            .firstOrNull { cells -> cells.any { it.isNotEmpty() } }
            ?: return null

        // El período está en la cabecera del estado de cuenta (la primera página), pero las filas
        // de todas las páginas necesitan el año.
        val period = pages.firstNotNullOfOrNull { it.period }

        val out = pages.flatMap { it.rows }
            .map { row -> row.mapIndexed { index, text -> if (index == 0) year(text, period) else text } }
        if (out.isEmpty()) return null
        return Csv.Sheet(delimiter = ';', rows = listOf(header) + out)
    }

    /** Una página leída: su cabecera, sus movimientos y el período que declara. */
    private data class Page(
        val header: List<String>,
        val rows: List<List<String>>,
        val period: Pair<LocalDate, LocalDate>?,
    )

    /** La tabla de una página, o `null` si esa página no trae una. */
    private fun page(stream: String): Page? {
        val edges = rules(stream)
        if (edges.size < 3) return null

        val rows = runs(stream).groupByLine().map { (y, line) ->
            Row(y, place(cells(line), edges))
        }
        val first = rows.indexOfFirst { SHORT_DATE.matches(it.cells.getOrNull(0).orEmpty()) }
        if (first < 0) return null
        val start = rows[first].y

        // La cabecera son las líneas pegadas arriba de la primera fila con fecha. Se juntan por
        // columna porque los títulos largos ocupan dos renglones dentro de la misma celda.
        val header = (0 until edges.size - 1).map { column ->
            repair(
                rows.take(first)
                    .filter { it.y - start in 0.0..HEADER_ZONE }
                    .mapNotNull { it.cells.getOrNull(column)?.takeIf { cell -> cell.isNotEmpty() } }
                    .joinToString(" "),
            )
        }

        val balance = header.indexOfFirst { it.lowercase().startsWith("saldo") }
        val out = ArrayList<List<String>>()
        val seen = HashSet<List<String>>()
        var afterGap = false
        for (row in rows.drop(first)) {
            if (!SHORT_DATE.matches(row.cells.getOrNull(0).orEmpty())) {
                if (out.isNotEmpty()) afterGap = true
                continue
            }
            val cells = row.cells.map { repair(it) }
            val key = cells.filterIndexed { index, _ -> index != balance }
            if (afterGap && key in seen) continue
            seen.add(key)
            afterGap = false
            out.add(cells)
        }
        return Page(header, out, period(rows.take(first)))
    }

    /** Un trozo de texto dibujado: dónde y qué dice. */
    private data class Run(val x: Double, val y: Double, val size: Double, val text: String)

    /** Un trozo de fila: varios pedazos pegados entre sí forman una celda. */
    private data class Cell(val x: Double, val text: String)

    /** Una fila ya repartida en columnas, con la altura a la que estaba dibujada. */
    private data class Row(val y: Double, val cells: List<String>)

    /** Los trozos con la misma altura son una fila: se agrupan de arriba abajo. */
    private fun List<Run>.groupByLine(): List<Pair<Double, List<Run>>> {
        val out = ArrayList<Pair<Double, List<Run>>>()
        var current = ArrayList<Run>()
        var top = 0.0
        for (run in sortedWith(compareByDescending<Run> { it.y }.thenBy { it.x })) {
            if (current.isEmpty()) {
                top = run.y
            } else if (abs(run.y - top) > LINE_TOLERANCE) {
                out.add(top to current)
                current = ArrayList()
                top = run.y
            }
            current.add(run)
        }
        if (current.isNotEmpty()) out.add(top to current)
        return out
    }

    /** Junta los trozos de una fila en celdas: un hueco de más de dos puntos es otra celda. */
    private fun cells(runs: List<Run>): List<Cell> {
        val out = ArrayList<Cell>()
        var text: StringBuilder? = null
        var start = 0.0
        var end = 0.0
        for (run in runs.sortedBy { it.x }) {
            if (text == null || run.x - end > CELL_GAP) {
                text?.let { out.add(Cell(start, it.toString())) }
                text = StringBuilder(run.text)
                start = run.x
            } else {
                text.append(run.text)
            }
            end = run.x + run.text.length * run.size * CHAR_WIDTH
        }
        text?.let { out.add(Cell(start, it.toString())) }
        return out
    }

    /** Cada celda en la columna donde cae su `x`, según los bordes que dibujó el banco. */
    private fun place(cells: List<Cell>, edges: List<Double>): List<String> {
        val columns = arrayOfNulls<StringBuilder>(edges.size - 1)
        for (cell in cells) {
            val index = columnOf(cell.x, edges)
            val slot = columns[index] ?: StringBuilder().also { columns[index] = it }
            if (slot.isNotEmpty()) slot.append(' ')
            slot.append(cell.text.trim())
        }
        return columns.map { it?.toString()?.trim().orEmpty() }
    }

    private fun columnOf(x: Double, edges: List<Double>): Int {
        var index = 0
        while (index < edges.size - 2 && x >= edges[index + 1]) index++
        return index
    }

    /**
     * El período que el banco imprime arriba ("DESDE 30/09/2026 HASTA 30/10/2026"). Sirve para
     * ponerle año a las filas, que sólo traen día y mes.
     */
    private fun period(above: List<Row>): Pair<LocalDate, LocalDate>? {
        val dates = above.flatMap { it.cells }
            .flatMap { cell -> FULL_DATE.findAll(cell).mapNotNull { dateOf(it.value) }.toList() }
        val first = dates.minOrNull() ?: return null
        return first to (dates.maxOrNull() ?: first)
    }

    /**
     * El año que le falta a `01/10`. Se prueba el año del período y los de al lado, y gana el que
     * deja la fecha dentro del período: así una cartola que va de diciembre a enero no anota el
     * 02/01 en el año equivocado.
     */
    private fun year(text: String, period: Pair<LocalDate, LocalDate>?): String {
        val match = SHORT_DATE.matchEntire(text.trim()) ?: return text
        val day = match.groupValues[1].toIntOrNull() ?: return text
        val month = match.groupValues[2].toIntOrNull() ?: return text
        if (match.groupValues[3].isNotEmpty()) return text
        val (from, to) = period ?: return text
        val year = listOf(to.year - 1, to.year, to.year + 1).firstOrNull { candidate ->
            candidate in from.year..to.year &&
                (candidate > from.year || month >= from.monthValue) &&
                (candidate < to.year || month <= to.monthValue)
        } ?: to.year
        return "%02d/%02d/%04d".format(day, month, year)
    }

    /**
     * Los flujos del archivo ya descomprimidos, como texto de un byte por carácter. Los que no se
     * pueden descomprimir —las imágenes del banco, por ejemplo— se saltean: acá sólo interesa el
     * texto.
     */
    private fun streams(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val raw = String(bytes, Charsets.ISO_8859_1)
        var from = 0
        while (true) {
            val start = raw.indexOf("stream", from)
            if (start < 0) break
            var data = start + STREAM.length
            if (data < raw.length && raw[data] == '\r') data++
            if (data < raw.length && raw[data] == '\n') data++
            val end = raw.indexOf("endstream", data)
            if (end < 0) break
            val body = raw.substring(data, end).toByteArray(Charsets.ISO_8859_1)
            inflate(body)?.let { out.add(String(it, Charsets.ISO_8859_1)) }
            from = end + END_STREAM.length
        }
        return out
    }

    /**
     * FlateDecode, que es lo que usan todas las cartolas. Un flujo que no está comprimido (o que
     * trae una imagen JPEG) no es un error: no se puede leer el texto de ahí y se sigue.
     */
    private fun inflate(body: ByteArray): ByteArray? = try {
        val inflater = Inflater()
        inflater.setInput(body)
        val out = ByteArrayOutputStream(body.size * 4)
        val buffer = ByteArray(8 * 1024)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buffer, 0, read)
            }
        }
        inflater.end()
        out.toByteArray().takeIf { it.isNotEmpty() }
    } catch (error: Exception) {
        null
    }

    /**
     * Los trozos de texto de un flujo, con su posición. Se sigue el estado de texto del PDF —la
     * matriz `Tm`, los desplazamientos `Td`/`T*` y el tamaño de letra— porque cada trozo se dibuja
     * en coordenadas absolutas y sin eso no hay forma de saber a qué fila pertenece.
     */
    private fun runs(stream: String): List<Run> {
        val out = ArrayList<Run>()
        var x = 0.0
        var y = 0.0
        var size = 0.0
        var leading = 0.0
        var pending = StringBuilder()
        val numbers = ArrayList<Double>()
        var i = 0
        while (i < stream.length) {
            val c = stream[i]
            when {
                c == '%' -> while (i < stream.length && stream[i] != '\n' && stream[i] != '\r') i++
                c.isWhitespace() -> i++
                c == '(' -> {
                    val (text, next) = readLiteral(stream, i)
                    pending.append(text)
                    i = next
                }
                c == '<' && i + 1 < stream.length && stream[i + 1] == '<' -> i += 2
                c == '<' -> {
                    val end = stream.indexOf('>', i)
                    if (end < 0) break
                    pending.append(hexText(stream.substring(i + 1, end)))
                    i = end + 1
                }
                c == '[' || c == ']' -> i++
                c == '/' -> {
                    var end = i + 1
                    while (end < stream.length && !isDelimiter(stream[end])) end++
                    i = end
                }
                c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                    var end = i
                    while (end < stream.length && (stream[end].isDigit() ||
                            stream[end] == '-' || stream[end] == '+' || stream[end] == '.')) {
                        end++
                    }
                    numbers.add(stream.substring(i, end).toDoubleOrNull() ?: 0.0)
                    i = end
                }
                else -> {
                    var end = i
                    while (end < stream.length && !isDelimiter(stream[end])) end++
                    val operator = stream.substring(i, end)
                    i = end
                    val last = numbers.size
                    when (operator) {
                        "BT" -> {
                            x = 0.0
                            y = 0.0
                        }
                        "Tm" -> if (last >= 6) {
                            x = numbers[last - 2]
                            y = numbers[last - 1]
                        }
                        "Td", "TD" -> if (last >= 2) {
                            x += numbers[last - 2]
                            y += numbers[last - 1]
                            if (operator == "TD") leading = -numbers[last - 1]
                        }
                        "T*" -> y -= leading
                        "TL" -> if (last >= 1) leading = numbers[last - 1]
                        "Tf" -> if (last >= 1) size = numbers[last - 1]
                        "Tj", "TJ", "'", "\"" -> {
                            val text = pending.toString()
                            if (text.isNotBlank()) out.add(Run(x, y, size, text))
                            if (operator == "'") y -= leading
                            pending = StringBuilder()
                        }
                    }
                    numbers.clear()
                }
            }
        }
        return out
    }

    /** Un texto literal de PDF: paréntesis balanceados, escapes y saltos de línea continuados. */
    private fun readLiteral(stream: String, start: Int): Pair<String, Int> {
        val out = StringBuilder()
        var depth = 1
        var i = start + 1
        while (i < stream.length) {
            val c = stream[i]
            when {
                c == '\\' && i + 1 < stream.length -> {
                    val next = stream[i + 1]
                    i += 2
                    when {
                        next == 'n' -> out.append('\n')
                        next == 'r' -> out.append('\r')
                        next == 't' -> out.append('\t')
                        next == 'b' -> out.append('\b')
                        next == 'f' -> out.append('\u000C')
                        next == '\n' -> Unit
                        next == '\r' -> if (i < stream.length && stream[i] == '\n') i++
                        next in '0'..'7' -> {
                            var value = next - '0'
                            var digits = 1
                            while (i < stream.length && digits < 3 && stream[i] in '0'..'7') {
                                value = value * 8 + (stream[i] - '0')
                                i++
                                digits++
                            }
                            out.append((value and 0xFF).toChar())
                        }
                        else -> out.append(next)
                    }
                }
                c == '(' -> {
                    depth++
                    out.append(c)
                    i++
                }
                c == ')' -> {
                    depth--
                    i++
                    if (depth == 0) return decode(out.toString()) to i
                    out.append(c)
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return decode(out.toString()) to i
    }

    private fun hexText(hex: String): String {
        val digits = hex.filter { it.isLetterOrDigit() }
        val bytes = ByteArray(digits.length / 2) { index ->
            digits.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
        }
        return decode(String(bytes, Charsets.ISO_8859_1))
    }

    /**
     * Un texto de PDF viene en los bytes que el banco eligió. El de Santander escribe en
     * windows-1252 ("dirección" llega como un solo byte), pero un generador moderno escribe UTF-8:
     * se prueba UTF-8 estricto primero, igual que con los CSV.
     */
    private fun decode(latin1: String): String {
        val bytes = latin1.toByteArray(Charsets.ISO_8859_1)
        return decodeUtf8(bytes) ?: String(bytes, WINDOWS_1252)
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: CharacterCodingException) {
            null
        }
    }

    /**
     * Este banco tiene un error propio: algunos nombres salen codificados de más ("sebastiÃÂÂ¡n").
     * Se sacan los `Â` sobrantes y, si lo que queda es UTF-8 válido, se usa; el caso sano
     * ("dirección") no tiene `Â` y no se toca.
     */
    private fun repair(text: String): String {
        if (!text.contains('Â')) return text
        val bytes = text.toByteArray(Charsets.ISO_8859_1).filter { it != 0xC2.toByte() }
        return decodeUtf8(bytes.toByteArray()) ?: text
    }

    /**
     * Las líneas verticales largas del flujo: son los bordes de las columnas de la tabla. Se
     * descartan los tramos cortos, que son separadores de una celda de la cabecera y no columnas.
     */
    private fun rules(stream: String): List<Double> {
        val vertical = HashMap<Double, Double>()
        var fromX = 0.0
        var fromY = 0.0
        val numbers = ArrayList<Double>()
        var i = 0
        while (i < stream.length) {
            val c = stream[i]
            when {
                c == '%' -> while (i < stream.length && stream[i] != '\n' && stream[i] != '\r') i++
                c.isWhitespace() -> i++
                c == '(' -> i = readLiteral(stream, i).second
                c == '<' || c == '>' || c == '[' || c == ']' || c == '/' || c == ')' -> i++
                c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                    var end = i
                    while (end < stream.length && (stream[end].isDigit() ||
                            stream[end] == '-' || stream[end] == '+' || stream[end] == '.')) {
                        end++
                    }
                    numbers.add(stream.substring(i, end).toDoubleOrNull() ?: 0.0)
                    i = end
                }
                else -> {
                    var end = i
                    while (end < stream.length && !isDelimiter(stream[end])) end++
                    val operator = stream.substring(i, end)
                    i = end
                    val last = numbers.size
                    if (operator == "m" && last >= 2) {
                        fromX = numbers[last - 2]
                        fromY = numbers[last - 1]
                    } else if (operator == "l" && last >= 2) {
                        val toX = numbers[last - 2]
                        val toY = numbers[last - 1]
                        if (abs(toX - fromX) <= 0.5) {
                            val at = round(toX * 10) / 10
                            vertical[at] = maxOf(vertical[at] ?: 0.0, abs(toY - fromY))
                        }
                        fromX = toX
                        fromY = toY
                    }
                    numbers.clear()
                }
            }
        }
        val tallest = vertical.values.maxOrNull() ?: return emptyList()
        return vertical.filterValues { it >= tallest * RULE_SHARE }.keys.sorted()
    }

    /** `30/10/2026`, `30-10-2026` o `30/10/26` a fecha; el año de dos dígitos es de este siglo. */
    private fun dateOf(text: String): LocalDate? {
        val parts = text.split('/', '-')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val raw = parts[2].toIntOrNull() ?: return null
        val year = if (parts[2].length <= 2) 2000 + raw else raw
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun isDelimiter(c: Char): Boolean = c.isWhitespace() || c in DELIMITERS

    private val DELIMITERS =
        charArrayOf('(', ')', '<', '>', '[', ']', '{', '}', '/', '%', '\u0000')

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    /** Dónde empieza la palabra `stream` en el archivo, y dónde termina el flujo. */
    private const val STREAM = "stream"
    private const val END_STREAM = "endstream"

    /** Los títulos de la cabecera están pegados: se juntan las líneas que quedan a esta distancia. */
    private const val HEADER_ZONE = 30.0

    /** Dos trozos con la `y` a menos de esto son la misma fila. */
    private const val LINE_TOLERANCE = 1.5

    /** Un hueco de más de dos puntos dentro de una fila es otra celda. */
    private const val CELL_GAP = 2.0

    /** Ancho aproximado de un carácter: alcanza para saber si dos trozos están pegados. */
    private const val CHAR_WIDTH = 0.6

    /** Una línea vertical que no llega a esta parte de la más alta no es borde de columna. */
    private const val RULE_SHARE = 0.6

    private val SHORT_DATE = Regex("""(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?""")

    private val FULL_DATE = Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""")
}
