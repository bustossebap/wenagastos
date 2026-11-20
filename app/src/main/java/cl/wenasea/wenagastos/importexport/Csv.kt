package cl.wenasea.wenagastos.importexport

/**
 * CSV según RFC 4180: comas, comillas y saltos de línea entre comillas dobles. Lo que la
 * planilla recibe como `=SUMA(...)` en un nombre de comercio se neutraliza con un apóstrofo,
 * así un aviso del banco no puede ejecutar una fórmula al abrir el archivo.
 */
object Csv {

    /**
     * Excel abre bien UTF-8 solo si el archivo empieza con BOM; sin él, "Súper" queda "SÃºper".
     * Los visores decentes ignoran el BOM, así que no molesta.
     */
    const val BOM: String = "\uFEFF"

    private const val SEPARATOR = ','
    private const val NEWLINE = "\r\n"

    fun write(headers: List<String>, rows: List<List<String>>): String {
        val out = StringBuilder(BOM)
        out.append(escapeRow(headers))
        for (row in rows) {
            out.append(NEWLINE).append(escapeRow(row))
        }
        out.append(NEWLINE)
        return out.toString()
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
