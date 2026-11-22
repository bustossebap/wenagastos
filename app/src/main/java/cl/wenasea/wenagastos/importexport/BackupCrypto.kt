package cl.wenasea.wenagastos.importexport

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Un respaldo que no se puede abrir, o que no es de esta app, con un mensaje mostrable. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * El respaldo es un archivo con contraseña: PBKDF2-HMAC-SHA256 (120.000 vueltas) para sacar la
 * llave desde lo que escribe el usuario, y AES-256-GCM para cifrar. Todo con `javax.crypto`,
 * que viene en el teléfono: no hay dependencias y se puede probar en la JVM.
 *
 * Formato, en orden: `WENABK1` (7) · versión (1) · iteraciones (4, big endian) · sal (16) ·
 * IV (12) · cuerpo cifrado (con el sello de GCM al final). La cabecera va en claro a propósito:
 * sin ella no se puede derivar la llave, y no dice nada de los movimientos.
 */
object BackupCrypto {

    const val MAGIC = "WENABK1"
    const val VERSION = 1

    /**
     * Menos que esto se adivina. Al crear, el diálogo no deja seguir: ocho dígitos —el caso
     * típico— son un PIN y están en las primeras decenas de cualquier lista de contraseñas
     * filtradas, así que las 120.000 vueltas de PBKDF2 no salvan nada.
     */
    const val MIN_PASSWORD = 12

    /**
     * Demasiado corta, o de puros números, que es un PIN disfrazado. Se exige **solo al crear**:
     * un respaldo viejo con contraseña débil tiene que poder restaurarse igual, o el respaldo
     * quedaría inservible justo cuando hace falta.
     */
    fun isWeakPassword(password: CharSequence): Boolean =
        password.length < MIN_PASSWORD || password.all { it.isDigit() }

    const val ITERATIONS = 120_000

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** Cabecera sin el cuerpo cifrado. */
    private const val HEADER_BYTES = 7 + 1 + 4 + SALT_BYTES + IV_BYTES

    fun isBackup(envelope: ByteArray): Boolean =
        envelope.size >= HEADER_BYTES &&
            String(envelope, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            val body = cipher.doFinal(plain)
            val head = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
            head.put(MAGIC.toByteArray(Charsets.US_ASCII))
            head.put(VERSION.toByte())
            head.putInt(iterations)
            head.put(salt)
            head.put(iv)
            return head.array() + body
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        if (envelope.size < HEADER_BYTES) throw BackupException("El archivo está incompleto")
        val head = ByteBuffer.wrap(envelope, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.length).also(head::get)
        if (String(magic, Charsets.US_ASCII) != MAGIC) {
            throw BackupException("El archivo no es un respaldo de Wenagastos")
        }
        val version = head.get().toInt() and 0xFF
        if (version > VERSION) {
            throw BackupException("El respaldo es de una versión más nueva de la app")
        }
        val iterations = head.getInt()
        if (iterations < 1_000 || iterations > 10_000_000) {
            throw BackupException("El respaldo tiene una cabecera inesperada")
        }
        val salt = ByteArray(SALT_BYTES).also(head::get)
        val iv = ByteArray(IV_BYTES).also(head::get)
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            return cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            throw BackupException("La contraseña no es la de este respaldo", e)
        } catch (e: GeneralSecurityException) {
            throw BackupException("No se pudo abrir el respaldo: ${e.message}", e)
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
