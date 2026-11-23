package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * El recorte del mes por cuenta (Fase 9.2). Es una función pura a propósito: la pantalla no
 * guarda un estado distinto para una cuenta, solo mira el mismo mes a través de ella, y eso tiene
 * que poder probarse sin base ni Compose.
 */
class LedgerTest {

    private fun account(id: Long, name: String, isActive: Boolean = true) = AccountEntity(
        id = id,
        name = name,
        institution = Institution.SANTANDER,
        kind = AccountKind.CHECKING,
        colorHex = Institution.SANTANDER.colorHex,
        isActive = isActive,
    )

    private fun movement(id: Long, accountId: Long, amount: Long = 1_000L) = TransactionEntity(
        id = id,
        accountId = accountId,
        occurredAt = 1_000L * id,
        amount = amount,
        direction = Direction.DEBIT,
        merchantRaw = "Comercio $id",
        merchantNorm = "comercio $id",
    )

    private val checking = movement(id = 1, accountId = 10)
    private val card = movement(id = 2, accountId = 20)
    private val other = movement(id = 3, accountId = 10)

    @Test
    fun `sin cuenta se ven todos los movimientos del mes`() {
        val all = listOf(checking, card, other)

        assertSame(all, Ledger.scope(all, accountId = null))
    }

    @Test
    fun `con una cuenta se ve solo lo de esa cuenta y en el mismo orden`() {
        val scoped = Ledger.scope(listOf(checking, card, other), accountId = 10)

        assertEquals(listOf(checking, other), scoped)
    }

    @Test
    fun `una cuenta sin movimientos deja la lista vacia`() {
        assertEquals(emptyList<TransactionEntity>(), Ledger.scope(listOf(card), accountId = 10))
    }

    @Test
    fun `el filtro no ofrece cuentas archivadas y las ordena por id`() {
        val selectable = Ledger.selectable(
            listOf(
                account(id = 3, name = "Tercera"),
                account(id = 1, name = "Primera"),
                account(id = 2, name = "Archivada", isActive = false),
            ),
        )

        assertEquals(listOf("Primera", "Tercera"), selectable.map { it.name })
    }
}
