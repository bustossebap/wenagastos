package cl.wenasea.wenagastos.capture

/**
 * Notificación ya extraída de [android.app.Notification], sin nada de Android
 * para poder parsearla y testearla como texto plano.
 */
data class RawNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val bigText: String? = null,
    val textLines: String? = null,
    val postedAt: Long,
    val key: String,
) {

    /** Título + cuerpo, sin repetir lo mismo dos veces (muchas apps mandan igual el texto). */
    val fullText: String
        get() = listOf(title, text, bigText, textLines)
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")
}
