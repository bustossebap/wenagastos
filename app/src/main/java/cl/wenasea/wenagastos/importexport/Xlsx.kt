package cl.wenasea.wenagastos.importexport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.RoundingMode
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * La cartola que el banco entrega en Excel (.xlsx) como hoja de filas, para que se lea igual que la
 * de CSV o la de PDF: [StatementImport] recibe filas y no le importa de dónde salieron.
 *
 * Un .xlsx es un ZIP con XML adentro, y acá no hay lector de XML: `Json` lee su propio formato y
 * `android.util.Xml` no corre en las pruebas de la JVM, que es donde se prueban las cartolas. Los
 * pedazos que hacen falta son tres y chicos —el libro (qué hoja trae), los textos compartidos (Excel
 * guarda cada texto una vez y las celdas lo citan por número) y la hoja (qué dice cada celda)— y se
 * sacan con búsquedas de texto, igual que [Pdf] saca el texto de un PDF.
 *
 * Tres cosas de Excel que no se leen como se ven:
 *
 * 1. una fecha no es texto: es la cantidad de días desde 1899 (o desde 1904, en los archivos viejos
 *    de Mac) y el estilo de la celda es lo que dice que ese número es una fecha. Se convierte a
 *    `dd/mm/aaaa` antes de entregarla, porque el resto de la importación espera fechas escritas;
 * 2. un monto también es un número sin formato: se escribe como lo escribe el banco
 *    (`1.234.567`), así se lee con las mismas reglas que el del CSV;
 * 3. una fila no trae las celdas vacías: si falta `C7`, las demás no se corren. Las columnas se
 *    arman por la letra de la dirección de cada celda, no por el orden en que aparecen.
 *
 * Lo que no se puede leer —un ZIP que no es un libro, una hoja protegida con contraseña, una hoja
 * sin celdas— devuelve `null`: es un archivo que no sirve, no una falla del programa.
 */
object Xlsx {

    /** Un libro de Excel, como cualquier ZIP, empieza con `PK\3\4`. */
    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size > SIGNATURE &&
            bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /**
     * El Excel de antes (.xls) no es un ZIP sino un contenedor OLE con registros binarios. No se
     * puede leer con esto, pero se reconoce para decirlo con esas palabras en vez de intentarlo y
     * fallar como si el archivo estuviera roto.
     */
    fun looksLikeLegacy(bytes: ByteArray): Boolean =
        bytes.size > SIGNATURE &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

    /**
     * Las filas de la primera hoja del libro, o `null` si no hay ninguna legible. Las filas vacías
     * no se devuelven: no dicen nada y sólo sumarían filas salteadas.
     */
    fun sheet(bytes: ByteArray): Csv.Sheet? {
        val entries = entries(bytes)
        val path = sheetPath(entries) ?: return null
        val sheet = entries[path] ?: return null

        val strings = entries[SHARED_STRINGS]?.let { sharedStrings(text(it)) } ?: emptyList()
        val dates = entries[STYLES]?.let { dateStyles(text(it)) } ?: emptySet()
        val epoch1904 = entries[WORKBOOK]?.let { text(it).contains("date1904=\"1\"") } ?: false

        val rows = rows(text(sheet), strings, dates, epoch1904)
        if (rows.isEmpty()) return null
        return Csv.Sheet(delimiter = ';', rows = rows)
    }

