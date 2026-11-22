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
        // Lo que entró se anota en la cuenta que dice "Cuenta de destino": es la propia.
        assertEquals("8777", n.last4)
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

    /** Cuerpo real del correo de MACHBANK, tal como lo publica Gmail en `bigText`. */
    private val machbank = """
        Recibiste una transferencia de Nicolás Andrés Bustos Ponce

        Recibiste una transferencia
        Hola Sebastian. Acabas de recibir una transferencia de Nicolás Andrés Bustos Ponce sin costo desde MACHBANK.
        Detalle
        Fecha
        21/11/2026 - 19:52:59
        Nombre destinatario
        Sebastian Ignacio Bustos
        ID
        19.526.470-8
        Banco destino
        Banco Falabella
        Cuenta destino
        19834048777
        Monto
        ${'$'}12.000
        Código de confirmación
        391501572352
        Te recomendamos no ingresar a tu cuenta desde redes públicas de internet.
        Enviado con 💜 desde MACHBANK
    """.trimIndent()

    /** Llegada de la notificación: 19:53:02, unos segundos después de la transferencia. */
    private val machAt = LocalDateTime.of(2026, 11, 21, 19, 53, 2)
        .atZone(Dates.zone)
        .toInstant()
        .toEpochMilli()

    private fun parsedMach(): ParsedNotification {
        val result = MailParser.parse(
            RawNotification(
                packageName = MailSource.PACKAGE,
                title = "MACHBANK",
                text = "Recibiste una transferencia de Nicolás Andrés Bustos Ponce",
                bigText = machbank,
                subText = "bustos.sebap@gmail.com",
                postedAt = machAt,
                key = "0|${MailSource.PACKAGE}|2|^sq_ig_i_personal|1",
            ),
            BankSource.MACH,
        )
        assertTrue("se esperaba Parsed y llegó $result", result is ParseResult.Parsed)
        return (result as ParseResult.Parsed).notification
    }

    @Test
    fun `el correo de MACH trae el remitente que el banco receptor no dice`() {
        val n = parsedMach()

        assertEquals(12_000L, n.amount)
        // El nombre no está en un campo: está en la frase "Recibiste una transferencia de…".
        assertEquals("Nicolás Andrés Bustos Ponce", n.merchant)
        assertTrue(n.merchantFound)
        assertEquals(Direction.CREDIT, n.direction)
        assertEquals(BankSource.MACH, n.source)
        // "Nombre destinatario" es el usuario; el número que importa es el de la cuenta destino.
        assertEquals("8777", n.last4)
        assertFalse(n.isTransfer)
        assertTrue(n.isConfident)
    }

    @Test
    fun `el correo de MACH dice que viene del banco de la contraparte`() {
        val n = parsedMach()

        // "Banco destino: Banco Falabella" con un correo de MACH: el aviso es de la contraparte.
        assertTrue(n.isCounterpart)
    }

    @Test
    fun `la hora del correo de MACH viene pegada a la fecha`() {
        val esperado = LocalDateTime.of(2026, 11, 21, 19, 52)
            .atZone(Dates.zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(esperado, parsedMach().occurredAt)
    }

    @Test
    fun `un aviso de MACH con cuenta de MACH no es de la contraparte`() {
        val body = """
            Recibiste una transferencia de Juan Pérez
            Detalle
            Fecha
            21/11/2026 - 10:00:00
            Banco destino
            BCI MACH
            Cuenta destino
            12345678901
            Monto
            ${'$'}5.000
        """.trimIndent()

        val result = MailParser.parse(
            RawNotification(
                packageName = MailSource.PACKAGE,
                title = "MACHBANK",
                text = "Recibiste una transferencia de Juan Pérez",
                bigText = body,
                postedAt = machAt,
                key = "0|${MailSource.PACKAGE}|3|^sq_ig_i_personal|1",
            ),
            BankSource.MACH,
        )

        val n = (result as ParseResult.Parsed).notification
        assertEquals("Juan Pérez", n.merchant)
        assertFalse(n.isCounterpart)
        assertEquals("8901", n.last4)
    }
}
