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
 * 2. las columnas salen de dos maneras. Si el banco dibujó la rejilla, las líneas verticales largas
 *    son las fronteras — es lo más fiel que hay. Si no la dibujó (o si las líneas que dibujó son de
 *    otro cuadro de la misma página), las columnas salen del propio texto: donde empiezan los
 *    textos y dónde terminan los montos, que el banco alinea a la derecha;
 * 3. los trozos con la misma `y` son una fila, y cada trozo cae en su columna;
 * 4. la cabecera es el bloque de renglones pegados sobre la primera fila (los títulos largos ocupan
 *    dos o tres renglones dentro de la misma celda);
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
        val cMaps = cMaps(bytes)
        val pages = streams(bytes)
            .filter { it.contains("Tj") || it.contains("TJ") }
            .mapNotNull { page(it, cMaps) }
            .filter { it.rows.isNotEmpty() }
        if (pages.isEmpty()) return null

        // La cabecera es la de la página que trajo los movimientos: la primera hoja de una cartola
        // de tarjeta trae el resumen de cupos y no tiene la tabla.
        val header = pages.maxByOrNull { it.rows.size }?.header ?: return null
        // Sin cabecera no se adivina: el nombre de la columna es lo que dice de qué lado va un
        // monto, y una tabla sin títulos se puede leer de dos maneras opuestas.
        if (header.all { it.isEmpty() }) return null

        // El período está en la cabecera del estado de cuenta (la primera página), pero las filas
        // de todas las páginas necesitan el año.
        val period = pages.firstNotNullOfOrNull { it.period }

        val out = pages.flatMap { it.rows }.map { row -> row.map { year(it, period) } }
        if (out.isEmpty()) return null
        return Csv.Sheet(delimiter = ';', rows = listOf(header) + out)
    }

    /** Una página leída: su cabecera, sus movimientos y el período que declara. */
    private data class Page(
        val header: List<String>,
        val rows: List<List<String>>,
        val period: Pair<LocalDate, LocalDate>?,
    )

    /**
     * La tabla de una página, o `null` si esa página no trae una.
     *
     * Se prueban las formas de sacar las columnas, en orden: la rejilla que dibujó el banco, las
     * posiciones del texto y, cuando ninguna de las dos deja una tabla con títulos, la tabla de la
     * tarjeta que el banco no tituló.
     */
    private fun page(stream: String, cMaps: Map<String, Map<Int, String>>): Page? {
        val runs = runs(stream, cMaps)
        if (runs.isEmpty()) return null
        val lines = runs.groupByLine()

        val ruled = rules(stream)
        val found = (if (ruled.size >= 3) table(lines, Grid(ruled, emptyList())) else null)
            ?: table(lines, textGrid(runs, lines.size))
        // La tarjeta sin títulos: la tabla sacada del texto toma la cuota (`01/01`) por una fecha y
        // queda con la cabecera vacía, que es lo mismo que no tener tabla.
        if (found == null || found.header.all { it.isEmpty() }) return headerless(lines) ?: found
        return found
    }

    /**
     * La tabla de una tarjeta que el banco no tituló. El estado de cuenta de la CMR dibuja el
     * detalle sin cabecera —arriba de las filas va un título de sección y nada más—, con la fecha en
     * su propia columna y cinco más a la derecha: la marca de titular (`T`), el monto, el monto otra
     * vez, la cuota (`01/03`) y el mes de facturación (`nov-2026`). La tabla que sale del texto toma
     * la cuota por una fecha —`01/01` se lee como día y mes— y queda sin títulos, que es lo mismo
     * que no tener tabla.
     *
     * Acá las columnas salen de lo que trae cada renglón: la fecha —sola, o pegada al comercio como
     * la dibuja otra versión del mismo estado de cuenta—, el comercio que va entre la fecha y la
     * primera columna de plata, y la cuota como frontera: a su izquierda el precio de la compra, a su
     * derecha la plata que el banco cobra este mes, que es la que se anota. El banco escribe el monto
     * dos veces cuando la compra no va en cuotas: es la misma columna repetida y da lo mismo cuál se
     * lea.
     *
     * `null` cuando la página no tiene al menos [HEADERLESS_ROWS] renglones con esa forma: sin
     * títulos, adivinar la tabla es anotar montos que no existen.
     */
    private fun headerless(lines: List<Pair<Double, List<Run>>>): Page? {
        val rows = lines.mapNotNull { (_, line) -> headerlessRow(line) }
        if (rows.size < HEADERLESS_ROWS) return null
        return Page(header = HEADERLESS_HEADER, rows = rows, period = null)
    }

    /** Un renglón de la tabla sin cabecera, en las columnas de [HEADERLESS_HEADER]. */
    private fun headerlessRow(line: List<Run>): List<String>? {
        val runs = line.sortedBy { it.x }
        val (first, date, inline) = datedRun(runs) ?: return null
        val right = runs.filter { it.x > first.x }
        val money = right.filter { MONEY.matches(it.text.trim()) }
        if (money.isEmpty()) return null
        // La cuota (`02/03`) es la frontera: a su izquierda va el precio de la compra y a su
        // derecha, lo que se cobra este mes.
        val installment = right.firstOrNull { INSTALLMENT.matches(it.text.trim()) }
        val price = money
            .filter { row -> installment == null || row.x < installment.x }
            .minByOrNull { it.x }
        val monthly = installment?.let { edge -> money.filter { it.x > edge.x }.maxByOrNull { it.x } }
        // El comercio: lo que va entre la fecha y la primera columna de plata, sin la marca de
        // titular (`T`) ni el mes de facturación (`nov-2026`), que son columnas y no el nombre.
        val edge = minOf(price?.x ?: Double.MAX_VALUE, installment?.x ?: Double.MAX_VALUE)
        val merchant = (
            listOf(inline) + right
                .filter { it.x < edge && !MONEY.matches(it.text.trim()) }
                .map { it.text.trim() }
            )
            .filter { text -> text.length > 1 && !MONTH.matches(text) }
            .joinToString(" ")
        if (merchant.isEmpty()) return null
        // La CMR escribe el pago de la tarjeta con el signo en el monto (`-208.962`), y para la app
        // un monto negativo en esa columna es un cargo. Se deja como lo escribe Falabella —el monto
        // en positivo y el signo en la columna de la cuota—, que es la forma que la importación
        // reconoce como plata que entra a la tarjeta.
        val charge = price?.text?.trim().orEmpty()
        val payment = charge.startsWith("-")
        val monthlyText = when {
            monthly != null -> monthly.text.trim()
            payment -> "-" + charge.removePrefix("-").trim()
            else -> ""
        }
        return listOf(
            date,
            repair(merchant),
            if (payment) charge.removePrefix("-").trim() else charge,
            installment?.text?.trim().orEmpty(),
            monthlyText,
        )
    }

    /**
     * El trozo que trae la fecha de un renglón, con lo que venga pegado a ella. Hay bancos que
     * dibujan la fecha sola (`12/10/2026` y el comercio en el trozo de al lado) y otros que la pegan
     * al comercio en el mismo trozo (`23/09/2026 C moran`).
     */
    private fun datedRun(runs: List<Run>): Triple<Run, String, String>? {
        for (run in runs) {
            val text = run.text.trim()
            DATE_AND_TEXT.matchEntire(text)?.let { match ->
                val date = match.groupValues[1]
                if (dateOf(date) != null) return Triple(run, date, match.groupValues[2].trim())
            }
            if (dateOf(text) != null) return Triple(run, text, "")
        }
        return null
    }

    /** Las columnas de una tabla: sus fronteras y, si salieron del texto, dónde terminan los montos. */
    private data class Grid(val edges: List<Double>, val ends: List<Double>)

    /** Una página con estas columnas, o `null` si con ellas no se ve ningún movimiento. */
    private fun table(lines: List<Pair<Double, List<Run>>>, grid: Grid): Page? {
        if (grid.edges.size < 3) return null
        val rows = lines.map { (y, line) -> Row(y, place(cells(line, grid), grid)) }
        val first = rows.indexOfFirst { row -> movement(row.cells) }
        if (first < 0) return null
        return Page(header(rows, first, grid), movements(rows, first), period(rows.take(first)))
    }

    /** Un renglón de la tabla es un movimiento si trae fecha y trae monto. */
    private fun movement(cells: List<String>): Boolean =
        cells.any { SHORT_DATE.matches(it) } && cells.any { MONEY.matches(it) }

    /**
     * La cabecera: los renglones pegados sobre la primera fila, juntados por columna. Se corta en
     * el primer hueco más grande que el interlineado de la tabla, que es donde empieza el título de
     * arriba (o el cuadro anterior) y no la cabecera.
     */
    private fun header(rows: List<Row>, first: Int, grid: Grid): List<String> {
        val block = ArrayList<Row>()
        val start = rows[first].y
        var below = start
        for (row in rows.take(first).reversed()) {
            if (row.y - below >= HEADER_GAP || row.y - start > HEADER_ZONE) break
            block.add(row)
            below = row.y
        }
        return (0 until grid.edges.size - 1).map { column ->
            repair(
                block.reversed()
                    .mapNotNull { it.cells.getOrNull(column)?.takeIf { cell -> cell.isNotEmpty() } }
                    .joinToString(" "),
            )
        }
    }

    /**
     * Las filas de la tabla. El pie de la cartola repite el último movimiento (a veces sin el
     * saldo): un renglón que ya salió, después de un hueco, no es un movimiento más.
     */
    private fun movements(rows: List<Row>, first: Int): List<List<String>> {
        val out = ArrayList<List<String>>()
        val seen = HashSet<List<String>>()
        var afterGap = false
        for (row in rows.drop(first)) {
            if (!movement(row.cells)) {
                if (out.isNotEmpty()) afterGap = true
                continue
            }
            val cells = row.cells.map { repair(it) }
            // La clave deja los montos en blanco: el pie repite el movimiento sin el saldo, y el
            // saldo es lo único que cambia.
            val key = cells.map { if (MONEY.matches(it)) "" else it }
            if (afterGap && key in seen) continue
            seen.add(key)
            afterGap = false
            out.add(cells)
        }
        return out
    }

    /** Un trozo de texto dibujado: dónde y qué dice. */
    private data class Run(val x: Double, val y: Double, val size: Double, val text: String)

    /** Un trozo de fila: varios pedazos pegados entre sí forman una celda. */
    private data class Cell(val x: Double, val end: Double, val text: String)

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
    private fun cells(runs: List<Run>, grid: Grid): List<Cell> {
        val out = ArrayList<Cell>()
        var text: StringBuilder? = null
        var start = 0.0
        var end = 0.0
        var column = 0
        for (run in runs.sortedBy { it.x }) {
            // Dos textos en columnas distintas son dos celdas por pegados que estén: la cabecera de
            // una tabla apretada se escribe celda contra celda, sin un punto de aire entre medio.
            val runColumn = columnOf(run.x, grid.edges)
            if (text == null || run.x - end > CELL_GAP || runColumn != column) {
                text?.let { out.add(Cell(start, end, it.toString())) }
                text = StringBuilder(run.text)
                start = run.x
                column = runColumn
            } else {
                text.append(run.text)
            }
            end = run.x + width(run.text, run.size)
        }
        text?.let { out.add(Cell(start, end, it.toString())) }
        return out
    }

    /**
     * Cada celda en su columna. Un texto cae donde empieza; un monto, donde termina: el banco los
     * alinea a la derecha, así que el mismo monto empieza más a la izquierda mientras más cifras
     * tenga. Cuando las columnas salen de la rejilla esto da lo mismo —las líneas son el límite—,
     * pero cuando salen del texto es la diferencia entre que un monto de siete cifras y uno de
     * cuatro caigan en la misma columna o en dos.
     */
    private fun place(cells: List<Cell>, grid: Grid): List<String> {
        val columns = arrayOfNulls<StringBuilder>(grid.edges.size - 1)
        for (cell in cells) {
            val index = columnOf(cell, grid)
            val slot = columns[index] ?: StringBuilder().also { columns[index] = it }
            if (slot.isNotEmpty()) slot.append(' ')
            slot.append(cell.text.trim())
        }
        return columns.map { it?.toString()?.trim().orEmpty() }
    }

    /** La columna de la celda: la que termina donde termina un monto, o la que la contiene. */
    private fun columnOf(cell: Cell, grid: Grid): Int {
        val money = grid.ends.firstOrNull { abs(cell.end - it) <= END_TOLERANCE }
        return columnOf(money?.minus(END_TOLERANCE / 2) ?: cell.x, grid.edges)
    }

    private fun columnOf(x: Double, edges: List<Double>): Int {
        var index = 0
        while (index < edges.size - 2 && x >= edges[index + 1]) index++
        return index
    }

    /**
     * Las columnas cuando el banco no dibujó la rejilla, sacadas del texto: son fronteras donde
     * empiezan los textos que se repiten y donde terminan los montos, que es lo único que el banco
     * alinea en todas las filas.
     */
    private fun textGrid(runs: List<Run>, lines: Int): Grid {
        // Una cartola de dos filas no repite nada: cuanto menos texto, menos se exige.
        val support = (lines / 3).coerceIn(1, 3)
        val money = runs.filter { MONEY.matches(it.text) }
        fun endOf(run: Run): Double = run.x + width(run.text, run.size)

        val ends = clusters(money.map(::endOf), maxOf(1, support - 1), END_TOLERANCE) { it.max() }
        // La columna de un monto empieza donde empieza el monto más ancho de esa columna.
        val lefts = ends.mapNotNull { edge ->
            money.filter { abs(endOf(it) - edge) <= END_TOLERANCE }.minOfOrNull { it.x }
        }
        val starts = clusters(runs.filterNot { MONEY.matches(it.text) }.map { it.x }, support, START_TOLERANCE) { it.min() }
        return Grid(merge(starts + lefts + ends), ends)
    }

    /** Los valores que se repiten, agrupados: dos marcas a menos de [tolerance] son la misma. */
    private fun clusters(
        values: List<Double>,
        support: Int,
        tolerance: Double,
        pick: (List<Double>) -> Double,
    ): List<Double> {
        val out = ArrayList<Double>()
        var group = ArrayList<Double>()
        for (value in values.sorted()) {
            if (group.isNotEmpty() && value - group.last() > tolerance) {
                if (group.size >= support) out.add(pick(group))
                group = ArrayList()
            }
            group.add(value)
        }
        if (group.size >= support) out.add(pick(group))
        return out
    }

    /** Las fronteras sin repetir: dos que caen a menos de un punto son la misma línea. */
    private fun merge(edges: List<Double>): List<Double> {
        val out = ArrayList<Double>()
        for (edge in edges.sorted()) {
            if (out.isEmpty() || edge - out.last() > 1.0) out.add(edge)
        }
        return out
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
     * Las tablas `ToUnicode` de las fuentes del archivo, por el nombre que usa el contenido
     * (`/F1`, `/F2`…). Hay bancos —la CMR— que escriben el texto con una fuente propia, donde cada
     * número es una letra elegida por el programa que armó el PDF: sin la tabla, el flujo no dice
     * nada. La tabla sí: `<0035> <0035> <0053>` quiere decir que el 0x35 se dibuja como una `S`.
     */
    private fun cMaps(bytes: ByteArray): Map<String, Map<Int, String>> {
        val objects = objects(bytes)
        if (objects.isEmpty()) return emptyMap()
        val out = HashMap<String, Map<Int, String>>()
        val fontBlock = Regex("""/Font\s*<<(.*?)>>""", RegexOption.DOT_MATCHES_ALL)
        val resource = Regex("""/(\w+)\s+(\d+)\s+0\s+R""")
        val toUnicode = Regex("""/ToUnicode\s+(\d+)\s+0\s+R""")
        for (body in objects.values) {
            val fonts = fontBlock.find(body)?.groupValues?.get(1) ?: continue
            for (name in resource.findAll(fonts)) {
                val font = objects[name.groupValues[2].toIntOrNull() ?: continue] ?: continue
                val table = objects[toUnicode.find(font)?.groupValues?.get(1)?.toIntOrNull() ?: continue]
                    ?: continue
                val map = parseCMap(streamOf(table) ?: continue)
                if (map.isNotEmpty()) out.putIfAbsent(name.groupValues[1], map)
            }
        }
        return out
    }

    /** Los objetos del archivo por número: su diccionario y, si tiene, su flujo. */
    private fun objects(bytes: ByteArray): Map<Int, String> {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val out = HashMap<Int, String>()
        for (match in Regex("""(\d+)\s+0\s+obj""").findAll(raw)) {
            if (out.size > MAX_OBJECTS) break
            val number = match.groupValues[1].toIntOrNull() ?: continue
            val start = match.range.last + 1
            val end = raw.indexOf("endobj", start).let { if (it < 0) raw.length else it }
            out[number] = raw.substring(start, end)
        }
        return out
    }

    /** El contenido de un flujo de un objeto, ya descomprimido si venía comprimido. */
    private fun streamOf(body: String): String? {
        val start = body.indexOf(STREAM)
        if (start < 0) return null
        var data = start + STREAM.length
        if (data < body.length && body[data] == '\r') data++
        if (data < body.length && body[data] == '\n') data++
        val end = body.indexOf(END_STREAM, data)
        if (end < 0) return null
        val raw = body.substring(data, end)
        return inflate(raw.toByteArray(Charsets.ISO_8859_1))?.let { String(it, Charsets.ISO_8859_1) } ?: raw
    }

    /** Una tabla `ToUnicode`: qué letra es cada código. */
    private fun parseCMap(text: String): Map<Int, String> {
        val out = HashMap<Int, String>()
        val hex = Regex("""<([0-9A-Fa-f]+)>""")
        val chars = Regex("""beginbfchar(.*?)endbfchar""", RegexOption.DOT_MATCHES_ALL)
        val ranges = Regex("""beginbfrange(.*?)endbfrange""", RegexOption.DOT_MATCHES_ALL)
        for (block in chars.findAll(text)) {
            val values = hex.findAll(block.groupValues[1]).map { it.groupValues[1] }.toList()
            for (index in values.indices step 2) {
                val code = values[index].toIntOrNull(16) ?: continue
                if (index + 1 < values.size) out[code] = utf16(values[index + 1])
            }
        }
        for (block in ranges.findAll(text)) parseRanges(block.groupValues[1], out)
        return out
    }

    /**
     * Los rangos de una tabla `ToUnicode`: `<0003> <0003> <0020>` es un código y su letra, y
     * `<0024> <003D> <0041>` es una fila de códigos seguidos que empieza en la `A`.
     */
    private fun parseRanges(block: String, out: MutableMap<Int, String>) {
        var index = 0

        fun next(): String? {
            while (index < block.length && block[index] != '<' && block[index] != '[') index++
            if (index >= block.length) return null
            val close = block.indexOf(if (block[index] == '[') ']' else '>', index)
            if (close < 0) return null
            val token = block.substring(index + 1, close)
            index = close + 1
            return token
        }

        while (out.size < MAX_CMAP && index < block.length) {
            val from = next()?.toIntOrNull(16) ?: return
            val to = next()?.toIntOrNull(16) ?: return
            if (to < from) return
            while (index < block.length && block[index].isWhitespace()) index++
            if (index < block.length && block[index] == '[') {
                val list = next() ?: return
                Regex("""<([0-9A-Fa-f]+)>""").findAll(list).forEachIndexed { offset, item ->
                    out[from + offset] = utf16(item.groupValues[1])
                }
            } else {
                val target = next() ?: return
                val base = utf16(target)
                for (code in from..minOf(to, from + MAX_CMAP)) {
                    out[code] = if (code == from) base else base.dropLast(1) + (base.last() + (code - from))
                }
            }
        }
    }

    /** Una cadena de la tabla, que viene en UTF-16 de dos bytes por letra. */
    private fun utf16(hex: String): String {
        val digits = if (hex.length % 2 == 0) hex else "0$hex"
        return buildString {
            var index = 0
            while (index + 3 < digits.length + 1 && index + 4 <= digits.length) {
                digits.substring(index, index + 4).toIntOrNull(16)?.let { append(it.toChar()) }
                index += 4
            }
        }.ifEmpty { hex.chunked(2).mapNotNull { it.toIntOrNull(16)?.toChar() }.joinToString("") }
    }

    private fun runs(stream: String, cMaps: Map<String, Map<Int, String>>): List<Run> {
        val out = ArrayList<Run>()
        var x = 0.0
        var y = 0.0
        var size = 0.0
        var leading = 0.0
        var font = ""
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
                    font = stream.substring(i + 1, end)
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
                            val text = translate(pending.toString(), cMaps[font])
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
                    if (depth == 0) return out.toString() to i
                    out.append(c)
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString() to i
    }

    private fun hexText(hex: String): String {
        val digits = hex.filter { it.isLetterOrDigit() }
        val bytes = ByteArray(digits.length / 2) { index ->
            digits.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    /**
     * El texto de un trozo. Si la fuente trae su tabla `ToUnicode`, el trozo son códigos y la tabla
     * dice qué letra es cada uno; hay tablas de un byte por código y tablas de dos, y se prueban las
     * dos quedándose con la que deja menos códigos sin traducir. Sin tabla, se decodifica como
     * siempre.
     */
    private fun translate(raw: String, map: Map<Int, String>?): String {
        if (map.isNullOrEmpty() || raw.isEmpty()) return decode(raw)
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        val single = byCodes(bytes, 1, map)
        val double = byCodes(bytes, 2, map)
        val best = if (double.second < single.second) double else single
        return best.first
    }

    /** El trozo leído de a [width] bytes por código, y cuántos códigos no estaban en la tabla. */
    private fun byCodes(bytes: ByteArray, width: Int, map: Map<Int, String>): Pair<String, Int> {
        val out = StringBuilder()
        var missing = 0
        var index = 0
        while (index + width <= bytes.size) {
            var code = 0
            for (step in 0 until width) code = (code shl 8) or (bytes[index + step].toInt() and 0xFF)
            val text = map[code]
            if (text == null) {
                missing++
                out.append((code and 0xFF).toChar())
            } else {
                out.append(text)
            }
            index += width
        }
        if (index < bytes.size) missing++
        return out.toString() to missing
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

    /**
     * Hasta dónde se busca la cabecera sobre la primera fila. El bloque se corta antes si aparece
     * un hueco más grande que el interlineado de la tabla.
     */
    private const val HEADER_ZONE = 40.0

    /** Un hueco de esta altura sobre la primera fila ya no es cabecera: es el título de arriba. */
    private const val HEADER_GAP = 14.0

    /** Dos trozos con la `y` a menos de esto son la misma fila. */
    private const val LINE_TOLERANCE = 1.5

    /** Un hueco de más de dos puntos dentro de una fila es otra celda. */
    private const val CELL_GAP = 2.0

    /** Dos textos que empiezan a menos de esto empiezan en la misma columna. */
    private const val START_TOLERANCE = 3.0

    /** Dos montos que terminan a menos de esto terminan en la misma columna. */
    private const val END_TOLERANCE = 2.0

    /**
     * Un monto como los escribe el banco: `$ 30.600`, `-775.655`, `0`. Sin espacios ni barras: una
     * fecha (`05/06`) también es puros dígitos y no es plata.
     */
    private val MONEY = Regex("""^[+-]?\s*\$?\s*\d[\d.,]*$""")

    /** El ancho que se le supone a un carácter que no está en la tabla. */
    private const val DEFAULT_WIDTH = 556

    /** Un archivo con más objetos que esto no es una cartola: no se sigue leyendo. */
    private const val MAX_OBJECTS = 5_000

    /** Tope de códigos por tabla `ToUnicode`, para que una tabla absurda no llene la memoria. */
    private const val MAX_CMAP = 5_000

    /**
     * Los anchos de Helvetica, en milésimas de em: son los que usa el banco para alinear los montos
     * a la derecha. Con la tabla completa no hace falta adivinar con un promedio: un `$ 208.960`
     * ocupa más que un `$ 600`, y por eso terminan en el mismo lugar aunque empiecen en otro.
     */
    private val WIDTHS = IntArray(256) { DEFAULT_WIDTH }.also { widths ->
        val upper = intArrayOf(
            667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833,
            722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611,
        )
        val lower = intArrayOf(
            556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833,
            556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500,
        )
        for (letter in 0..25) {
            widths['A'.code + letter] = upper[letter]
            widths['a'.code + letter] = lower[letter]
        }
        for (digit in '0'..'9') widths[digit.code] = 556
        for (narrow in " .,:;'|/()[]") widths[narrow.code] = 278
        widths['$'.code] = 556
        widths['%'.code] = 889
        widths['+'.code] = 584
        widths['='.code] = 584
        widths['*'.code] = 389
        widths['#'.code] = 556
        widths['&'.code] = 667
        widths['@'.code] = 1015
        widths['_'.code] = 556
        widths['\\'.code] = 278
    }

    /** Las letras con tilde ocupan lo mismo que su letra sin tilde. */
    private val ACCENTS = mapOf(
        'á' to 'a', 'à' to 'a', 'é' to 'e', 'è' to 'e', 'í' to 'i', 'ì' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'ñ' to 'n',
        'Á' to 'A', 'À' to 'A', 'É' to 'E', 'È' to 'E', 'Í' to 'I', 'Ì' to 'I',
        'Ó' to 'O', 'Ò' to 'O', 'Ú' to 'U', 'Ù' to 'U', 'Ü' to 'U', 'Ñ' to 'N',
    )

    /**
     * Cuánto ocupa el texto, en puntos. No hace falta la fuente exacta del banco: con los anchos
     * de Helvetica —la de casi todas las cartolas— alcanza para saber dónde termina un monto.
     */
    private fun width(text: String, size: Double): Double {
        var units = 0
        for (character in text) {
            val glyph = ACCENTS[character] ?: character
            units += if (glyph.code < WIDTHS.size) WIDTHS[glyph.code] else DEFAULT_WIDTH
        }
        return units * size / 1000.0
    }

    /** Una línea vertical que no llega a esta parte de la más alta no es borde de columna. */
    private const val RULE_SHARE = 0.6

    private val SHORT_DATE = Regex("""(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?""")

    private val FULL_DATE = Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""")

    /** Un renglón de la tarjeta sin cabecera: la fecha y, en el mismo trozo, el comercio. */
    private val DATE_AND_TEXT = Regex("""^(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\s+(\S.*)$""")

    /** La cuota de la compra (`02/03`): no es plata, y separa el precio de la cuota del mes. */
    private val INSTALLMENT = Regex("""^\d{1,2}/\d{1,2}$""")

    /** El mes de facturación de la fila (`nov-2026`): es una columna, no parte del comercio. */
    private val MONTH = Regex("""^[a-z]{3}-\d{4}$""", RegexOption.IGNORE_CASE)

    /**
     * Los títulos que se le ponen a la tabla que el banco no tituló, con los nombres que reconoce
     * `StatementTemplate`: sin ellos no hay forma de decir qué columna es el monto, y una tabla sin
     * títulos se puede leer de dos maneras opuestas.
     */
    private val HEADERLESS_HEADER = listOf("Fecha", "Descripción", "Monto", "Cuota", "Valor cuota")

    /** Menos renglones que esto no son una tabla: es la hoja del resumen de cupos. */
    private const val HEADERLESS_ROWS = 3
}
