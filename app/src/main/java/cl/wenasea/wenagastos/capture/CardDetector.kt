package cl.wenasea.wenagastos.capture

/**
 * Detecta los últimos 4 dígitos de la tarjeta o de la cuenta y si es de crédito o débito.
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

    /**
     * Número de cuenta completo, como lo escribe Santander en los traspasos:
     * "Transferencia hacia tu cuenta 000095441483". No hay tarjeta nombrada, así que
     * los patrones de arriba no lo ven; el `$` excluido evita leer un monto
     * ("cuenta por $ 1.250.000") como si fuera un número de cuenta.
     */
    private val ACCOUNT_NUMBER = Regex(
        """\bcuenta\b[^\d$]{0,20}?(\d[\d.\- ]{5,}\d)""",
        RegexOption.IGNORE_CASE,
    )

    /** Un número de cuenta chileno tiene 8 o más dígitos; menos que eso es un monto o un folio. */
    private const val MIN_ACCOUNT_DIGITS = 8

    fun last4(text: String): String? {
        LAST4_PATTERNS.forEach { pattern ->
            val match = pattern.find(text) ?: return@forEach
            return match.groupValues[1]
        }
        return accountLast4(text)
    }

    /** Últimos 4 dígitos del número de cuenta, si el aviso lo trae completo. */
    fun accountLast4(text: String): String? {
        ACCOUNT_NUMBER.findAll(text).forEach { match ->
            val digits = match.groupValues[1].filter(Char::isDigit)
            if (digits.length >= MIN_ACCOUNT_DIGITS) return digits.takeLast(4)
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
