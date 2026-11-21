package cl.wenasea.wenagastos.data.local

/**
 * Cabecera del archivo: lo único que separa una base de texto plano de una cifrada.
 *
 * SQLite escribe siempre los mismos 16 bytes (`SQLite format 3\0`); SQLCipher escribe 16 bytes de
 * sal aleatoria. Se mira antes de abrir la base, así que sirve para decidir si hay que migrar sin
 * que Room toque el archivo todavía.
 */
object DbHeader {

    /** Cuántos bytes hay que leer para decidir. */
    const val SIZE = 16

    private val PLAIN = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * `true` si la cabecera es la de SQLite sin cifrar. Un archivo más corto que la cabecera no
     * alcanza para decidir y no se considera texto plano.
     */
    fun isPlainText(header: ByteArray): Boolean {
        if (header.size < PLAIN.size) return false
        for (index in PLAIN.indices) {
            if (header[index] != PLAIN[index]) return false
        }
        return true
    }
}
