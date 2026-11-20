package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity

/**
 * Números de 4 dígitos que el banco imprime en los avisos de una cuenta: los de la tarjeta
 * en las compras y los de la cuenta en las transferencias. Una misma cuenta puede tener
 * varios (ej. el número de la cuenta corriente y el de su tarjeta de débito).
 *
 * Se guardan separados por coma en [AccountEntity.last4]; acá vuelven como lista, sin
 * vacíos, sin repetidos y en el orden en que los agregó el usuario.
 */
fun AccountEntity.numbers(): List<String> = parseNumbers(last4)

/** Lee la lista separada por coma descartando lo que no sea un número de 4 dígitos. */
fun parseNumbers(csv: String?): List<String> =
    csv.orEmpty()
        .split(',')
        .map(String::trim)
        .filter { it.length == 4 && it.all(Char::isDigit) }
        .distinct()

/** Normaliza para guardar: se queda con los 4 dígitos de cada entrada y quita repetidos. */
fun normalizeNumbers(numbers: Iterable<String>): String? =
    numbers
        .map { it.filter(Char::isDigit).take(4) }
        .filter { it.length == 4 }
        .distinct()
        .joinToString(",")
        .ifEmpty { null }
