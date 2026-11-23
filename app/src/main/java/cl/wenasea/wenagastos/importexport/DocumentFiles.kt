package cl.wenasea.wenagastos.importexport

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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
                if (out.size() > maxBytes) throw IOException("El archivo es demasiado grande para leerlo")
            }
            return out.toByteArray()
        }
    }

    /**
     * La cartola como hoja de filas. El banco la entrega en CSV, en Excel o —Santander y
     * Falabella— en PDF, y el tipo que el selector declara no siempre acierta, así que manda el
     * contenido: si el archivo empieza con `%PDF-` la tabla se saca del PDF; si es un ZIP, del libro
     * de Excel; si no, es un texto que se separa por comas.
     */
    fun statementSheet(uri: Uri, maxBytes: Int = MAX_BYTES): Csv.Sheet {
        val bytes = read(uri, maxBytes)
        if (Pdf.looksLikePdf(bytes)) {
            // El PDF no es un archivo dañado: es una cartola con un dibujo que no se pudo leer.
            return Pdf.sheet(bytes) ?: throw StatementException(
                "El PDF no trae una tabla de movimientos que sepa leer. " +
                    "Prueba con la cartola en CSV desde el sitio del banco.",
            )
        }
        if (Xlsx.looksLikeZip(bytes)) {
            return Xlsx.sheet(bytes) ?: throw StatementException(
                "El Excel no trae una hoja con los movimientos que sepa leer. " +
                    "Prueba con la planilla de movimientos o con la cartola en CSV.",
            )
        }
        if (Xlsx.looksLikeLegacy(bytes)) {
            // El .xls es un contenedor binario de los años noventa, no un ZIP: no se abre acá.
            throw StatementException(
                "El archivo es un Excel antiguo (.xls) y no lo puedo abrir. " +
                    "Ábrelo en Excel y guárdalo como .xlsx o como CSV.",
            )
        }
        return Csv.split(decode(bytes))
    }

    /**
     * El nombre con que el usuario conoce el archivo ("cartola-noviembre.csv"). Sirve para que el
     * corte de saldo diga de dónde vino. No todos los proveedores lo tienen: `null` es normal.
     */
    fun name(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    } catch (error: Exception) {
        // Un proveedor que no responde no puede tumbar la importación: el nombre es un adorno.
        null
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024

        const val CSV_MIME = "text/csv"
        const val JSON_MIME = "application/json"

        /** El respaldo es binario: el selector lo deja guardar con cualquier nombre. */
        const val BACKUP_MIME = "application/octet-stream"

        /** El selector de la cartola: los bancos no coinciden en el tipo que declaran. */
        val TEXT_MIMES = arrayOf(
            "text/*",
            "text/comma-separated-values",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/pdf",
        )

        private val LATIN = Charset.forName("windows-1252")

        fun decode(bytes: ByteArray): String {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (error: CharacterCodingException) {
                String(bytes, LATIN)
            }
        }
    }
}
