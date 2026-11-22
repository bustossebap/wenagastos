package cl.wenasea.wenagastos.importexport

/**
 * CSV según RFC 4180: comas, comillas y saltos de línea entre comillas dobles. Lo que la
 * planilla recibe como `=SUMA(...)` en un nombre de comercio se neutraliza con un apóstrofo,
 * así un aviso del banco no puede ejecutar una fórmula al abrir el archivo.
 *
 * Al escribir es siempre coma y BOM, porque el archivo lo abre Excel. Al leer, en cambio, el
 * separador se deduce del propio archivo: una cartola exportada desde un Excel en español usa
 * `;`, porque la coma es el separador decimal, y hay bancos que separan con tabulación.
 */
object Csv {

    /**
     * Excel abre bien UTF-8 solo si el archivo empieza con BOM; sin él, "Súper" queda "SÃºper".
     * Los visores decentes ignoran el BOM, así que no molesta.
     */
    const val BOM: String = "\uFEFF"

    private const val SEPARATOR = ','
    private const val NEWLINE = "\r\n"

    /** Los separadores que aparecen en las cartolas: coma, punto y coma y tabulación. */
    private val DELIMITERS = charArrayOf(',', ';', '\t')

    /** Cuántas filas se miran para deducir el separador: la cabecera y un poco más. */
    private const val SAMPLE_ROWS = 10

    /**
     * Para deducir el separador alcanza con el principio del archivo. Una cartola puede pesar
     * varios MB y no tiene sentido recorrerla tres veces para elegir entre tres caracteres.
     */
    private const val SNIFF_CHARS = 8 * 1024

    /** Una hoja leída: el separador que se dedujo y las filas, en orden. */
    data class Sheet(val delimiter: Char, val rows: List<List<String>>)

    fun write(headers: List<String>, rows: List<List<String>>): String {
        val out = StringBuilder(BOM)
        out.append(escapeRow(headers))
        for (row in rows) {
            out.append(NEWLINE).append(escapeRow(row))
        }
        out.append(NEWLINE)
        return out.toString()
    }

    /**
     * Las filas del texto, en orden. Descarta el BOM, acepta CRLF, LF y CR como fin de fila, y
     * respeta las comillas: un separador, una comilla doble o un salto de línea dentro de un campo
     * entrecomillado son parte del campo, no estructura.
     *
     * El salto de línea final no inventa una fila vacía, pero una fila con campos vacíos sí se
     * devuelve: las columnas tienen que quedar alineadas para que el mapeo encuentre la fecha y el
     * monto en todas.
     */
    fun read(text: String): List<List<String>> = split(text).rows

    /** Como [read], pero además dice con qué separador se leyó, para poder mostrarlo. */
    fun split(text: String): Sheet {
        val delimiter = detect(text)
        return Sheet(delimiter, parse(text, delimiter))
    }

    /**
     * Qué separador usa el archivo. Se prueban los tres y gana el que arma la tabla más pareja:
     * una cartola con `;` leída con comas se parte donde la descripción trae una coma y deja de
     * tener columnas fijas, así que el mapeo no encontraría el monto.
     */
    fun detect(text: String): Char {
        val sample = text.take(SNIFF_CHARS)
        var best = SEPARATOR
        var bestScore = 0
        for (candidate in DELIMITERS) {
            val score = consistency(parse(sample, candidate))
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    /**
     * Cuánto explica el separador: el ancho que más se repite en las primeras filas, multiplicado
     * por cuántas filas lo comparten. Un separador que no parte nada saca cero, y uno que parte
     * distinto en cada fila —la coma de una descripción— saca menos que el que sí forma columnas.
     */
    private fun consistency(rows: List<List<String>>): Int {
        val widths = rows.take(SAMPLE_ROWS).map { it.size }.filter { it > 1 }
        if (widths.isEmpty()) return 0
        val mode = widths.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return 0
        return mode.key * mode.value
    }

    /**
     * El autómata de RFC 4180, con la indulgencia que pide un archivo hecho por un banco: una
     * comilla suelta en medio de un campo se toma como texto y una comilla que abre y no cierra se
     * queda con lo que venga, en vez de perder el archivo entero.
     */
    private fun parse(text: String, delimiter: Char): List<List<String>> {
        if (text.isEmpty()) return emptyList()
        val body = if (text.startsWith(BOM)) text.substring(1) else text

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var insideQuotes = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                insideQuotes -> when {
                    c == '"' && i + 1 < body.length && body[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }

                    c == '"' -> insideQuotes = false
                    else -> field.append(c)
                }

                c == '"' && field.isEmpty() -> insideQuotes = true
                c == delimiter -> {
                    row.add(field.toString())
                    field.setLength(0)
                }

                c == '\r' || c == '\n' -> {
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row)
                    row = ArrayList()
                    // CRLF cuenta como un solo fin de fila.
                    if (c == '\r' && i + 1 < body.length && body[i + 1] == '\n') i++
                }

                else -> field.append(c)
            }
            i++
        }
        // Lo que quedó sin cerrar solo es una fila si trae algo: el salto final no es una fila.
        if (field.isNotEmpty() || row.isNotEmpty() || insideQuotes) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    private fun escapeRow(row: List<String>): String = row.joinToString(SEPARATOR.toString()) { escape(it) }

    private fun escape(value: String): String {
        val safe = if (looksLikeFormula(value)) "'$value" else value
        val mustQuote = safe.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }
        if (!mustQuote) return safe
        return '"' + safe.replace("\"", "\"\"") + '"'
    }

    /** Un campo que la planilla tomaría como fórmula si empieza con estos caracteres. */
    private fun looksLikeFormula(value: String): Boolean =
        value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')
}
