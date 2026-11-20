package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.Direction
import kotlin.math.abs

/** Formato de moneda chilena sin decimales: 1234567 → "$1.234.567". */
object Money {

    fun format(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val digits = abs(amount).toString()
        val grouped = digits
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
        return "$sign$$grouped"
    }

    fun formatSigned(direction: Direction, amount: Long): String =
        if (direction == Direction.DEBIT) "-${format(amount)}" else "+${format(amount)}"

    /** Acepta "12.500", "$12500" o "12500" y devuelve 12500. */
    fun parse(text: String): Long? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toLongOrNull()?.takeIf { it > 0 }
    }

    /** Versión corta para ejes de gráficos: 1.250.000 → "$1,25M". */
    fun formatCompact(amount: Long): String = when {
        abs(amount) >= 1_000_000 -> "$" + trimZeros(amount / 1_000_000.0) + "M"
        abs(amount) >= 1_000 -> "$" + (amount / 1_000) + "k"
        else -> format(amount)
    }

    private fun trimZeros(value: Double): String {
        val rounded = String.format(java.util.Locale.US, "%.2f", value)
        return rounded.trimEnd('0').trimEnd('.').replace('.', ',')
    }
}
