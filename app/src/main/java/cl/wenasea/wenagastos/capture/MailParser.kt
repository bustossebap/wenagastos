package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.TextNorm
import java.time.LocalDateTime

/**
 * Lee el correo del banco que Gmail publica en la barra y lo convierte en un movimiento.
 *
 * Banco Falabella no avisa una transferencia por push ni la deja en su buzón interno: el único
 * rastro es el correo, y su cuerpo trae todo campo por campo:
 *
 * ```
 * Aviso de transferencia de fondos realizada
 * Detalle
 * Nombre destinatario
 * Nico Bustos
 * Banco
 * BCI MACHBANK
 * Número de cuenta
 * 777921040931
 * Monto transferencia
 * $2.000
 * Cuenta de origen
 * Cuenta Corriente 19834048777
 * Fecha
 * 21-11-2026
 * Hora
 * 16:27
 * Número de operación
 * 123922
 * ```
 *
 * Por eso se leen los campos por su etiqueta y no con patrones sueltos: el RUT y el número de
 * cuenta del destinatario son números largos que un extractor a ciegas podría tomar por monto,
 * y el número de cuenta del destinatario no es la cuenta del usuario (la propia es la de origen).
 *
 * Se exige que el correo anuncie un movimiento y traiga un monto en pesos: una promoción
 * ("hasta $50.000 de descuento") se descarta antes de tocar la base.
 */
object MailParser {

    /** Correos del banco que no son un movimiento: promociones, avisos y resúmenes. */
    private val NOISE = listOf(
        Regex("""correos?\s+nuevos?""", RegexOption.IGNORE_CASE),
        Regex(
            """(promocion|beneficio|descuento|sorteo|cupon|oferta|premio)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """(clave|contrasena|password|pin|otp)\s+(de|para|nueva|nuevo)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """(actualizamos|nueva\s+version|mantencion|mantenimiento|terminos\s+y\s+condiciones)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""(resumen|estado)\s+de\s+cuenta""", RegexOption.IGNORE_CASE),
    )

    /** Frases con las que el banco anuncia que hubo plata de por medio. */
    private val MOVEMENT = listOf(
        Regex("""aviso\s+de\s+transferencia""", RegexOption.IGNORE_CASE),
        Regex("""transferencia\s+de\s+fondos""", RegexOption.IGNORE_CASE),
        Regex("""(realizaste|efectuaste|hiciste)\s+una\s+transferencia""", RegexOption.IGNORE_CASE),
        Regex("""\btransferiste\b""", RegexOption.IGNORE_CASE),
        Regex("""comprobante\s+de""", RegexOption.IGNORE_CASE),
        Regex("""(consumo|compra)\s+(con|en)\s+tu\s+tarjeta""", RegexOption.IGNORE_CASE),
        Regex("""(cargo|abono|deposito)\s+(en|a)\s+tu\s+cuenta""", RegexOption.IGNORE_CASE),
    )

