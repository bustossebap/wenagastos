package cl.wenasea.wenagastos.capture

/**
 * Extrae el monto en pesos chilenos del texto de una notificación bancaria.
 *
 * Reglas:
 * - Solo CLP. Si el texto menciona dólares, euros o UF se descarta (evita registrar en otra moneda).
 * - Los montos que vienen después de "saldo", "cupo" o "disponible" se ignoran,
 *   porque no son el movimiento sino el estado de la cuenta.
 * - `12.500` es doce mil quinientos; `12.500,50` son doce mil quinientos (se descartan los centavos).
 */
object AmountExtractor {

    private val FOREIGN = Regex(
        """(\busd\b|u\${'$'}s|us\${'$'}|\beur\b|€|\buf\b|d[oó]lares?\b|euros?\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Monto explícito: con `$`, con "CLP" o con la palabra "pesos". */
    private val MARKED = Regex(
        """(?:\${'$'}|clp|pesos)\s*([0-9][0-9]{0,3}(?:[.,][0-9]{3})*(?:,[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /** Verbo de movimiento seguido de un monto sin símbolo: "compraste 12.500". */
    private val VERB_AMOUNT = Regex(
        """(?:compraste|compra|pagaste|pago|recibiste|cargo|consumo|transferiste|abono|deposito)""" +
            """\D{0,12}([0-9][0-9]{0,3}(?:[.,][0-9]{3})+(?:,[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val BALANCE_CUES = Regex(
        """(saldo|cupo|disponible|acumulado|total\s+de\s+compras|l[ií]mite)""",
        RegexOption.IGNORE_CASE,
    )

    private data class Candidate(val amount: Long, val isBalance: Boolean)

    /** Monto del movimiento, o `null` si no hay uno confiable. */
    fun find(text: String): Long? {
        if (FOREIGN.containsMatchIn(text)) return null
        val marked = candidates(text)

        marked.firstOrNull { !it.isBalance }?.let { return it.amount }
        if (marked.isNotEmpty()) return null

        return VERB_AMOUNT.find(text.replace('\u00A0', ' '))
            ?.groupValues
            ?.get(1)
            ?.let(::parseClp)
    }

    /** `true` cuando el aviso trae montos, pero todos son saldo/cupo (no hubo movimiento). */
    fun looksLikeBalanceOnly(text: String): Boolean {
        if (FOREIGN.containsMatchIn(text)) return false
        val marked = candidates(text)
        return marked.isNotEmpty() && marked.all { it.isBalance }
    }

    private fun candidates(text: String): List<Candidate> {
        val clean = text.replace('\u00A0', ' ')
        return MARKED.findAll(clean).mapNotNull { match ->
            val amount = parseClp(match.groupValues[1]) ?: return@mapNotNull null
            val before = clean.substring(maxOf(0, match.range.first - 30), match.range.first)
            Candidate(amount, BALANCE_CUES.containsMatchIn(before))
        }.toList()
    }

    /** Convierte "12.500", "1.234.567", "12.500,50" o "12,500.00" a pesos. */
    fun parseClp(raw: String): Long? {
        val trimmed = raw.trim().trimEnd('.', ',').trim()
        if (trimmed.isEmpty()) return null
        val integerPart = when {
            trimmed.contains('.') && trimmed.contains(',') -> {
                // El último separador es el decimal; el otro es de miles.
                if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                    trimmed.substringBeforeLast(',').replace(".", "")
                } else {
                    trimmed.substringBeforeLast('.').replace(",", "")
                }
            }

            trimmed.contains(',') -> trimmed.replace(",", "")
            else -> trimmed.replace(".", "")
        }
        val value = integerPart.toLongOrNull() ?: return null
        return value.takeIf { it in 1..99_999_999L }
    }
}
