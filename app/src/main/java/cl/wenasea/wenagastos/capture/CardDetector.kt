package cl.wenasea.wenagastos.capture

/**
 * Detecta los últimos 4 dígitos de la tarjeta y si es de crédito o débito.
 * Sirve para asignar el movimiento a la cuenta correcta.
 */
object CardDetector {

    private val LAST4_PATTERNS = listOf(
        Regex(
            """(?:terminad[ao]s?|finalizad[ao]|acabad[ao])\s+en\s+(?:\*+|x+|•+)?\s*(\d{4})""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""(?:\*{2,}|•{2,}|x{4,}|\.{3,})\s?(\d{4})\b"""),
        Regex(
            """(?:tarjeta|t\.?c\.?r\.?)\D{0,25}?(\d{4})\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    fun last4(text: String): String? {
        LAST4_PATTERNS.forEach { pattern ->
            val match = pattern.find(text) ?: return@forEach
            return match.groupValues[1]
        }
        return null
    }

    fun isCredit(text: String): Boolean =
        Regex("""\b(cr[eé]dito|cmr|t\.?\s?de\s+cr[eé]dito)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    fun isDebit(text: String): Boolean =
        Regex("""\b(d[eé]bito|t\.?\s?de\s+d[eé]bito)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}
