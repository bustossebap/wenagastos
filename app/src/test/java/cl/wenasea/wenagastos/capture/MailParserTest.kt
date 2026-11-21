package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.Dates
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailParserTest {

    private val postedAt = LocalDateTime.of(2026, 11, 21, 16, 46)
        .atZone(Dates.zone)
        .toInstant()
        .toEpochMilli()

    /** Cuerpo real del correo de Banco Falabella, tal como lo publica Gmail en `bigText`. */
    private val falabella = """
        Aviso de transferencia de fondos realizada

        Detalle
        Nombre destinatario
        Nico Bustos
        Rut
        210409319
        Banco
        BCI MACHBANK
        Producto
        Cuenta Corriente
        Número de cuenta
        777921040931
        Asunto
        Transferencia a Nico Bustos
        Monto transferencia
        ${'$'}2.000
        ________________________________
        Cuenta de origen
        Cuenta Corriente 19834048777
        ________________________________
        Fecha
        21-11-2026
        Hora
        16:27
        Número de operación
        123922
    """.trimIndent()

    private fun parse(
        text: String = "Aviso de transferencia de fondos realizada",
        bigText: String? = falabella,
    ): ParseResult = MailParser.parse(
        RawNotification(
            packageName = MailSource.PACKAGE,
            title = "Banco Falabella",
            text = text,
            bigText = bigText,
            subText = "bustos.sebap@gmail.com",
            postedAt = postedAt,
            key = "0|${MailSource.PACKAGE}|1|^sq_ig_i_personal|1",
        ),
        BankSource.BANCO_FALABELLA,
    )

    private fun parsed(bigText: String? = falabella): ParsedNotification {
        val result = parse(bigText = bigText)
        assertTrue("se esperaba Parsed y llegó $result", result is ParseResult.Parsed)
        return (result as ParseResult.Parsed).notification
    }

    private fun rejectedReason(text: String, bigText: String? = falabella): String? =
        (parse(text = text, bigText = bigText) as? ParseResult.Rejected)?.reason

    @Test
    fun `la transferencia del correo trae monto destino y hora real`() {
        val n = parsed()

        assertEquals(2_000L, n.amount)
        assertEquals("Nico Bustos", n.merchant)
        assertTrue(n.merchantFound)
        assertEquals(Direction.DEBIT, n.direction)
        assertEquals(BankSource.BANCO_FALABELLA, n.source)
        // La cuenta propia es la de origen; la del destinatario (…0931) no debe pisarla.
        assertEquals("8777", n.last4)
        assertFalse(n.isTransfer)
        assertFalse(n.cardIsCredit)
        assertTrue(n.isConfident)

        val operacion = LocalDateTime.of(2026, 11, 21, 16, 27)
            .atZone(Dates.zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(operacion, n.occurredAt)
    }

    @Test
    fun `el rut y el numero de cuenta del destinatario no se leen como monto`() {
        val n = parsed()

        assertEquals(2_000L, n.amount)
        assertTrue(n.amount != 210_409_319L)
        assertTrue(n.amount != 77_792_104_093L)
    }

    @Test
    fun `una promocion con monto no se guarda`() {
        val reason = rejectedReason(
            text = "Hasta ${'$'}50.000 de descuento en tus compras con la tarjeta CMR",
            bigText = null,
        )

        assertEquals("Correo que no es un movimiento", reason)
    }

    @Test
    fun `un resumen de correos nuevos se ignora`() {
        val reason = rejectedReason(text = "3 correos nuevos", bigText = null)

        assertEquals("Correo que no es un movimiento", reason)
    }

    @Test
    fun `una transferencia recibida entra como ingreso`() {
        val body = """
            Aviso de transferencia de fondos realizada
            Detalle
            Nombre remitente
            Juan Pérez
            Monto transferencia
            ${'$'}15.000
            Cuenta de destino
            Cuenta Corriente 19834048777
            Fecha
            21-11-2026
            Hora
            10:05
        """.trimIndent()

        val n = parsed(body)

        assertEquals(15_000L, n.amount)
        assertEquals("Juan Pérez", n.merchant)
        assertEquals(Direction.CREDIT, n.direction)
        // Sin "Cuenta de origen" no hay número propio que asignar.
        assertNull(n.last4)
    }

    @Test
    fun `un correo sin monto se rechaza`() {
        val reason = rejectedReason(
            text = "Aviso de transferencia de fondos realizada",
            bigText = "Aviso de transferencia de fondos realizada\nFecha\n21-11-2026",
        )

        assertEquals("No encontré un monto en pesos", reason)
    }

    @Test
    fun `sin fecha legible se usa la hora de la notificacion`() {
        val body = """
            Aviso de transferencia de fondos realizada
            Nombre destinatario
            Nico Bustos
            Monto transferencia
            ${'$'}2.000
        """.trimIndent()

        assertEquals(postedAt, parsed(body).occurredAt)
    }
}
