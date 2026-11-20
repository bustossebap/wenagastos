package cl.wenasea.wenagastos.capture

/**
 * Reconoce los traspasos entre cuentas propias ("Transferencia desde/hacia tu cuenta").
 *
 * No basta con la palabra "transferencia" ni con "tu cuenta": un "Transferiste $50.000 a
 * MARIA SOTO desde tu cuenta corriente" es plata que se fue de verdad y cuenta como gasto,
 * y un "Recibiste una transferencia de JUAN PEREZ en tu cuenta" es un ingreso. Solo es
 * traspaso cuando el aviso nombra la cuenta propia **y no tiene destinatario**: esa plata
 * no se gastó, solo cambió de bolsillo.
 */
object TransferDetector {

    private val TRANSFER = Regex(
        """\b(transferencia|transferiste|transferi|transferir|transfiere|transfirio|""" +
            """traspaso|traspasaste|traspasar)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "hacia tu cuenta", "desde tus cuentas", "entre mis cuentas": la plata se mueve en casa. */
    private val OWN_ACCOUNT = Regex(
        """\b(?:hacia|desde|entre)\s+(?:tu|tus|mis)\s+cuentas?\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isOwnAccountTransfer(text: String): Boolean {
        val plain = TextNorm.plain(text)
        return TRANSFER.containsMatchIn(plain) && OWN_ACCOUNT.containsMatchIn(plain)
    }
}