    /**
     * Las partes del ZIP que interesan, ya descomprimidas. Se lee con `ZipInputStream` y no con
     * `ZipFile` porque el archivo llegó como bytes y no como una ruta: viene del selector de
     * Android. Un .xlsx trae además tema, imágenes y propiedades que acá no se miran.
     */
    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (out.size < MAX_ENTRIES) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !wanted(entry.name)) continue
                val content = readEntry(zip)
                if (content != null) out[entry.name] = content
            }
        }
        return out
    }

    private fun wanted(name: String): Boolean =
        name == WORKBOOK || name == WORKBOOK_RELS || name == SHARED_STRINGS || name == STYLES ||
            (name.startsWith(SHEETS) && name.endsWith(".xml"))

    /** Una entrada del ZIP, con tope: un archivo que dice pesar 8 KB no puede inflarse a 8 GB. */
    private fun readEntry(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            out.write(buffer, 0, read)
            if (out.size() > MAX_ENTRY) return null
        }
        return out.toByteArray()
    }

    /**
     * Dónde está la primera hoja. El libro la nombra con una relación (`r:id="rId1"`) y el archivo
     * de relaciones la traduce a una ruta. Si algo de eso no está, se toma la primera hoja del ZIP:
     * una cartola de un mes trae una sola.
     */
    private fun sheetPath(entries: Map<String, ByteArray>): String? {
        val workbook = entries[WORKBOOK]
        val rels = entries[WORKBOOK_RELS]
        if (workbook != null && rels != null) {
            val id = TAG.findAll(text(workbook))
                .firstOrNull { it.groupValues[1] == "sheet" }
                ?.let { attributes(it.groupValues[2])["r:id"] }
            val target = id?.let { wanted ->
                TAG.findAll(text(rels))
                    .filter { it.groupValues[1] == "Relationship" }
                    .map { attributes(it.groupValues[2]) }
                    .firstOrNull { it["Id"] == wanted }
                    ?.get("Target")
            }
            if (target != null) {
                val path = if (target.startsWith("/")) target.drop(1) else "$XL/$target"
                if (entries.containsKey(path)) return path
            }
        }
        return entries.keys.filter { it.startsWith(SHEETS) && it.endsWith(".xml") }.minOrNull()
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    /**
     * Los textos compartidos, en el orden en que las celdas los citan. Un texto puede venir partido
     * en trozos (`<r><t>LIDE</t></r><r><t>R</t></r>`, cuando el banco mezcla negritas) y entonces se
     * juntan: para la cartola es un solo nombre de comercio.
     */
    private fun sharedStrings(xml: String): List<String> =
        SI.findAll(xml)
            .map { item ->
                T_TEXT.findAll(item.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
            }
            .toList()

    /**
     * Qué estilos de celda son una fecha, por su posición en `cellXfs`. Excel guarda las fechas como
     * un número y el formato es lo único que las distingue de un monto: los formatos 14 a 22 son los
     * de fecha que trae Excel, y los propios (`dd-MM-yyyy`) se reconocen por las letras.
     */
    private fun dateStyles(xml: String): Set<Int> {
        val custom = TAG.findAll(xml)
            .filter { it.groupValues[1] == "numFmt" }
            .map { attributes(it.groupValues[2]) }
            .mapNotNull { attrs ->
                val id = attrs["numFmtId"]?.toIntOrNull() ?: return@mapNotNull null
                id to attrs["formatCode"].orEmpty()
            }
            .toMap()

        val formats = cellXfs(xml)
        return formats.indices.filter { isDate(formats[it], custom) }.toSet()
    }

    /** Los `numFmtId` de `cellXfs`, en orden: el atributo `s` de una celda es su posición. */
    private fun cellXfs(xml: String): List<Int> {
        val section = CELL_XFS.find(xml)?.groupValues?.get(1) ?: return emptyList()
        return TAG.findAll(section)
            .filter { it.groupValues[1] == "xf" }
            .map { attributes(it.groupValues[2])["numFmtId"]?.toIntOrNull() ?: 0 }
            .toList()
    }

    private fun isDate(format: Int, custom: Map<Int, String>): Boolean {
        // El 14 es `dd/mm/aaaa` y del 15 al 22 son sus variantes, con hora incluida o no.
        if (format in 14..22) return true
        val code = custom[format] ?: return false
        // `[$-409]` es el idioma y `"$"` un texto literal: ninguna de las dos cosas es una fecha.
        val clean = code.replace(BRACKETS, "").replace(LITERAL, "")
        return clean.any { it == 'y' || it == 'Y' || it == 'd' || it == 'D' }
    }

    /**
     * Las celdas repartidas en filas y columnas. Cada celda dice su dirección (`B12`), así que una
     * celda vacía simplemente no aparece y no corre a las demás.
     */
    private fun rows(
        xml: String,
        strings: List<String>,
        dates: Set<Int>,
        epoch1904: Boolean,
    ): List<List<String>> {
        val cells = HashMap<Int, HashMap<Int, String>>()
        var width = 0
        for (cell in CELL.findAll(xml)) {
            val attrs = attributes(cell.groupValues[1])
            val at = attrs["r"]?.let { address(it) } ?: continue
            val content = value(attrs, cell.groupValues[2], strings, dates, epoch1904) ?: continue
            cells.getOrPut(at.first) { HashMap() }[at.second] = content
            width = maxOf(width, at.second + 1)
        }
        if (width == 0) return emptyList()
        return cells.keys.sorted().map { row ->
            val line = cells.getValue(row)
            (0 until width).map { column -> line[column].orEmpty() }
        }.filter { line -> line.any { it.isNotBlank() } }
    }

    /** `B12` a (fila, columna), contando desde cero. */
    private fun address(ref: String): Pair<Int, Int>? {
        val match = ADDRESS.matchEntire(ref) ?: return null
        val row = match.groupValues[2].toIntOrNull() ?: return null
        var column = 0
        for (letter in match.groupValues[1]) column = column * 26 + (letter - 'A' + 1)
        return (row - 1) to (column - 1)
    }

    /**
     * Lo que la celda dice. El tipo (`t`) es la mitad de la respuesta: `s` es un texto compartido
     * —su número lo dice el `<v>`—, `inlineStr` viene escrito ahí mismo, `str` es una fórmula con
     * resultado de texto, y sin tipo o con `n` es un número, que puede ser un monto o una fecha.
     */
    private fun value(
        attrs: Map<String, String>,
        inner: String,
        strings: List<String>,
        dates: Set<Int>,
        epoch1904: Boolean,
    ): String? {
        val raw = VALUE.find(inner)?.groupValues?.get(1)
        return when (attrs["t"]) {
            "s" -> raw?.toIntOrNull()?.let { strings.getOrNull(it) }
            "inlineStr" -> T_TEXT.findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
            "str" -> raw?.let { unescape(it) }
            // Un error de Excel (#N/A, #¡VALOR!) no es un monto: se deja escrito y no es fila.
            "e" -> raw?.let { unescape(it) }
            "b" -> raw?.let { if (it == "1") "VERDADERO" else "FALSO" }
            else -> raw?.let { number(it, attrs["s"]?.toIntOrNull(), dates, epoch1904) }
        }
    }

    private fun number(raw: String, style: Int?, dates: Set<Int>, epoch1904: Boolean): String {
        if (style != null && style in dates) {
            raw.toDoubleOrNull()?.let { serial -> date(serial, epoch1904)?.let { return it } }
        }
        return grouped(raw)
    }

    /**
     * Una fecha de Excel a `dd/mm/aaaa`. El día 1 es el 1 de enero de 1900, salvo por el error que
     * Excel arrastra desde Lotus (cree que 1900 fue bisiesto): por eso la base práctica es el 30 de
     * diciembre de 1899. Un número que no cae en un año razonable no era una fecha.
     */
    private fun date(serial: Double, epoch1904: Boolean): String? {
        val base = if (epoch1904) EPOCH_1904 else EPOCH_1900
        val date = runCatching { base.plusDays(serial.toLong()) }.getOrNull() ?: return null
        if (date.year !in YEARS) return null
        return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    }

    /**
     * Un número como lo escribe el banco: miles con punto y, si los trae, centavos con coma. Así
     * `1.234,56` y `9.079` se leen con las mismas reglas que el monto de un CSV.
     */
    private fun grouped(raw: String): String {
        val value = raw.toBigDecimalOrNull() ?: return raw
        val plain = value.abs().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        val point = plain.indexOf('.')
        val whole = if (point < 0) plain else plain.substring(0, point)
        val cents = if (point < 0) "" else plain.substring(point + 1)
        val thousands = whole.reversed().chunked(3).joinToString(".").reversed()
        val body = if (cents.isEmpty()) thousands else "$thousands,$cents"
        return if (value.signum() < 0) "-$body" else body
    }

    /** `&amp;` y `&#241;` son parte del nombre del comercio, no del archivo. */
    private fun unescape(text: String): String =
        if (!text.contains('&')) {
            text
        } else {
            ENTITY.replace(text) { match ->
                val body = match.groupValues[1]
                when {
                    body == "amp" -> "&"
                    body == "lt" -> "<"
                    body == "gt" -> ">"
                    body == "quot" -> "\""
                    body == "apos" -> "'"
                    body.startsWith("#x") || body.startsWith("#X") ->
                        body.drop(2).toIntOrNull(16)?.let { code -> String(Character.toChars(code)) }
                            ?: match.value
                    body.startsWith("#") ->
                        body.drop(1).toIntOrNull()?.let { code -> String(Character.toChars(code)) }
                            ?: match.value
                    else -> match.value
                }
            }
        }

    /** Los atributos de una etiqueta abierta: `r="B12" t="s"` a un mapa. */
    private fun attributes(tag: String): Map<String, String> =
        ATTRIBUTE.findAll(tag).associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private const val XL = "xl"
    private const val SHEETS = "$XL/worksheets/"
    private const val WORKBOOK = "$XL/workbook.xml"
    private const val WORKBOOK_RELS = "$XL/_rels/workbook.xml.rels"
    private const val SHARED_STRINGS = "$XL/sharedStrings.xml"
    private const val STYLES = "$XL/styles.xml"

    /** Cuántos bytes de firma se miran para reconocer un ZIP o un .xls. */
    private const val SIGNATURE = 4

    /** Un libro de cartola pesa unos pocos KB; una entrada de más de esto no es una cartola. */
    private const val MAX_ENTRY = 8 * 1024 * 1024

    /** Y no se descomprimen más entradas que las de un libro: hoja, textos, estilos y poco más. */
    private const val MAX_ENTRIES = 16

    /** Un año fuera de este rango es una columna mal mapeada, no una cartola vieja. */
    private val YEARS = 1990..2100

    private val EPOCH_1900: LocalDate = LocalDate.of(1899, 12, 30)
    private val EPOCH_1904: LocalDate = LocalDate.of(1904, 1, 1)

    /** Una etiqueta abierta: el nombre y lo que lleva adentro. */
    private val TAG = Regex("""<([A-Za-z][\w:.-]*)\b([^>]*?)/?>""")

    /** Un atributo y su valor entre comillas. `[\w:.-]` porque hay `r:id` y `xml:space`. */
    private val ATTRIBUTE = Regex("""([\w:.-]+)="([^"]*)"""")

    /** Una etiqueta con contenido, como `<si>…</si>`: los textos, uno por etiqueta. */
    private val SI = Regex("""<si\b[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)

    /** El bloque de estilos de celda, que es el que numera el atributo `s` de cada celda. */
    private val CELL_XFS = Regex("""<cellXfs\b[^>]*>(.*?)</cellXfs>""", RegexOption.DOT_MATCHES_ALL)

    /** Una celda: sus atributos siempre, el contenido sólo si no se cierra en la misma etiqueta. */
    private val CELL = Regex("""<c\b([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)

    private val VALUE = Regex("""<v\b[^>]*>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)

    private val T_TEXT = Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)

    /** La dirección de una celda: letras de columna y número de fila. */
    private val ADDRESS = Regex("""([A-Z]+)(\d+)""")

    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")

    /** Secciones entre corchetes (`[$-409]`, `[h]`) y textos literales (`"$"`) del formato. */
    private val BRACKETS = Regex("""\[[^\]]*]""")
    private val LITERAL = Regex("\"[^\"]*\"")
}
