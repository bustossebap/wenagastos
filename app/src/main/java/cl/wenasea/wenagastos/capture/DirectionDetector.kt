package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.TextNorm

/**
 * Decide si el movimiento saca o mete plata, según el verbo de la notificación.
 * Si aparecen verbos de ambos lados gana el que aparece primero en el texto.
 *
 * Los traspasos entre cuentas propias no traen verbo ("se realizó una Transferencia hacia tu
 * cuenta ..."), pero la preposición dice todo: "hacia" entra y "desde" sale.
 */
object DirectionDetector {

    private val CREDIT = Regex(
        """\b(recibiste|recibimos|te\s+transfirio|te\s+transfirieron|abono|abonamos|abonado|""" +
            """deposito|depositaron|sueldo|devolucion|devolvimos|reembolso|reembolsamos|""" +
            """te\s+pagaron|ingreso|ganaste|""" +
            """received|refunded|deposited|credited|cashback)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val DEBIT = Regex(
        """\b(compraste|compra|cargamos|cargo|pagaste|pago|consumo|consumiste|giro|retiro|""" +
            """retiraste|avance|transferiste|enviaste|debito|debitamos|suscripcion|cobro|cobramos|""" +
            """you\s+paid|paid|you\s+spent|spent|purchase|charged|cash\s+advance|withdrawal|withdrew)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** La cuenta propia como destino: la plata entra a la casa. */
    private val INTO_OWN_ACCOUNT = Regex("""\b(?:hacia|a)\s+tu\s+cuenta\b""")

    /** La cuenta propia como origen: la plata sale (aunque sea hacia otra cuenta tuya). */
    private val FROM_OWN_ACCOUNT = Regex("""\bdesde\s+tu\s+cuenta\b""")

    fun detect(text: String): Direction? {
        val normalized = TextNorm.plain(text)
        ownAccountCue(normalized)?.let { return it }
        val credit = CREDIT.find(normalized)
        val debit = DEBIT.find(normalized)
        return when {
            credit == null && debit == null -> null
            credit == null -> Direction.DEBIT
            debit == null -> Direction.CREDIT
            credit.range.first <= debit.range.first -> Direction.CREDIT
            else -> Direction.DEBIT
        }
    }

    /** "hacia tu cuenta" entra, "desde tu cuenta" sale; si vienen las dos gana la primera. */
    private fun ownAccountCue(normalized: String): Direction? {
        val into = INTO_OWN_ACCOUNT.find(normalized)
        val from = FROM_OWN_ACCOUNT.find(normalized)
        return when {
            into == null && from == null -> null
            into == null -> Direction.DEBIT
            from == null -> Direction.CREDIT
            into.range.first <= from.range.first -> Direction.CREDIT
            else -> Direction.DEBIT
        }
    }
}
