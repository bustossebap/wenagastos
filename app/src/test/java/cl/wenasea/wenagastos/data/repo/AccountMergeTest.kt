package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.capture.fakes.FakeAccountDao
import cl.wenasea.wenagastos.capture.fakes.FakeAccountSnapshotDao
import cl.wenasea.wenagastos.capture.fakes.FakeTransactionDao
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unir la tarjeta de débito con la cuenta corriente es lo que hace el banco de verdad: la plata
 * es la misma. La fusión mueve historia y borra los traspasos entre ambas, así que estas pruebas
 * fijan las reglas y el orden: primero se borran las patas internas, después se mueve el resto.
 */
class AccountMergeTest {

    private val accounts = FakeAccountDao()
    private val movements = FakeTransactionDao()
    private val snapshots = FakeAccountSnapshotDao()

    private val repository = AccountMergeRepository(
        runner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        },
        accounts = accounts,
        movements = movements,
        snapshots = snapshots,
    )

    private fun account(
        name: String,
        institution: Institution = Institution.SANTANDER,
        kind: AccountKind = AccountKind.CHECKING,
        accountLast4: String? = null,
        cardLast4: String? = null,
    ) = AccountEntity(
        name = name,
        institution = institution,
        kind = kind,
        colorHex = institution.colorHex,
        accountLast4 = accountLast4,
        cardLast4 = cardLast4,
    )

    private fun movement(
        accountId: Long,
        amount: Long = 5_000L,
        direction: Direction = Direction.DEBIT,
        transferAccountId: Long? = null,
    ) = TransactionEntity(
        accountId = accountId,
        occurredAt = 1_760_000_000_000L,
        amount = amount,
        direction = direction,
        merchantRaw = "Compra",
        merchantNorm = "compra",
        isTransfer = transferAccountId != null,
        transferAccountId = transferAccountId,
    )

    @Test
    fun `la tarjeta de debito se une con la corriente y suma sus numeros`() = runBlocking {
        val (checkingId, debitId) = accounts.seed(
            account("Santander Cta. Cte.", accountLast4 = "1483"),
            account("Santander Débito", kind = AccountKind.DEBIT, cardLast4 = "4321"),
        )
        movements.seed(movement(accountId = debitId))
        accounts.movementCounts = mapOf(debitId to 1)

        val plan = repository.plan(checkingId, debitId)
        assertEquals(1, plan.movements)
        assertEquals(0, plan.transfers)
        repository.merge(plan)

        val merged = accounts.getById(checkingId)!!
        assertEquals("1483", merged.accountLast4)
        assertEquals("4321", merged.cardLast4)
        assertTrue(merged.isActive)
        assertNull(accounts.getById(debitId))
        assertEquals(checkingId, movements.stored.single().accountId)
    }

    @Test
    fun `los traspasos entre las dos se borran y los demas se mueven`() = runBlocking {
        val (checkingId, debitId) = accounts.seed(
            account("Santander Cta. Cte.", accountLast4 = "1483"),
            account("Santander Débito", kind = AccountKind.DEBIT, cardLast4 = "4321"),
        )
        movements.seed(
            // Traspaso interno: al unir las cuentas ya no va de una a otra, se borra.
            movement(accountId = checkingId, transferAccountId = debitId),
            movement(
                accountId = debitId,
                direction = Direction.CREDIT,
                transferAccountId = checkingId,
            ),
            // Compra suelta de la tarjeta: pasa a la cuenta que se queda.
            movement(accountId = debitId, amount = 9_000L),
        )

        val plan = repository.plan(checkingId, debitId)
        assertEquals(2, plan.transfers)
        repository.merge(plan)

        assertEquals(1, movements.stored.size)
        val remaining = movements.stored.single()
        assertEquals(checkingId, remaining.accountId)
        assertEquals(9_000L, remaining.amount)
    }

    @Test
    fun `un traspaso hacia la cuenta que desaparece se repunta a la que queda`() = runBlocking {
        val (checkingId, debitId, otherId) = accounts.seed(
            account("Santander Cta. Cte.", accountLast4 = "1483"),
            account("Santander Débito", kind = AccountKind.DEBIT, cardLast4 = "4321"),
            account(
                "MercadoPago",
                institution = Institution.MERCADOPAGO,
                kind = AccountKind.SAVINGS,
            ),
        )
        movements.seed(
            // Traspaso desde otra cuenta hacia la de débito: su pata debe apuntar a la corriente.
            movement(accountId = otherId, transferAccountId = debitId),
        )

        repository.merge(repository.plan(checkingId, debitId))

        val leg = movements.stored.single()
        assertEquals(otherId, leg.accountId)
        assertNotNull(leg.transferAccountId)
        assertEquals(checkingId, leg.transferAccountId)
    }

    @Test
    fun `la cuenta que se queda gana las tarjetas de la otra`() = runBlocking {
        val (checkingId, debitId) = accounts.seed(
            account("Santander Cta. Cte."),
            account("Santander Débito", kind = AccountKind.DEBIT, cardLast4 = "4321"),
        )

        repository.merge(repository.plan(checkingId, debitId))

        assertEquals("4321", accounts.getById(checkingId)!!.cardLast4)
    }

    @Test
    fun `no se unen cuentas de bancos distintos`() = runBlocking {
        val (santanderId, falabellaId) = accounts.seed(
            account("Santander Cta. Cte."),
            account("Falabella Cta. Cte.", institution = Institution.FALABELLA),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.plan(santanderId, falabellaId) }
        }

        assertEquals("Solo se pueden unir cuentas del mismo banco", error.message)
    }

    @Test
    fun `una tarjeta de credito no se une con la cuenta corriente`() = runBlocking {
        val (checkingId, cardId) = accounts.seed(
            account("Santander Cta. Cte."),
            account("Santander MC", kind = AccountKind.CREDIT, cardLast4 = "5566"),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.plan(checkingId, cardId) }
        }

        assertEquals("Una tarjeta de crédito no se une con una cuenta", error.message)
    }

    @Test
    fun `una cuenta consigo misma no se une`() = runBlocking {
        val id = accounts.seed(account("Santander Cta. Cte.")).single()

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.plan(id, id) }
        }

        assertEquals("Son la misma cuenta", error.message)
    }

    /**
     * El saldo es de la cuenta, y la fusión borra la cuenta que se une: si su corte se quedara
     * apuntándole, la llave foránea lo borraría en cascada y el saldo se perdería en silencio.
     */
    @Test
    fun `la fusion se lleva los saldos a la cuenta que se queda`() = runBlocking {
        val (checkingId, debitId) = accounts.seed(
            account("Santander Cta. Cte.", accountLast4 = "1483"),
            account("Santander Débito", kind = AccountKind.DEBIT, cardLast4 = "4321"),
        )
        snapshots.seed(
            AccountSnapshotEntity(accountId = debitId, asOf = 1_000L, balance = 250_000L),
        )

        repository.merge(repository.plan(checkingId, debitId))

        val moved = snapshots.stored.single()
        assertEquals(checkingId, moved.accountId)
        assertEquals(250_000L, moved.balance)
    }
}
