package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.domain.Dates
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El export es lo que el usuario abre en Excel o mira en un editor: la fecha tiene que ir en ISO
 * (para que ordene), el monto como entero y el sentido en su propia columna, porque el signo se
 * pierde cuando la planilla mezcla gastos con ingresos.
 */
class ExportTest {

    private val account = AccountEntity(
        id = 1,
        name = "Santander Cta. Cte.",
        institution = Institution.SANTANDER,
        kind = AccountKind.CHECKING,
        colorHex = "#EC0000",
    )
    private val card = AccountEntity(
        id = 2,
        name = "Falabella CMR",
        institution = Institution.FALABELLA,
        kind = AccountKind.CREDIT,
        colorHex = "#7CB342",
    )
    private val market = CategoryEntity(
        id = 10,
        name = "Supermercado",
        emoji = "🛒",
        colorHex = "#2E7D32",
        kind = CategoryKind.EXPENSE,
    )

    private val accounts = mapOf(account.id to account, card.id to card)
    private val categories = mapOf(market.id to market)

    /** 20-11-2026 12:30 en Chile. */
    private val noon: Long = LocalDate.of(2026, 11, 20)
        .atTime(12, 30)
        .atZone(Dates.zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `la fila lleva fecha iso, hora y el tipo del movimiento`() {
        val row = Export.row(expense(), accounts, categories)

        assertEquals(
            listOf(
                "7",
                "2026-11-20",
                "12:30",
                "18990",
                "Gasto",
                "Santander Cta. Cte.",
                "",
                "Supermercado",
                "LIDER EXPRESS",
                "Confirmado",
                "Manual",
                "",
                "",
            ),
            row,
        )
    }

    @Test
    fun `un ingreso se distingue del gasto`() {
        val income = expense().copy(id = 8, direction = Direction.CREDIT, isExpense = false)

        assertEquals("Ingreso", Export.row(income, accounts, categories)[4])
    }

    @Test
    fun `un traspaso muestra la cuenta de destino`() {
        val transfer = expense().copy(id = 9, isTransfer = true, transferAccountId = card.id)

        val row = Export.row(transfer, accounts, categories)

        assertEquals("Traspaso", row[4])
        assertEquals("Falabella CMR", row[6])
    }

    @Test
    fun `sin categoria se escribe sin categoria`() {
        assertEquals("Sin categoría", Export.row(expense().copy(categoryId = null), accounts, categories)[7])
    }

    @Test
    fun `el csv lleva cabecera y una linea por movimiento`() {
        val csv = Export.transactionsCsv(listOf(expense(), expense().copy(id = 8)), accounts, categories)

        assertTrue(csv.startsWith(Csv.BOM + "id,fecha,hora,monto,tipo,"))
        assertEquals(3, csv.trimEnd().split("\r\n").size)
    }

    @Test
    fun `el json lleva el resumen y omite los campos vacios`() {
        val text = Export.transactionsJson(listOf(expense()), accounts, categories, generatedAt = noon)

        @Suppress("UNCHECKED_CAST")
        val root = Json.read(text) as Map<String, Any?>

        assertEquals("Wenagastos", root["app"])
        assertEquals("movimientos", root["documento"])
        assertEquals("2026-11-20T12:30:00-03:00", root["generadoEn"])
        assertEquals(mapOf("movimientos" to 1L, "cuentas" to 2L, "categorias" to 1L), root["resumen"])

        @Suppress("UNCHECKED_CAST")
        val movement = (root["movimientos"] as List<*>).single() as Map<String, Any?>
        assertEquals(18_990L, movement["monto"])
        assertEquals("Gasto", movement["tipo"])
        assertFalse(movement.containsKey("notas"))
        assertFalse(movement.containsKey("cuentaDestino"))
    }

    private fun expense() = TransactionEntity(
        id = 7,
        accountId = account.id,
        occurredAt = noon,
        amount = 18_990L,
        direction = Direction.DEBIT,
        merchantRaw = "LIDER EXPRESS",
        merchantNorm = "lider express",
        categoryId = market.id,
    )
}
