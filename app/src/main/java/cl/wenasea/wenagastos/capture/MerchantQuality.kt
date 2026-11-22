package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.domain.TextNorm

/**
 * Distingue un nombre que sirve para reconocer el movimiento de una frase que solo describe el
 * aviso.
 *
 * Cuando el banco no dice quién pagó o quién transfirió, lo que queda ("tu Cuenta Banco Falabella
 * 8777", "Transferencia recibida") no es un comercio: sirve para mostrar la fila, pero no para
 * darla por buena. Con esta marca el motor sabe qué movimiento todavía espera que llegue la otra
 * fuente del mismo evento (el correo de la contraparte, el push del banco) y puede completarlo.
 */
object MerchantQuality {

    /** Frases armadas por el aviso, no por el comercio. Ya normalizadas con [TextNorm.plain]. */
    private val GENERIC = listOf(
        Regex("""^(?:tu|su|mi|la|el)\s+(?:cuenta|tarjeta)\b"""),
        Regex("""^cuentas?\s+(?:corriente|rut|de\s+ahorro|vista)\b"""),
        Regex("""^transferencia(?:\s+recibida|\s+enviada|\s+entre\s+cuentas)?$"""),
        Regex("""^traspaso\b"""),
        Regex("""^movimiento\s"""),
        Regex("""^(?:recibiste|recibimos|te\s+transfirieron|compraste|pagaste|transferiste)\b"""),
        Regex("""^sin\s+comercio$"""),
    )

    /** `true` si el nombre no alcanza para confirmar el movimiento ni para enseñar una regla. */
    fun isGeneric(merchant: String?): Boolean {
        val plain = TextNorm.plain(merchant ?: return true)
        if (plain.isEmpty()) return true
        return GENERIC.any { it.containsMatchIn(plain) }
    }
}
