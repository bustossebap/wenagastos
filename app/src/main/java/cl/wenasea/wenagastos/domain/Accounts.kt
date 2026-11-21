package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind

/**
 * Números de 4 dígitos que el banco imprime en los avisos de una cuenta: los de la tarjeta
 * en las compras y los de la cuenta en las transferencias. Una misma cuenta puede tener
 * varios (ej. el número de la cuenta corriente y el de su tarjeta de débito), y la plata es
 * la misma: la tarjeta no es otra cuenta.
 *
 * [accountLast4] es uno solo y [cardLast4] guarda las tarjetas separadas por coma; acá
 * vuelven todos juntos, sin vacíos ni repetidos, para cruzar los avisos por número exacto.
 */
fun AccountEntity.numbers(): List<String> =
    (listOfNotNull(accountLast4?.let(::parseNumbers)?.firstOrNull()) + parseNumbers(cardLast4))
        .distinct()

/** Número de la cuenta (el de las transferencias), si lo tiene. */
fun AccountEntity.accountNumber(): String? = parseNumbers(accountLast4).firstOrNull()

/** Números de las tarjetas de la cuenta (los de las compras). */
fun AccountEntity.cardNumbers(): List<String> = parseNumbers(cardLast4)

/**
 * Una tarjeta de crédito o de débito: su número es de tarjeta y no de una cuenta que la respalde.
 * En el editor hay un solo campo para las dos cosas, pero se guardan en `cardLast4`.
 */
val AccountKind.cardOnly: Boolean
    get() = this == AccountKind.CREDIT || this == AccountKind.DEBIT

/** Deja un solo número de 4 dígitos, para [AccountEntity.accountLast4]. */
fun normalizeNumber(input: String?): String? =
    input?.filter(Char::isDigit)?.take(4)?.takeIf { it.length == 4 }

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

/**
 * Reparte lo que el editor escribe en el campo de número y en la lista de tarjetas. En una tarjeta
 * de crédito o débito no hay número de cuenta: lo escrito es de tarjeta y el número que ya estaba
 * guardado (el editor lo muestra en ese campo) se conserva junto con los demás.
 */
fun splitNumbers(
    kind: AccountKind,
    number: String?,
    cards: List<String>,
): Pair<String?, String?> =
    if (kind.cardOnly) {
        null to normalizeNumbers(listOfNotNull(normalizeNumber(number)) + cards)
    } else {
        normalizeNumber(number) to normalizeNumbers(cards)
    }
