package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Un traspaso se puede registrar desde cualquiera de sus dos patas y la lista tiene que mostrarlo
 * siempre como "origen → destino", sin importar de qué lado venga el movimiento.
 */
class TransfersTest {

    @Test
    fun `una pata que sale tiene como origen su propia cuenta`() {
        val leg = leg(Direction.DEBIT, accountId = 1L, transferAccountId = 2L)

        assertEquals(TransferEnds(originId = 1L, destinationId = 2L), leg.transferEnds())
    }

    @Test
    fun `una pata que entra se lee al reves`() {
        val leg = leg(Direction.CREDIT, accountId = 2L, transferAccountId = 1L)

        assertEquals(TransferEnds(originId = 1L, destinationId = 2L), leg.transferEnds())
    }

    @Test
    fun `una pata sin enlazar deja el destino vacio`() {
        val leg = leg(Direction.DEBIT, accountId = 1L, transferAccountId = null)

        assertEquals(TransferEnds(originId = 1L, destinationId = null), leg.transferEnds())
    }

    @Test
    fun `las columnas guardadas dependen del sentido`() {
        assertEquals(TransferColumns(1L, 2L), transferColumns(Direction.DEBIT, 1L, 2L))
        assertEquals(TransferColumns(2L, 1L), transferColumns(Direction.CREDIT, 1L, 2L))
    }

    @Test
    fun `el sentido contrario es la otra pata`() {
        assertEquals(Direction.CREDIT, Direction.DEBIT.opposite())
        assertEquals(Direction.DEBIT, Direction.CREDIT.opposite())
    }

    private fun leg(
        direction: Direction,
        accountId: Long,
        transferAccountId: Long?,
    ) = TransactionEntity(
        accountId = accountId,
        occurredAt = 0L,
        amount = 22_000L,
        direction = direction,
        merchantRaw = "Traspaso",
        merchantNorm = "traspaso",
        isTransfer = true,
        transferAccountId = transferAccountId,
    )
}
