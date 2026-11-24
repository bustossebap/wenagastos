package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El saldo no se suma desde el principio: se toma el último corte del banco y se le agregan los
 * movimientos confirmados posteriores. Estas pruebas fijan las tres decisiones que lo hacen
 * honesto —lo anterior al corte ya está dentro, lo *Por revisar* no mueve nada y un traspaso sí
 * mueve el saldo de cada cuenta— y los totales por banco.
 */
class BalancesTest {

    private val checking = account(id = 1, institution = Institution.SANTANDER)
    private val wallet = account(id = 2, institution = Institution.MERCADOPAGO)
    private val card = account(
        id = 3,
        institution = Institution.FALABELLA,
        kind = AccountKind.CREDIT,
    )

    private fun account(
        id: Long,
        institution: Institution,
        kind: AccountKind = AccountKind.CHECKING,
        isActive: Boolean = true,
    ) = AccountEntity(
        id = id,
        name = "${institution.label} $id",
        institution = institution,
        kind = kind,
        colorHex = institution.colorHex,
        isActive = isActive,
    )

    private fun snapshot(
        id: Long = 1,
        accountId: Long = 1,
        asOf: Long = 1_000L,
        balance: Long,
        source: SnapshotSource = SnapshotSource.MANUAL,
    ) = AccountSnapshotEntity(
        id = id,
        accountId = accountId,
        asOf = asOf,
        balance = balance,
        source = source,
    )

    private fun movement(
        id: Long = 1,
        accountId: Long = 1,
        amount: Long = 10_000L,
        direction: Direction = Direction.DEBIT,
        at: Long = 2_000L,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        isTransfer: Boolean = false,
        transferAccountId: Long? = null,
        installmentTotal: Int? = null,
    ) = TransactionEntity(
        id = id,
        accountId = accountId,
        occurredAt = at,
        amount = amount,
        direction = direction,
        merchantRaw = "Comercio",
        merchantNorm = "comercio",
        status = status,
        isTransfer = isTransfer,
        transferAccountId = transferAccountId,
        installmentTotal = installmentTotal,
    )

    @Test
    fun `sin corte el saldo no se sabe y no es cero`() {
        val balance = Balances.accountBalance(checking, null, listOf(movement()))

        assertNull(balance.balance)
        assertFalse(balance.known)
        assertEquals(0L, balance.delta)
        assertEquals(0, balance.movements)
    }

    @Test
    fun `el saldo es el corte mas lo confirmado despues de esa fecha`() {
        val cut = snapshot(asOf = 1_000L, balance = 100_000L)
        val movements = listOf(
            movement(id = 1, at = 1_000L, amount = 999_999L), // justo en el corte: ya está dentro
            movement(id = 2, at = 500L, amount = 999_999L), // antes del corte: también
            movement(id = 3, at = 2_000L, amount = 30_000L),
            movement(id = 4, at = 3_000L, amount = 5_000L, direction = Direction.CREDIT),
            movement(id = 5, at = 4_000L, status = TransactionStatus.PENDING),
            movement(id = 6, at = 5_000L, status = TransactionStatus.IGNORED),
            movement(id = 7, at = 6_000L, accountId = 2, amount = 1L),
        )

        val balance = Balances.accountBalance(checking, cut, movements)

        assertEquals(100_000L - 30_000L + 5_000L, balance.balance)
        assertEquals(2, balance.movements)
    }

    @Test
    fun `manda el corte mas nuevo y si empatan el ultimo cargado`() {
        val old = snapshot(id = 1, asOf = 1_000L, balance = 10_000L)
        val newer = snapshot(id = 2, asOf = 5_000L, balance = 50_000L)
        val tie = snapshot(id = 3, asOf = 5_000L, balance = 70_000L)

        val latest = Balances.latestByAccount(listOf(old, newer, tie))

        assertEquals(tie, latest[checking.id])
    }

    @Test
    fun `el corte nuevo manda aunque el viejo fuera mas alto`() {
        val movements = listOf(movement(at = 9_000L, amount = 1_000L))
        val balances = Balances.accounts(
            accounts = listOf(checking),
            snapshots = listOf(
                snapshot(id = 1, asOf = 1_000L, balance = 900_000L),
                snapshot(id = 2, asOf = 8_000L, balance = 20_000L),
            ),
            transactions = movements,
        )

        assertEquals(19_000L, balances.single().balance)
    }

    @Test
    fun `las cuentas se agrupan por banco y las archivadas no suman`() {
        val archived = account(id = 4, institution = Institution.SANTANDER, isActive = false)
        val groups = Balances.institutions(
            accounts = listOf(checking, wallet, card, archived),
            snapshots = listOf(
                snapshot(id = 1, accountId = 1, asOf = 1_000L, balance = 100_000L),
                snapshot(id = 2, accountId = 2, asOf = 1_000L, balance = 25_000L),
            ),
            transactions = emptyList(),
        )

        assertEquals(3, groups.size)
        assertEquals(Institution.SANTANDER, groups[0].institution)
        assertEquals(1, groups[0].accounts.size)
        assertEquals(100_000L, groups[0].total)
        assertEquals(Institution.FALABELLA, groups[1].institution)
        assertEquals(0, groups[1].known)
        assertEquals(Institution.MERCADOPAGO, groups[2].institution)
        assertEquals(25_000L, groups[2].total)
    }

