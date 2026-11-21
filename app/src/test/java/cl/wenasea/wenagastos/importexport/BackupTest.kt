package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El respaldo se restaura reemplazando todo, así que los ids viajan en el archivo: son los que
 * mantienen unidas las dos patas de un traspaso y las reglas con su categoría. Si el archivo
 * viene incompleto, `decode` tiene que negarse antes de que se borre algo.
 */
class BackupTest {

    private val account = AccountEntity(
        id = 1,
        name = "Santander Cta. Cte.",
        institution = Institution.SANTANDER,
        kind = AccountKind.CHECKING,
        accountLast4 = "1234",
        cardLast4 = "5678",
        colorHex = "#EC0000",
        isActive = true,
        createdAt = 1_000L,
    )
    private val card = AccountEntity(
        id = 2,
        name = "Falabella CMR",
        institution = Institution.FALABELLA,
        kind = AccountKind.CREDIT,
        colorHex = "#7CB342",
        createdAt = 1_100L,
    )
    private val category = CategoryEntity(
        id = 10,
        name = "Supermercado",
        emoji = "🛒",
        colorHex = "#2E7D32",
        kind = CategoryKind.EXPENSE,
        monthlyBudget = 150_000L,
        sortOrder = 10,
        isArchived = false,
    )
    private val rule = MerchantRuleEntity(
        id = 3,
        merchantKey = "lider",
        merchantLabel = "LIDER",
        direction = Direction.DEBIT,
        categoryId = 10,
        uses = 4,
        createdAt = 2_000L,
        updatedAt = 3_000L,
    )
    private val transfer = TransactionEntity(
        id = 20,
        accountId = 1,
        occurredAt = 1_760_000_000_000L,
        amount = 50_000L,
        direction = Direction.DEBIT,
        merchantRaw = "Traspaso a Falabella",
        merchantNorm = "traspaso a falabella",
        categoryId = 10,
        status = TransactionStatus.CONFIRMED,
        isExpense = true,
        isTransfer = true,
        transferAccountId = 2,
        transferGroupId = 77,
        notes = "pago de la tarjeta",
        source = TransactionSource.MANUAL,
        rawText = "texto del aviso",
        dedupeHash = "abc123",
        createdAt = 4_000L,
        updatedAt = 5_000L,
    )

    private val data = BackupData(
        accounts = listOf(account, card),
        categories = listOf(category),
        rules = listOf(rule),
        transactions = listOf(transfer),
    )

    @Test
    fun `un respaldo vuelve igual, con ids y traspasos`() {
        assertEquals(data, Backup.decode(Backup.encode(data, schemaVersion = 5)))
    }

    @Test
    fun `un respaldo v1 reparte sus numeros entre cuenta y tarjetas`() {
        val text = """
            {"app":"Wenagastos","documento":"respaldo","version":1,"cuentas":[
              {"id":1,"name":"Cta","kind":"CHECKING","last4":"1483,4321"},
              {"id":2,"name":"CMR","kind":"CREDIT","last4":"7788"}]}
        """.trimIndent()

        val accounts = Backup.decode(text).accounts

        assertEquals("1483", accounts[0].accountLast4)
        assertEquals("4321", accounts[0].cardLast4)
        assertNull(accounts[1].accountLast4)
        assertEquals("7788", accounts[1].cardLast4)
    }

    @Test
    fun `el archivo se reconoce como respaldo de la app`() {
        val text = Backup.encode(data, schemaVersion = 5)

        assertTrue(text.startsWith("""{"app":"Wenagastos","documento":"respaldo","""))
        assertTrue(text.contains("\"version\":2"))
        assertTrue(text.contains("\"schema\":5"))
        assertTrue(text.contains("\"accountLast4\":\"1234\""))
        assertTrue(text.contains("\"cardLast4\":\"5678\""))
    }

    @Test
    fun `un documento que no es respaldo se rechaza`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.decode("""{"app":"Wenagastos","documento":"movimientos"}""")
        }

        assertEquals("El archivo no es un respaldo de Wenagastos", error.message)
    }

    @Test
    fun `un movimiento de una cuenta que no viene se rechaza`() {
        val text = backupJson("""{"id":1,"accountId":99,"occurredAt":1760000000000,"amount":1000}""")

        val error = assertThrows(BackupException::class.java) { Backup.decode(text) }

        assertEquals("El respaldo trae movimientos de una cuenta que no incluye", error.message)
    }

    @Test
    fun `un movimiento con monto invalido se rechaza`() {
        val text = backupJson("""{"id":1,"accountId":1,"occurredAt":1760000000000,"amount":-1000}""")

        assertThrows(BackupException::class.java) { Backup.decode(text) }
    }

    @Test
    fun `un json danado se rechaza con un mensaje`() {
        val error = assertThrows(BackupException::class.java) { Backup.decode("""{"app":"Wena""") }

        assertTrue(error.message.orEmpty().startsWith("El respaldo está dañado"))
    }

    @Test
    fun `sin movimientos el respaldo sigue siendo valido`() {
        val empty = BackupData(accounts = listOf(account), categories = emptyList())

        assertEquals(empty, Backup.decode(Backup.encode(empty, schemaVersion = 5)))
    }

    @Test
    fun `un tipo desconocido cae en el valor por defecto`() {
        val text = """
            {"app":"Wenagastos","documento":"respaldo","version":1,"cuentas":[
              {"id":1,"name":"Cuenta","institution":"BANCO_NUEVO","kind":"RARA","colorHex":"#000000"}]}
        """.trimIndent()

        val decoded = Backup.decode(text).accounts.single()

        assertEquals(Institution.OTHER, decoded.institution)
        assertEquals(AccountKind.CHECKING, decoded.kind)
    }

    private fun backupJson(movements: String): String = """
        {"app":"Wenagastos","documento":"respaldo","version":1,"schema":4,
         "cuentas":[{"id":1,"name":"Cta","colorHex":"#EC0000"}],
         "movimientos":[$movements]}
    """.trimIndent()
}
