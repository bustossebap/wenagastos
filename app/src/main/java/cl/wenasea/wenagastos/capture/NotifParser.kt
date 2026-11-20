package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.TextNorm

/** Resultado de leer una notificación bancaria. */
data class ParsedNotification(
    val amount: Long,
    val merchant: String,
    /** `false` si el comercio se inventó a partir del título: el movimiento queda "Por revisar". */
    val merchantFound: Boolean,
    val direction: Direction,
    val last4: String?,
    val cardIsCredit: Boolean,
    val source: BankSource,
    val occurredAt: Long,
    val rawText: String,
    /** Traspaso entre cuentas propias: la plata no se gastó, solo cambió de cuenta. */
    val isTransfer: Boolean = false,
) {
    /** Confianza suficiente para registrar el movimiento como confirmado. */
    val isConfident: Boolean get() = amount > 0 && (merchantFound || isTransfer)
}

sealed interface ParseResult {
    data class Parsed(val notification: ParsedNotification) : ParseResult

    data class Rejected(val reason: String) : ParseResult
}

/**
 * Traduce una notificación a un movimiento.
 *
 * Rechaza solo dos casos: avisos que no son movimientos (claves, promociones, versiones)
 * y textos donde no hay ningún monto en pesos. Si falta el comercio igual se registra,
 * pero marcado como "Por revisar" para que lo completes a mano.
 */
object NotifParser {

    private val NOISE = listOf(
        Regex(
            """(codigo|clave|otp|token|pin)\s*(de|:)?\s*(verificacion|seguridad|acceso|dinamico)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""(inicia|iniciar)\s+sesion""", RegexOption.IGNORE_CASE),
        Regex("""bienvenid[oa]""", RegexOption.IGNORE_CASE),
        Regex("""(actualizamos|nueva\s+version|mantencion|mantenimiento)""", RegexOption.IGNORE_CASE),
        Regex("""(agregaste|agregamos|bloqueaste|desbloqueaste)\s+tu\s+tarjeta""", RegexOption.IGNORE_CASE),
        Regex("""(promocion|beneficio|descuento\s+exclusivo|sorteo)""", RegexOption.IGNORE_CASE),
        Regex("""\b(tu\s+clave|clave\s+de\s+acceso)\b""", RegexOption.IGNORE_CASE),
    )

    fun parse(raw: RawNotification, source: BankSource): ParseResult {
        val text = raw.fullText
        if (text.isBlank()) return ParseResult.Rejected("Notificación sin texto")

        // Los avisos que no son movimientos se comparan sin tildes ni mayúsculas.
        val normalized = TextNorm.plain(text)
        if (NOISE.any { it.containsMatchIn(normalized) }) {
            return ParseResult.Rejected("Aviso no transaccional")
        }

        val amount = AmountExtractor.find(text)
            ?: return ParseResult.Rejected(
                if (AmountExtractor.looksLikeBalanceOnly(text)) {
                    "Es un saldo, no un movimiento"
                } else {
                    "No encontré un monto en pesos"
                },
            )

        // El sentido sale del cuerpo: el título suele ser el nombre del banco
        // ("Mercado Pago" haría parecer gasto un cobro recibido).
        val body = listOf(raw.text, raw.bigText, raw.textLines)
            .filterNotNull()
            .joinToString(" ")
            .trim()
        val direction = DirectionDetector.detect(body.ifBlank { text })
            ?: DirectionDetector.detect(text)
            ?: Direction.DEBIT

        // Traspaso entre cuentas propias: sin destinatario, "tu cuenta" es el otro extremo.
        val extracted = MerchantExtractor.find(text)
        val isTransfer = extracted == null && TransferDetector.isOwnAccountTransfer(text)

        val fallback = fallbackMerchant(raw, source)
        val merchant = when {
            // Un traspaso entre cuentas propias no tiene comercio: se etiqueta por lo que es.
            isTransfer -> TRANSFER_MERCHANT
            extracted != null -> extracted
            else -> fallback.name
        }

        return ParseResult.Parsed(
            ParsedNotification(
                amount = amount,
                merchant = merchant,
                merchantFound = extracted != null || fallback.isMerchant,
                direction = direction,
                last4 = CardDetector.last4(text),
                cardIsCredit = CardDetector.isCredit(text),
                source = source,
                occurredAt = raw.postedAt,
                rawText = text,
                isTransfer = isTransfer,
            ),
        )
    }

    private data class Fallback(val name: String, val isMerchant: Boolean)

    /**
     * Cuando el texto no dice el comercio se usa el título, pero solo si no es el nombre
     * de la app (Google Wallet manda el comercio en el título; el banco manda "Santander").
     */
    private fun fallbackMerchant(raw: RawNotification, source: BankSource): Fallback {
        val candidate = raw.title.trim()
        val looksLikeName = candidate.length in 2..40 && candidate.any { it.isLetter() }
        val isAppName = TextNorm.plain(candidate) in APP_NAMES || candidate == source.label

        return when {
            looksLikeName && !isAppName -> Fallback(candidate, isMerchant = true)
            else -> Fallback("Movimiento ${source.label}", isMerchant = false)
        }
    }

    private val APP_NAMES = setOf(
        "google wallet",
        "wallet",
        "santander",
        "banco santander",
        "falabella",
        "cmr falabella",
        "mercado pago",
        "mercadopago",
        "tenpo",
        "banco de chile",
    )

    /** Nombre con el que se muestra un traspaso entre cuentas propias (no hay comercio). */
    private const val TRANSFER_MERCHANT = "Transferencia"
}
