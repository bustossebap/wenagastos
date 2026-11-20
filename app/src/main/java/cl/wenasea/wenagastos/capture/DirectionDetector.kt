package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction

/**
 * Decide si el movimiento saca o mete plata, según el verbo de la notificación.
 * Si aparecen verbos de ambos lados gana el que aparece primero en el texto.
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
            """retiraste|transferiste|enviaste|debito|debitamos|suscripcion|cobro|cobramos|""" +
            """you\s+paid|paid|you\s+spent|spent|purchase|charged|withdrawal|withdrew)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun detect(text: String): Direction? {
        val normalized = TextNorm.plain(text)
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
}
