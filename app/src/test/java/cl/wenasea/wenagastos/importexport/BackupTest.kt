package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.CategoryNature
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
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
        creditLimit = 1_500_000L,
        statementDay = 25,
        paymentDay = 5,
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
        nature = CategoryNature.FIXED,
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

    private val snapshot = AccountSnapshotEntity(
        id = 7,
        accountId = 1,
        asOf = 1_759_000_000_000L,
        balance = 1_250_000L,
        source = SnapshotSource.MANUAL,
        note = "lo que decía el banco",
        createdAt = 6_000L,
    )

    private val data = BackupData(
        accounts = listOf(account, card),
        categories = listOf(category),
        rules = listOf(rule),
        transactions = listOf(transfer),
        snapshots = listOf(snapshot),
    )

    @Test
    fun `un respaldo vuelve igual, con ids y traspasos`() {
        assertEquals(data, Backup.decode(Backup.encode(data, schemaVersion = 5)))
    }

    @Test
    fun `una compra en cuotas vuelve con su cuota y su precio`() {
        val cuota = transfer.copy(
            id = 21,
            isTransfer = false,
            transferAccountId = null,
            transferGroupId = null,
            installmentNumber = 3,
            installmentTotal = 12,
            originAmount = 600_000L,
        )
        val conCuotas = data.copy(transactions = listOf(transfer, cuota))

        assertEquals(conCuotas, Backup.decode(Backup.encode(conCuotas, schemaVersion = 9)))
    }

    @Test
    fun `un respaldo v5 se lee como compras pagadas de una vez`() {
        // El archivo de antes de la Fase 9.3 no trae cuotas: sus movimientos no van en cuotas, que
        // es distinto de «no se sabe».
        val text = backupJson(
            """{"id":1,"accountId":1,"occurredAt":1760000000000,"amount":50000,
               "merchantRaw":"Altamar Spa","merchantNorm":"altamar spa"}""",
        )

        val movimiento = Backup.decode(text).transactions.single()

        assertNull(movimiento.installmentNumber)
        assertNull(movimiento.installmentTotal)
        assertNull(movimiento.originAmount)
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

    /** Un respaldo v3 (antes del cupo) se sigue leyendo: la tarjeta queda sin configurar. */
    @Test
    fun `un respaldo sin cupo deja la tarjeta sin configurar`() {
        val text = """
            {"app":"Wenagastos","documento":"respaldo","version":3,"schema":6,"cuentas":[
              {"id":2,"name":"CMR","institution":"FALABELLA","kind":"CREDIT",
               "cardLast4":"7788","colorHex":"#7CB342","isActive":true,"createdAt":1}]}
        """.trimIndent()

        val account = Backup.decode(text).accounts.single()

        assertNull(account.creditLimit)
        assertNull(account.statementDay)
        assertNull(account.paymentDay)
    }

    @Test
    fun `el archivo se reconoce como respaldo de la app`() {
        val text = Backup.encode(data, schemaVersion = 5)

        assertTrue(text.startsWith("""{"app":"Wenagastos","documento":"respaldo","""))
        assertTrue(text.contains("\"version\":6"))
        assertTrue(text.contains("\"schema\":5"))
        assertTrue(text.contains("\"accountLast4\":\"1234\""))
        assertTrue(text.contains("\"cardLast4\":\"5678\""))
        assertTrue(text.contains("\"creditLimit\":1500000"))
        assertTrue(text.contains("\"statementDay\":25"))
        assertTrue(text.contains("\"nature\":\"FIXED\""))
        assertTrue(text.contains("\"saldos\":[{"))
    }

    /**
     * Un respaldo v4 (antes de la naturaleza) se sigue leyendo: la categoría queda variable, que
     * es el mismo valor por defecto que le puso la migración de la base.
     */
    @Test
    fun `un respaldo sin naturaleza deja la categoria como variable`() {
        val text = """
            {"app":"Wenagastos","documento":"respaldo","version":4,"schema":7,"categorias":[
              {"id":10,"name":"Supermercado","emoji":"🛒","colorHex":"#2E7D32","kind":"EXPENSE",
               "sortOrder":10,"isArchived":false}]}
        """.trimIndent()

        val category = Backup.decode(text).categories.single()

        assertEquals("Supermercado", category.name)
        assertEquals(CategoryNature.VARIABLE, category.nature)
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
    fun `un saldo de una cuenta que no viene se rechaza`() {
        val text = """
            {"app":"Wenagastos","documento":"respaldo","version":3,
             "cuentas":[{"id":1,"name":"Cta","colorHex":"#EC0000"}],
             "saldos":[{"id":1,"accountId":99,"asOf":1760000000000,"balance":1000}]}
        """.trimIndent()

        val error = assertThrows(BackupException::class.java) { Backup.decode(text) }

        assertEquals("El respaldo trae saldos de una cuenta que no incluye", error.message)
    }

    /** Un respaldo v2 (antes de los saldos) se sigue leyendo: simplemente no trae cortes. */
    @Test
    fun `un respaldo sin saldos se lee con la lista vacia`() {
        val text = """
            {"app":"Wenagastos","documento":"respaldo","version":2,"schema":5,
             "cuentas":[{"id":1,"name":"Cta","colorHex":"#EC0000"}]}
        """.trimIndent()

        assertTrue(Backup.decode(text).snapshots.isEmpty())
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
