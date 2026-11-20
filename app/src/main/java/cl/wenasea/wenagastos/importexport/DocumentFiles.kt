package cl.wenasea.wenagastos.importexport

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Lee y escribe el archivo que el usuario eligió en el selector del sistema (Storage Access
 * Framework). SAF da permiso solo sobre ese archivo, así que la app no pide almacenamiento ni
 * escribe en carpetas ajenas: el usuario decide dónde va su respaldo.
 */
class DocumentFiles(private val resolver: ContentResolver) {

    fun writeText(uri: Uri, text: String) = write(uri, text.toByteArray(Charsets.UTF_8))

    fun write(uri: Uri, bytes: ByteArray) {
        val output = resolver.openOutputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
        output.use { it.write(bytes) }
    }

    /**
     * Un respaldo son unos pocos cientos de KB. El tope evita que un archivo enorme elegido por
     * error se coma la memoria del teléfono antes de detectar que no es un respaldo.
     */
    fun read(uri: Uri, maxBytes: Int = MAX_BYTES): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                out.write(buffer, 0, read)
                if (out.size() > maxBytes) throw IOException("El archivo es muy grande para un respaldo")
            }
            return out.toByteArray()
        }
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024

        const val CSV_MIME = "text/csv"
        const val JSON_MIME = "application/json"

        /** El respaldo es binario: el selector lo deja guardar con cualquier nombre. */
        const val BACKUP_MIME = "application/octet-stream"
    }
}
