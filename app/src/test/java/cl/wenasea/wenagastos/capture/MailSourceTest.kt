package cl.wenasea.wenagastos.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MailSourceTest {

    @Test
    fun `un correo del Banco Falabella se reconoce`() {
        assertEquals(BankSource.BANCO_FALABELLA, MailSource.of("Banco Falabella"))
    }

    @Test
    fun `el dominio del banco tambien sirve`() {
        assertEquals(
            BankSource.BANCO_FALABELLA,
            MailSource.of("Banco Falabella <no-responder@bancofalabella.cl>"),
        )
    }

    @Test
    fun `las mayusculas y tildes no estorban`() {
        assertEquals(BankSource.BANCO_FALABELLA, MailSource.of("BANCO FALABELLA"))
    }

    @Test
    fun `un correo de MACHBANK se reconoce como MACH`() {
        // Gmail pone el remitente en el título: así llega el aviso de una transferencia recibida.
        assertEquals(BankSource.MACH, MailSource.of("MACHBANK"))
        assertEquals(BankSource.MACH, MailSource.of("MACHBANK <no-reply@machbank.cl>"))
    }

    @Test
    fun `otro remitente que empieza con mach no se confunde con MACH`() {
        assertNull(MailSource.of("Machine Learning Weekly"))
        assertNull(MailSource.of("MACHALINES"))
    }

    @Test
    fun `un correo de otra parte no se reconoce`() {
        assertNull(MailSource.of("GitHub"))
        assertNull(MailSource.of(""))
    }

    @Test
    fun `el remitente no se confunde con el resto del asunto`() {
        // El nombre del banco tiene que estar en el texto, no inventarse por parecido.
        assertNull(MailSource.of("Falabella S.A. promociones"))
    }
}
