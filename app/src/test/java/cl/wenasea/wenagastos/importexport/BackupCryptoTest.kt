package cl.wenasea.wenagastos.importexport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El respaldo va cifrado con la contraseña del usuario. Lo que importa: que la contraseña
 * correcta devuelva el archivo igual, que una equivocada no devuelva nada y que el archivo se
 * reconozca como de esta app antes de gastar tiempo derivando la llave.
 */
class BackupCryptoTest {

    private val password = "clave-segura".toCharArray()
    private val plain = """{"app":"Wenagastos","texto":"ñandú"}""".toByteArray()

    @Test
    fun `ida y vuelta con la contrasena correcta`() {
        val envelope = BackupCrypto.encrypt(plain, password)

        assertArrayEquals(plain, BackupCrypto.decrypt(envelope, password))
    }

    @Test
    fun `dos cifrados del mismo texto no se parecen`() {
        val first = BackupCrypto.encrypt(plain, password, iterations = 1_000)
        val second = BackupCrypto.encrypt(plain, password, iterations = 1_000)

        assertFalse(first.contentEquals(second))
        assertArrayEquals(BackupCrypto.decrypt(first, password), BackupCrypto.decrypt(second, password))
    }

    @Test
    fun `una contrasena equivocada no abre el respaldo`() {
        val envelope = BackupCrypto.encrypt(plain, password, iterations = 1_000)

        val error = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(envelope, "otra-clave".toCharArray())
        }

        assertTrue(error.message.orEmpty().contains("contraseña"))
    }

    @Test
    fun `un archivo ajeno se rechaza antes de probar la contrasena`() {
        val error = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(ByteArray(120) { 7 }, password)
        }

        assertEquals("El archivo no es un respaldo de Wenagastos", error.message)
    }

    @Test
    fun `un archivo cortado se rechaza`() {
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt(ByteArray(10), password) }
    }

    @Test
    fun `un byte cambiado invalida el sello`() {
        val envelope = BackupCrypto.encrypt(plain, password, iterations = 1_000)
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()

        assertThrows(BackupException::class.java) { BackupCrypto.decrypt(envelope, password) }
    }

    @Test
    fun `isBackup reconoce la cabecera`() {
        val envelope = BackupCrypto.encrypt(plain, password, iterations = 1_000)

        assertTrue(BackupCrypto.isBackup(envelope))
        assertFalse(BackupCrypto.isBackup(plain))
    }

    @Test
    fun `una contrasena corta o de puros numeros se rechaza al crear`() {
        assertTrue(BackupCrypto.isWeakPassword("12345678"))
        assertTrue(BackupCrypto.isWeakPassword("123456789012"))
        assertTrue(BackupCrypto.isWeakPassword("grapa-larga"))
        assertFalse(BackupCrypto.isWeakPassword("caballo-bateria-grapa-punta"))
    }

    /**
     * La regla es solo para crear: el respaldo que ya existe con una contraseña débil tiene que
     * seguir abriéndose, o la corrección dejaría al usuario sin su única copia.
     */
    @Test
    fun `un respaldo viejo con contrasena debil se sigue abriendo`() {
        val debil = "12345678".toCharArray()
        val envelope = BackupCrypto.encrypt(plain, debil, iterations = 1_000)

        assertArrayEquals(plain, BackupCrypto.decrypt(envelope, debil))
    }
}
