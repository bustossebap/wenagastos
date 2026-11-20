package cl.wenasea.wenagastos.domain

import java.text.Normalizer

/** Utilidades para comparar texto de notificaciones sin depender de tildes ni mayúsculas. */
object TextNorm {

    /** Minúsculas, sin tildes, sin espacios dobles. */
    fun plain(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Igual que [plain] pero además deja solo letras, dígitos y espacios. */
    fun key(text: String): String = plain(text)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
