package cl.wenasea.wenagastos.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** La llave de la base no se pudo obtener de este teléfono. */
class DbKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Llave de la base cifrada.
 *
 * Los 32 bytes que abren la base no se guardan en claro: van envueltos con una clave AES del
 * Android Keystore, que no se puede exportar (en la mayoría de los teléfonos vive en el TEE).
 * Quien copie los archivos de la app se lleva el sobre cerrado, no la llave.
 *
 * Se genera una sola vez, en la primera apertura de la versión cifrada, y se escribe con
 * `commit()`: si el proceso muere justo después, la llave tiene que estar en disco sí o sí.
 */
class DbKey(private val context: Context) {

    /** Los bytes que abre la base y si hubo que crearlos ahora mismo. */
    class Material(val passphrase: ByteArray, val created: Boolean)

    /**
     * Devuelve la contraseña de SQLCipher (32 bytes en hexadecimal).
     *
     * @throws DbKeyException si el Keystore ya no tiene la clave que abría el sobre guardado.
     */
    fun load(): Material {
        val stored = stored()
        if (stored != null) {
            val raw = runCatching { unwrap(stored.first, stored.second) }.getOrElse { error ->
                throw DbKeyException("Este teléfono ya no tiene la llave de la base", error)
            }
            return Material(DbHex.encode(raw).toByteArray(Charsets.UTF_8), created = false)
        }

        val raw = ByteArray(SIZE).also { SecureRandom().nextBytes(it) }
        val sealed = runCatching { wrap(raw) }.getOrElse { error ->
            throw DbKeyException("No pude guardar la llave de la base", error)
        }
        prefs().edit()
            .putString(KEY_IV, base64(sealed.first))
            .putString(KEY_BLOB, base64(sealed.second))
            .commit()
        return Material(DbHex.encode(raw).toByteArray(Charsets.UTF_8), created = true)
    }

    /** Borra la llave del teléfono: la base cifrada queda ilegible a propósito. */
    fun reset() {
        prefs().edit().clear().commit()
        runCatching {
            val keyStore = keyStore()
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        }
    }

    private fun stored(): Pair<ByteArray, ByteArray>? {
        val prefs = prefs()
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return unBase64(iv) to unBase64(blob)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun secretKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(raw: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv to cipher.doFinal(raw)
    }

    private fun unwrap(iv: ByteArray, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(blob)
    }

    private fun prefs() = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private companion object {
        const val FILE = "wenagastos.secret"
        const val KEY_IV = "llave_iv"
        const val KEY_BLOB = "llave_cifrada"
        const val ALIAS = "wenagastos_db_key"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val SIZE = 32
        const val KEY_BITS = 256
        const val TAG_BITS = 128

        fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun unBase64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    }
}

/**
 * Hexadecimal en minúsculas. La contraseña que recibe SQLCipher son los 32 bytes aleatorios
 * escritos así, para que nunca viaje un texto con caracteres raros hasta el JNI.
 */
object DbHex {

    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            append(DIGITS[value shr 4])
            append(DIGITS[value and 0x0f])
        }
    }

    fun decode(text: String): ByteArray {
        require(text.length % 2 == 0) { "El hexadecimal tiene que tener largo par" }
        return ByteArray(text.length / 2) { index ->
            val high = digit(text[index * 2])
            val low = digit(text[index * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    private fun digit(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> throw IllegalArgumentException("No es un dígito hexadecimal: $char")
    }
}
