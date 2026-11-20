package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifParserTest {

    private val postedAt = 1_700_000_000_000L

    private fun parse(
        packageName: String,
        title: String,
        text: String,
        bigText: String? = null,
    ): ParseResult {
        val source = requireNotNull(BankSource.of(packageName)) { "paquete no soportado" }
        return NotifParser.parse(
            RawNotification(
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText,
                postedAt = postedAt,
                key = "0|$packageName|1|null|1",
            ),
            source,
        )
    }

    private fun parsed(
        packageName: String,
        title: String,
        text: String,
        bigText: String? = null,
    ): ParsedNotification {
        val result = parse(packageName, title, text, bigText)
        assertTrue("se esperaba Parsed y llegó $result", result is ParseResult.Parsed)
        return (result as ParseResult.Parsed).notification
    }

    private fun rejectedReason(
        packageName: String,
        title: String,
        text: String,
    ): String? = (parse(packageName, title, text) as? ParseResult.Rejected)?.reason

    @Test
    fun `compra con tarjeta de debito del Santander`() {
        val n = parsed(
            packageName = "cl.santander.smartphone",
            title = "Compra con tarjeta",
            text = "Compraste $12.500 en LIDER EXPRESS con tu tarjeta de débito " +
                "terminada en 4321. Saldo disponible: $85.000",
        )

        assertEquals(12_500L, n.amount)
        assertEquals(Direction.DEBIT, n.direction)
        assertEquals("LIDER EXPRESS", n.merchant)
        assertTrue(n.merchantFound)
        assertEquals("4321", n.last4)
        assertFalse(n.cardIsCredit)
        assertTrue(n.isConfident)
        assertEquals(postedAt, n.occurredAt)
    }

    @Test
    fun `compra con tarjeta de credito CMR`() {
        val n = parsed(
            packageName = "cl.santander.santanderpasschile",
            title = "CMR",
            text = "Compraste $89.990 en PARIS con tu tarjeta de crédito terminada en 1234.",
        )

        assertEquals(89_990L, n.amount)
        assertEquals("1234", n.last4)
        assertTrue(n.cardIsCredit)
        assertEquals("PARIS", n.merchant)
    }

    @Test
    fun `compra en Falabella con cupo que no es el monto`() {
        val n = parsed(
            packageName = "com.falabella.falabellaApp",
            title = "Falabella",
            text = "Realizaste una compra por $45.990 en FALABELLA RETAIL con tu CMR. " +
                "Cupo disponible: $500.000",
        )

        assertEquals(45_990L, n.amount)
        assertEquals(Direction.DEBIT, n.direction)
        assertEquals("FALABELLA RETAIL", n.merchant)
        assertTrue(n.cardIsCredit)
    }

    @Test
    fun `transferencia recibida usa el remitente como comercio`() {
        val n = parsed(
            packageName = "cl.santander.smartphone",
            title = "Transferencia",
            text = "Recibiste una transferencia de JUAN PEREZ por $30.000 en tu Cuenta RUT.",
        )

        assertEquals(30_000L, n.amount)
        assertEquals(Direction.CREDIT, n.direction)
        assertEquals("JUAN PEREZ", n.merchant)
        assertTrue(n.merchantFound)
    }

    @Test
    fun `transferencia enviada queda como gasto`() {
        val n = parsed(
            packageName = "cl.santander.smartphone",
            title = "Transferencia",
            text = "Transferiste $50.000 a MARIA SOTO desde tu cuenta corriente.",
        )

        assertEquals(50_000L, n.amount)
        assertEquals(Direction.DEBIT, n.direction)
        assertEquals("MARIA SOTO", n.merchant)
    }

    @Test
    fun `cobro de MercadoPago sin comercio queda por revisar`() {
        val n = parsed(
            packageName = "com.mercadopago.wallet",
            title = "Mercado Pago",
            text = "Te pagaron $15.000. El dinero ya está disponible en tu cuenta.",
        )

        assertEquals(15_000L, n.amount)
        assertEquals(Direction.CREDIT, n.direction)
        assertFalse(n.merchantFound)
        assertFalse(n.isConfident)
        assertEquals("Movimiento MercadoPago", n.merchant)
    }

    @Test
    fun `Google Wallet en ingles con el comercio en el titulo`() {
        val n = parsed(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Lider Express",
            text = "You paid CLP 12,500.00 with Visa ••••4321",
        )

        assertEquals(12_500L, n.amount)
        assertEquals(Direction.DEBIT, n.direction)
        assertEquals("Lider Express", n.merchant)
        assertTrue(n.merchantFound)
        assertTrue(n.isConfident)
        assertEquals("4321", n.last4)
    }

    @Test
    fun `Google Wallet en ingles con 'at' en el texto`() {
        val n = parsed(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Google Wallet",
            text = "You paid CLP 12,500.00 with Visa ••••4321 at LIDER EXPRESS",
        )

        assertEquals("LIDER EXPRESS", n.merchant)
        assertTrue(n.merchantFound)
    }

    @Test
    fun `el texto grande de la notificacion tambien sirve`() {
        val n = parsed(
            packageName = "cl.santander.smartphone",
            title = "Santander",
            text = "Tienes un nuevo movimiento",
            bigText = "Compraste $7.990 en UBER EATS con tu tarjeta terminada en 9988",
        )

        assertEquals(7_990L, n.amount)
        assertEquals("UBER EATS", n.merchant)
        assertEquals("9988", n.last4)
    }

    @Test
    fun `rechaza avisos que no son movimientos`() {
        assertEquals(
            "Aviso no transaccional",
            rejectedReason(
                "cl.santander.smartphone",
                "Seguridad",
                "Tu código de verificación es 123456",
            ),
        )
        assertEquals(
            "Aviso no transaccional",
            rejectedReason(
                "cl.santander.smartphone",
                "Promoción",
                "Gana $50.000 en el sorteo de Santander",
            ),
        )
    }

    @Test
    fun `rechaza avisos sin monto en pesos`() {
        assertEquals(
            "Es un saldo, no un movimiento",
            rejectedReason(
                "cl.santander.smartphone",
                "Santander",
                "Tu saldo disponible es $150.000",
            ),
        )
        assertEquals(
            "No encontré un monto en pesos",
            rejectedReason(
                "cl.santander.smartphone",
                "Santander",
                "Compraste US$25,00 en AMAZON PRIME",
            ),
        )
        assertEquals(
            "No encontré un monto en pesos",
            rejectedReason(
                "cl.santander.smartphone",
                "Santander",
                "Tienes un nuevo movimiento",
            ),
        )
    }

    @Test
    fun `rechaza notificaciones vacias`() {
        assertEquals(
            "Notificación sin texto",
            rejectedReason("cl.santander.smartphone", "", ""),
        )
    }

    @Test
    fun `los paquetes desconocidos no son una fuente conocida`() {
        assertNull(BankSource.of("com.whatsapp"))
        assertEquals(BankSource.SANTANDER, BankSource.of("cl.santander.smartphone"))
    }
}
