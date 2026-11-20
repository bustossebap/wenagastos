package cl.wenasea.wenagastos.importexport

/**
 * JSON mínimo, sin librerías: lo que la app necesita para exportar y respaldar (objetos,
 * arreglos, textos, números, booleanos y `null`). Vive acá, sin Android de por medio, para que
 * el formato del respaldo se pueda probar en la JVM sin emulador.
 *
 * El parser es estricto a propósito: un respaldo cortado a la mitad tiene que fallar con un
 * mensaje claro en vez de entrar a la base con la mitad de los movimientos.
 */
object Json {

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    fun read(text: String): Any? = Parser(text).parse()

    /** Error de formato: el mensaje se muestra tal cual en Ajustes. */
    class JsonFormatException(message: String) : IllegalArgumentException(message)

    private fun writeTo(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int -> out.append(value.toString())
            is Long -> out.append(value.toString())
            is Double -> if (value.isFinite()) out.append(number(value)) else out.append("null")
            is Float -> if (value.isFinite()) out.append(number(value.toDouble())) else out.append("null")
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    writeTo(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    writeTo(out, item)
                }
                out.append(']')
            }
            is Array<*> -> writeTo(out, value.asList())
            else -> writeString(out, value.toString())
        }
    }

    /** 1.0 se escribe como 1, para que no queden montos "12000.0" en el archivo. */
    private fun number(value: Double): String {
        val whole = value == Math.floor(value) && Math.abs(value) < 1e15
        return if (whole) value.toLong().toString() else value.toString()
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ') {
                    out.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {

        private var index = 0

        fun parse(): Any? {
            val value = readValue()
            skipSpace()
            if (index < text.length) fail("sobra texto después del JSON")
            return value
        }

        private fun readValue(): Any? {
            skipSpace()
            if (index >= text.length) fail("el JSON está vacío")
            return when (val ch = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (ch == '-' || ch.isDigit()) readNumber() else fail("no esperaba '$ch'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            index++
            skipSpace()
            if (peek() == '}') {
                index++
                return result
            }
            while (true) {
                skipSpace()
                if (peek() != '"') fail("se esperaba el nombre de un campo")
                val key = readString()
                skipSpace()
                expect(':')
                result[key] = readValue()
                skipSpace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return result
                    }
                    else -> fail("se esperaba ',' o '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val result = ArrayList<Any?>()
            index++
            skipSpace()
            if (peek() == ']') {
                index++
                return result
            }
            while (true) {
                result += readValue()
                skipSpace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return result
                    }
                    else -> fail("se esperaba ',' o ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (index >= text.length) fail("el texto quedó sin cerrar")
                when (val ch = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= text.length) fail("escape incompleto")
                        when (val escaped = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> out.append(readUnicode())
                            else -> fail("escape desconocido '\\$escaped'")
                        }
                    }
                    else -> out.append(ch)
                }
            }
        }

        private fun readUnicode(): Char {
            if (index + 4 > text.length) fail("escape \\u incompleto")
            val hex = text.substring(index, index + 4)
            index += 4
            return hex.toIntOrNull(16)?.toChar() ?: fail("escape \\u inválido")
        }

        private fun readNumber(): Any {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && text[index].isDigit()) index++
            var decimal = false
            if (index < text.length && text[index] == '.') {
                decimal = true
                index++
                while (index < text.length && text[index].isDigit()) index++
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                decimal = true
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                while (index < text.length && text[index].isDigit()) index++
            }
            val raw = text.substring(start, index)
            return if (decimal) {
                raw.toDoubleOrNull() ?: fail("número inválido '$raw'")
            } else {
                raw.toLongOrNull() ?: fail("número inválido '$raw'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, index)) fail("se esperaba '$word'")
            index += word.length
            return value
        }

        private fun expect(ch: Char) {
            skipSpace()
            if (index >= text.length || text[index] != ch) fail("se esperaba '$ch'")
            index++
        }

        private fun peek(): Char {
            if (index >= text.length) fail("el JSON quedó incompleto")
            return text[index]
        }

        private fun skipSpace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun fail(message: String): Nothing =
            throw JsonFormatException("JSON inválido: $message (posición ${index + 1})")
    }
}