    /** "Transferencia a Nico Bustos": el asunto repite el destino cuando falta el nombre. */
    private val SUBJECT_TARGET = Regex(
        """(?:transferencia|transferiste|envio|pago)\s+(?:a|para)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val DATE = Regex("""(\d{1,2})[-/](\d{1,2})[-/](\d{4})""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")

    private const val ONE_DAY = 24 * 60 * 60_000L

    fun parse(raw: RawNotification, source: BankSource): ParseResult {
        val body = body(raw)
        val normalized = TextNorm.plain("${raw.title}\n$body")
        if (normalized.isEmpty()) return ParseResult.Rejected("Correo sin texto")

        if (NOISE.any { it.containsMatchIn(normalized) }) {
            return ParseResult.Rejected("Correo que no es un movimiento")
        }
        // Sin esta señal, cualquier correo con un monto (una promo, un convenio) se guardaría.
        if (MOVEMENT.none { it.containsMatchIn(normalized) }) {
            return ParseResult.Rejected("Correo que no es un movimiento")
        }

        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amount = field(lines, "monto transferencia", "monto del movimiento", "monto")
            ?.let { AmountExtractor.find(it) }
            ?: AmountExtractor.find(body)
            ?: return ParseResult.Rejected(
                if (AmountExtractor.looksLikeBalanceOnly(body)) {
                    "Es un saldo, no un movimiento"
                } else {
                    "No encontré un monto en pesos"
                },
            )

        val direction = direction(lines, body)
        val target = target(lines, direction)
        // Sin destinatario, "hacia/desde tu cuenta" es un traspaso entre cuentas propias.
        val isTransfer = target == null && TransferDetector.isOwnAccountTransfer(body)
        val merchant = when {
            isTransfer -> TRANSFER
            target != null -> target
            else -> "Movimiento ${source.label}"
        }

        return ParseResult.Parsed(
            ParsedNotification(
                amount = amount,
                merchant = merchant,
                merchantFound = target != null || isTransfer,
                direction = direction,
                last4 = originLast4(lines),
                // Una transferencia sale de una cuenta corriente, no de una tarjeta de crédito.
                cardIsCredit = false,
                source = source,
                occurredAt = occurredAt(lines, raw.postedAt),
                rawText = body,
                isTransfer = isTransfer,
            ),
        )
    }

    /** El cuerpo completo: Gmail manda en `bigText` el texto del correo con sus saltos de línea. */
    private fun body(raw: RawNotification): String {
        raw.bigText?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return listOfNotNull(raw.text, raw.textLines)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** Valor de un campo: la primera línea no vacía que sigue a su etiqueta. */
    private fun field(lines: List<String>, vararg labels: String): String? {
        for (label in labels) {
            val index = lines.indexOfFirst { TextNorm.plain(it).trim(' ', ':', '.') == label }
            if (index >= 0) {
                val value = lines.drop(index + 1).firstOrNull { it.isNotBlank() }?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    /**
     * El sentido sale del lado propio que nombra el correo: "Cuenta de origen" es plata que salió
     * y "Cuenta de destino" plata que entró. Los verbos, cuando están, tienen la última palabra.
     */
    private fun direction(lines: List<String>, body: String): Direction {
        DirectionDetector.detect(body)?.let { return it }
        val outgoing = field(lines, "cuenta de origen", "nombre destinatario", "destinatario") != null
        val incoming = field(lines, "cuenta de destino", "nombre remitente", "remitente") != null
        return if (incoming && !outgoing) Direction.CREDIT else Direction.DEBIT
    }

    /** A quién fue (o de quién vino) la plata. */
    private fun target(lines: List<String>, direction: Direction): String? {
        val labels = if (direction == Direction.DEBIT) {
            arrayOf("nombre destinatario", "nombre del destinatario", "destinatario")
        } else {
            arrayOf("nombre remitente", "nombre del remitente", "remitente")
        }
        field(lines, *labels)?.let { return cleanName(it) }
        field(lines, "asunto")?.let { subject ->
            SUBJECT_TARGET.find(subject)?.groupValues?.get(1)?.let { return cleanName(it) }
        }
        return null
    }

    private fun cleanName(raw: String): String? {
        val value = raw.trim().trim(' ', ',', '.', ';', ':', '-', '·')
            .replace(Regex("""\s+"""), " ")
        if (value.length !in 2..60) return null
        if (value.none { it.isLetter() }) return null
        return value
    }

    /**
     * Últimos 4 de la cuenta de origen. Se toma solo "Cuenta de origen": el número que aparece
     * antes, en "Número de cuenta", es la cuenta del destinatario y llevaría a la cuenta equivocada.
     */
    private fun originLast4(lines: List<String>): String? {
        val origin = field(lines, "cuenta de origen") ?: return null
        val digits = origin.filter(Char::isDigit)
        // Un número de cuenta chileno tiene 8 o más dígitos; menos es un folio o un monto.
        return if (digits.length >= 8) digits.takeLast(4) else null
    }

    /**
     * La hora del movimiento, no la de llegada del correo: Gmail agrupa y republica, así que la
     * notificación puede aparecer minutos después. Si la fecha no se puede leer, se usa la de
     * la notificación.
     */
    private fun occurredAt(lines: List<String>, postedAt: Long): Long {
        val date = DATE.find(field(lines, "fecha") ?: return postedAt) ?: return postedAt
        val day = date.groupValues[1].toIntOrNull() ?: return postedAt
        val month = date.groupValues[2].toIntOrNull() ?: return postedAt
        val year = date.groupValues[3].toIntOrNull() ?: return postedAt

        val time = field(lines, "hora")?.let { TIME.find(it) }
        val hour = time?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minute = time?.groupValues?.get(2)?.toIntOrNull() ?: 0

        val millis = runCatching {
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(Dates.zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull() ?: return postedAt

        // El correo se publica al instante: una fecha muy posterior a la llegada es una mala lectura.
        return millis.takeIf { it <= postedAt + ONE_DAY } ?: postedAt
    }

    /** Nombre con el que se muestra un traspaso entre cuentas propias (no hay destinatario). */
    private const val TRANSFER = "Transferencia"
}
