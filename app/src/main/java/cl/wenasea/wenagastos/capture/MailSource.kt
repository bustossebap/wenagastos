package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.domain.TextNorm

/**
 * Correos del banco, para los movimientos que no avisan por push.
 *
 * Banco Falabella no publica notificación de una transferencia ni la deja en su buzón
 * interno: el único rastro automático es el correo, y en el teléfono quien publica ese
 * correo en la barra es Gmail. Sin esta fuente una transferencia solo se puede anotar a mano.
 *
 * La notificación de Gmail trae el **remitente en el título**, así que se filtra por ahí:
 * si el remitente no está en [senders] no se guarda nada. Ese descarte ocurre antes de
 * tocar la base, para que Wenagastos no retenga correos ajenos ni en el registro de captura.
 */
object MailSource {

    /** App de correo que publica la notificación. */
    const val PACKAGE = "com.google.android.gm"

    /** Un remitente aceptado y el banco al que pertenece. */
    data class Sender(val name: String, val source: BankSource)

    /**
     * Remitentes aceptados, ya normalizados con [TextNorm.plain].
     *
     * Gmail muestra el nombre visible del remitente ("Banco Falabella"), pero si el usuario
     * lo tiene guardado en contactos puede aparecer otro nombre: por eso también se acepta
     * el dominio, que suele quedar en el texto de la notificación.
     */
    val senders: List<Sender> = listOf(
        Sender("banco falabella", BankSource.BANCO_FALABELLA),
        Sender("bancofalabella", BankSource.BANCO_FALABELLA),
        Sender("bancofalabella.cl", BankSource.BANCO_FALABELLA),
        // MACH (BCI) avisa la transferencia a quien la recibe. Se compara con el texto completo
        // ("machbank" y no "mach") porque "machine", "machala" y parecidos empiezan igual.
        Sender("machbank", BankSource.MACH),
        Sender("machbank.cl", BankSource.MACH),
    )

    /** Banco al que pertenece el correo, o `null` si el remitente no está en la lista. */
    fun of(text: String): BankSource? {
        val plain = TextNorm.plain(text)
        if (plain.isEmpty()) return null
        // Gana el remitente que aparece primero: en el cuerpo, un correo de MACH nombra
        // "Banco Falabella" como banco destino, y eso no lo vuelve un correo del Falabella.
        return senders
            .mapNotNull { sender ->
                plain.indexOf(sender.name).takeIf { it >= 0 }?.let { at -> at to sender.source }
            }
            .minByOrNull { it.first }
            ?.second
    }

    /**
     * Banco de una notificación de Gmail. Mira primero el título (ahí va el remitente) y recién
     * después el cuerpo, porque un contacto guardado puede cambiar el nombre visible y dejar el
     * dominio más abajo.
     */
    fun of(raw: RawNotification): BankSource? {
        of(listOfNotNull(raw.title, raw.subText).joinToString(" "))?.let { return it }
        return of(listOfNotNull(raw.text, raw.bigText, raw.textLines).joinToString(" "))
    }
}