    @Test
    fun `una cuenta sin saldo cuenta como desconocida y no suma`() {
        val second = account(id = 4, institution = Institution.SANTANDER)
        val group = Balances.institutions(
            accounts = listOf(checking, second),
            snapshots = listOf(snapshot(id = 1, accountId = 1, balance = 80_000L)),
            transactions = emptyList(),
        ).single()

        assertEquals(1, group.known)
        assertEquals(1, group.unknown)
        assertEquals(80_000L, group.total)
    }

    /**
     * Un traspaso saca la plata de una cuenta y la mete en otra: cada saldo se mueve aunque el
     * patrimonio no. Si las dos patas están registradas, el total de los dos bancos da lo mismo.
     */
    @Test
    fun `un traspaso mueve cada saldo y no el total`() {
        val movements = listOf(
            movement(
                id = 1,
                accountId = 1,
                amount = 40_000L,
                direction = Direction.DEBIT,
                isTransfer = true,
                transferAccountId = 2,
            ),
            movement(
                id = 2,
                accountId = 2,
                amount = 40_000L,
                direction = Direction.CREDIT,
                isTransfer = true,
                transferAccountId = 1,
            ),
        )
        val balances = Balances.accounts(
            accounts = listOf(checking, wallet),
            snapshots = listOf(
                snapshot(id = 1, accountId = 1, asOf = 1_000L, balance = 100_000L),
                snapshot(id = 2, accountId = 2, asOf = 1_000L, balance = 0L),
            ),
            transactions = movements,
        )

        assertEquals(60_000L, balances[0].balance)
        assertEquals(40_000L, balances[1].balance)
        assertEquals(100_000L, balances.sumOf { it.balance ?: 0L })
    }

    @Test
    fun `el gasto del mes no cuenta traspasos ni pendientes y es por cuenta`() {
        val movements = listOf(
            movement(id = 1, accountId = 1, amount = 15_000L, at = 2_000L),
            movement(id = 2, accountId = 1, amount = 9_000L, at = 2_100L, isTransfer = true),
            movement(id = 3, accountId = 1, amount = 7_000L, at = 2_200L, status = TransactionStatus.PENDING),
            movement(id = 4, accountId = 1, amount = 3_000L, at = 2_300L, direction = Direction.CREDIT),
            movement(id = 5, accountId = 2, amount = 4_500L, at = 2_400L),
        )

        val byAccount = Balances.monthSpendByAccount(movements, listOf(checking, wallet))

        assertEquals(15_000L, byAccount[1])
        assertEquals(4_500L, byAccount[2])
    }

    @Test
    fun `el desglose del mes separa fijos, variables y ahorros por cuenta`() {
        val movements = listOf(
            movement(id = 1, accountId = 1, amount = 20_000L, at = 2_000L, installmentTotal = 3),
            movement(id = 2, accountId = 1, amount = 8_000L, at = 2_100L),
            movement(id = 3, accountId = 1, amount = 5_000L, at = 2_200L, direction = Direction.CREDIT),
            movement(id = 4, accountId = 1, amount = 50_000L, at = 2_300L, isTransfer = true, transferAccountId = 2),
            movement(id = 5, accountId = 2, amount = 50_000L, at = 2_300L, direction = Direction.CREDIT, isTransfer = true, transferAccountId = 1),
            movement(id = 6, accountId = 1, amount = 9_000L, at = 2_400L, isTransfer = true, status = TransactionStatus.PENDING),
        )

        assertEquals(20_000L, Balances.fixedExpensesByAccount(movements, 1))
        assertEquals(8_000L, Balances.variableExpensesByAccount(movements, 1))
        // El ahorro es la pata que sale: el traspaso cuenta una vez, la pata que entra en la otra
        // cuenta no suma y el que todavía está Por revisar tampoco.
        assertEquals(50_000L, Balances.savingsByAccount(movements, 1))
        assertEquals(0L, Balances.savingsByAccount(movements, 2))
    }

    @Test
    fun `un saldo negativo es una deuda y resta en el total`() {
        val group = Balances.institutions(
            accounts = listOf(card),
            snapshots = listOf(
                snapshot(id = 1, accountId = 3, balance = -250_000L, source = SnapshotSource.CARTOLA),
            ),
            transactions = emptyList(),
        )

        assertTrue(group.single().accounts.single().known)
        assertEquals(-250_000L, group.single().total)
    }
}
