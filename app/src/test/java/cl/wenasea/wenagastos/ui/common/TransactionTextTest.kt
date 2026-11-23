package cl.wenasea.wenagastos.ui.common

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Un traspaso se lee sin categoría: `↔️` en el globo y "Santander → Falabella" debajo. Decir "Sin
 * categoría" sería tratarlo como un gasto al que le falta la etiqueta, y un traspaso no es un gasto.
 */
class TransactionTextTest {

    private val santander = AccountEntity(
        id = 1L,
        name = "Santander Cta. Cte.",
        institution = Institution.SANTANDER,
        kind = AccountKind.CHECKING,
        colorHex = "#D32F2F",
    )

    private val falabella = AccountEntity(
        id = 2L,
        name = "Falabella Cta. Cte.",
        institution = Institution.FALABELLA,
        kind = AccountKind.CHECKING,
        colorHex = "#7CB342",
    )

    private val supermercado = CategoryEntity(
        id = 10L,
        name = "Supermercado",
        emoji = "🛒",
        colorHex = "#2E7D32",
        kind = CategoryKind.EXPENSE,
    )

    private val accounts = listOf(santander, falabella).associateBy { it.id }
    private val categories = mapOf(supermercado.id to supermercado)

    @Test
    fun `un traspaso se dibuja con la flecha de ida y vuelta`() {
        val row = transfer(santander.id, falabella.id, Direction.DEBIT)

        assertEquals("↔️", categoryEmoji(row, categories))
        assertEquals(TRANSFER_EMOJI, categoryEmoji(row, categories))
    }

    @Test
    fun `un traspaso se lee como el par de cuentas, sin categoria`() {
        val sale = transfer(santander.id, falabella.id, Direction.DEBIT)
        val entra = transfer(falabella.id, santander.id, Direction.CREDIT)

        assertEquals("Santander Cta. Cte. → Falabella Cta. Cte.", transactionSubtitle(sale, categories, accounts))
        // Las dos patas dicen lo mismo: la plata va de Santander a Falabella.
        assertEquals("Santander Cta. Cte. → Falabella Cta. Cte.", transactionSubtitle(entra, categories, accounts))
        assertFalse(transactionSubtitle(sale, categories, accounts).contains("Sin categoría"))
    }

    @Test
    fun `un traspaso sin la otra pata dice traspaso y la cuenta`() {
        val row = transfer(santander.id, null, Direction.DEBIT)

        assertEquals("↔️", categoryEmoji(row, categories))
        assertEquals("Traspaso · Santander Cta. Cte.", transactionSubtitle(row, categories, accounts))
    }

    @Test
    fun `un gasto normal sigue mostrando su categoria`() {
        val row = TransactionEntity(
            accountId = santander.id,
            occurredAt = 1L,
            amount = 2_500L,
            direction = Direction.DEBIT,
            merchantRaw = "Unimarc",
            merchantNorm = "unimarc",
            categoryId = supermercado.id,
        )

        assertEquals("🛒", categoryEmoji(row, categories))
        assertEquals("Supermercado · Santander Cta. Cte.", transactionSubtitle(row, categories, accounts))
    }

    private fun transfer(
        accountId: Long,
        transferAccountId: Long?,
        direction: Direction,
    ) = TransactionEntity(
        accountId = accountId,
        occurredAt = 1L,
        amount = 40_000L,
        direction = direction,
        merchantRaw = "Transferencia",
        merchantNorm = "transferencia",
        categoryId = null,
        isExpense = false,
        isTransfer = true,
        transferAccountId = transferAccountId,
    )
}
