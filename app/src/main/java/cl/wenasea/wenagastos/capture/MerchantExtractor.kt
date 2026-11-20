package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.domain.TextNorm

/**
 * Extrae el comercio (o el destinatario de una transferencia) del texto de la notificación.
 *
 * Se prueban los patrones en orden de confianza:
 * 1. Etiqueta explícita: "Comercio: LIDER EXPRESS".
 * 2. Texto entre comillas: «Uber Eats».
 * 3. "en <comercio>" — el caso más común: "Compraste $12.500 en LIDER EXPRESS con tu tarjeta".
 * 4. "a <destinatario>" / "de <remitente>" — para transferencias.
 */
object MerchantExtractor {

    private val LABELED = Regex(
        """(?:comercio|local|establecimiento|beneficiario|destinatario|remitente)\s*:?\s*(.{2,40}?)(?=[,.;]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    private val QUOTED = Regex("""[«"“']([^«»"”']{2,40})[»"”']""")

    private val AFTER_EN = Regex(
        """\ben\s+(.{2,42}?)(?=\s+(?:con|por|desde|usando|tu|su|el|la|los|las|del|de|""" +
            """a\s+las|el\s+dia)\b|[.,;:!¡?]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    /** Inglés: Google Wallet avisa "You paid CLP 12,500.00 with Visa ••••4321 at LIDER EXPRESS". */
    private val AFTER_AT = Regex(
        """\bat\s+(.{2,42}?)(?=\s+(?:with|using|via|on|for|from)\b|[.,;:!?]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    private val AFTER_A = Regex(
        """\ba\s+(.{2,42}?)(?=\s+(?:desde|con|por|tu|su|el|la|los|las)\b|[.,;:!?]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    private val AFTER_DE = Regex(
        """\bde\s+(.{2,42}?)(?=\s+(?:por|con|desde|tu|su|el|la|los|las)\b|[.,;:!?]|\s*$)""",
        RegexOption.IGNORE_CASE,
    )

    private val TRANSFER_VERB = Regex(
        """\b(transferiste|transferencia|enviaste|recibiste|te\s+transfirio|te\s+transfirieron)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Palabras que no son un comercio, ya normalizadas (sin tildes). */
    private val NOISE = setOf(
        "tu cuenta", "su cuenta", "la cuenta", "mi cuenta",
        "tu tarjeta", "su tarjeta", "la tarjeta", "esta tarjeta",
        "tarjeta de credito", "tarjeta de debito", "cuenta corriente", "cuenta rut",
        "cuotas", "el extranjero", "internet", "linea", "sucursal", "comercio",
        "mercado pago", "santander", "falabella", "banco de chile", "tenpo",
        "el dia de hoy", "hoy", "linea de credito", "tu compra", "la compra",
    )

    fun find(text: String): String? {
        val clean = text.replace('\u00A0', ' ')
        LABELED.find(clean)?.groupValues?.get(1)?.let { return clean(it) }
        QUOTED.find(clean)?.groupValues?.get(1)?.let { return clean(it) }
        // En una transferencia manda el destinatario/remitente, no el "en tu cuenta" del final.
        if (TRANSFER_VERB.containsMatchIn(clean)) {
            AFTER_A.find(clean)?.groupValues?.get(1)?.let { return clean(it) }
            AFTER_DE.find(clean)?.groupValues?.get(1)?.let { return clean(it) }
        }
        AFTER_EN.find(clean)?.groupValues?.get(1)?.let { return clean(it) }
        AFTER_AT.find(clean)?.groupValues?.get(1)?.let { return clean(it) }
        return null
    }

    private fun clean(raw: String): String? {
        var value = raw.trim().trim(' ', ',', '.', ';', ':', '-', '*', '·', '\n')
        value = value.replace(Regex("""[*\-]+$"""), "")
        value = value.replace(Regex("""\s+"""), " ").trim()
        if (value.length < 2) return null
        if (value.none { it.isLetter() }) return null
        val normalized = TextNorm.plain(value)
        if (normalized in NOISE) return null
        if (normalized.length < 2) return null
        return value
    }
}
