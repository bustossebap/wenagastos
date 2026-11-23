package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Las compras en cuotas (Fase 9.3).
 *
 * Lo que hay que fijar acá: que la celda de la cartola se lea como la escriben los dos bancos
 * (Santander dice en cuál cuota va, Falabella cuántas faltan), que una cuota dudosa se prefiera a
 * una inventada, y que «cuánto falta pagar» se calcule con la cuota del mes y no con el precio de
 * la compra.
 */
class InstallmentsTest {

    @Test
    fun `la celda se lee como la escribe cada banco`() {
        assertEquals(Installments.Cell.Progress(5, 6), Installments.parseCell("05/06"))
        assertEquals(Installments.Cell.Progress(5, 6), Installments.parseCell("5/6"))
        assertEquals(Installments.Cell.Progress(5, 12), Installments.parseCell("5 DE 12"))
        assertEquals(Installments.Cell.Progress(5, 12), Installments.parseCell(" 5 de 12 "))
        // Falabella escribe cuántas cuotas faltan, no en cuál va.
        assertEquals(Installments.Cell.Pending(9), Installments.parseCell("9"))
    }

    @Test
    fun `una compra pagada de una vez no es una cuota`() {
        // Llenar la ficha de «cuota» en todas las compras normales estorbaría más de lo que ayuda.
        assertNull(Installments.parseCell(""))
        assertNull(Installments.parseCell("0"))
        assertNull(Installments.parseCell("1/1"))
        assertNull(Installments.parseCell("   "))
    }

    @Test
    fun `una celda que no es una cuota no se adivina`() {
        assertNull(Installments.parseCell("abc"))
        assertNull(Installments.parseCell("13/12"))
        assertNull(Installments.parseCell("0/12"))
        assertNull(Installments.parseCell("12.345"))
        assertNull(Installments.parseCell("61"))
        assertNull(Installments.parseCell("999"))
    }

    @Test
    fun `santander dice en cual cuota va y se guarda tal cual`() {
        assertEquals(
            3 to 12,
            Installments.resolve(Installments.Cell.Progress(3, 12), 50_000L, 600_000L),
        )
        // Aunque la cartola no traiga el precio de la compra.
        assertEquals(3 to 12, Installments.resolve(Installments.Cell.Progress(3, 12), 50_000L, null))
    }

    @Test
    fun `falabella dice cuantas faltan y el total sale del precio de la compra`() {
        // `CUOTAS PENDIENTES 9`, `VALOR CUOTA 50.000`, `MONTO ORIGEN 600.000`: son doce cuotas y
        // esta es la tercera.
        assertEquals(
            3 to 12,
            Installments.resolve(Installments.Cell.Pending(9), 50_000L, 600_000L),
        )
    }

    @Test
    fun `sin el precio de la compra no se inventa la cuota`() {
        // Con solo «faltan 9» no se sabe si son 10 o 12 cuotas: es mejor no saberlo.
        assertNull(Installments.resolve(Installments.Cell.Pending(9), 50_000L, null))
        // Un precio que no es múltiplo de la cuota es otra columna, no la compra.
        assertNull(Installments.resolve(Installments.Cell.Pending(9), 50_000L, 599_000L))
        // Faltan todas: la compra todavía no empieza y no hay cuota que anotar.
        assertNull(Installments.resolve(Installments.Cell.Pending(12), 50_000L, 600_000L))
        assertNull(Installments.resolve(null, 50_000L, 600_000L))
    }

    @Test
    fun `la ficha de la fila dice en que cuota va y cuanto queda`() {
        val cuota = charge(number = 3, total = 12, perCharge = 50_000L)

        assertEquals("3/12", Installments.label(cuota))
        assertEquals("cuota 3/12", Installments.shortLabel(cuota))
        assertEquals(9, Installments.remaining(cuota))
        assertEquals(450_000L, Installments.pendingAmount(cuota))
    }

    @Test
    fun `sin el numero de cuota se dice en cuantas va la compra`() {
        val cuota = charge(number = null, total = 12, perCharge = 50_000L)

        assertNull(Installments.label(cuota))
        assertEquals("en 12 cuotas", Installments.shortLabel(cuota))
        assertNull(Installments.remaining(cuota))
        assertNull(Installments.pendingAmount(cuota))
    }

    @Test
    fun `un gasto normal no tiene ficha de cuota`() {
        val gasto = charge(number = null, total = null, perCharge = 2_500L)

        assertNull(Installments.label(gasto))
        assertNull(Installments.shortLabel(gasto))
        assertNull(Installments.remaining(gasto))
        assertNull(Installments.pendingAmount(gasto))
    }

    @Test
    fun `cuanto falta pagar se suma por compra`() {
        // La misma compra en 12 cuotas, anotadas las tres primeras.
        val compra = (1..3).map { number ->
            charge(number = number, total = 12, perCharge = 50_000L, origin = 600_000L, at = number.toLong())
        }

        val plan = Installments.plans(compra).single()

        assertEquals("LIDER", plan.merchant)
        assertEquals(12, plan.total)
        assertEquals(3, plan.paid)
        assertEquals(50_000L, plan.perCharge)
        // Nueve cuotas por delante, a la cuota del mes: no el precio de la compra otra vez.
        assertEquals(9, plan.remaining)
        assertEquals(450_000L, plan.pending)
        assertEquals("3/12", plan.label)
    }

    @Test
    fun `una compra terminada no aparece por pagar`() {
        // «Cuotas por pagar» es la parte del sueldo que está comprometida, no un archivo de compras.
        val pagada = (1..12).map { number ->
            charge(number = number, total = 12, perCharge = 50_000L, origin = 600_000L, at = number.toLong())
        }
        val enCurso = charge(number = 1, total = 3, perCharge = 10_000L, merchant = "FALABELLA")

        val plans = Installments.plans(pagada + enCurso)

        assertEquals(1, plans.size)
        assertEquals("FALABELLA", plans[0].merchant)
    }

    @Test
    fun `dos compras parecidas no se mezclan`() {
        // Mismo comercio y mismas cuotas, pero distinto precio: son dos compras, y sumarlas dejaría
        // una de ellas «pagada» por la otra.
        val primera = charge(number = 1, total = 6, perCharge = 10_000L, origin = 60_000L, at = 1L)
        val segunda = charge(number = 1, total = 6, perCharge = 10_000L, origin = 120_000L, at = 2L)
        assertEquals(2, Installments.plans(listOf(primera, segunda)).size)

        // Y el comercio separa dos compras del mismo precio.
        val otraTienda = charge(number = 1, total = 6, perCharge = 10_000L, merchant = "PARIS")
        assertEquals(2, Installments.plans(listOf(primera, otraTienda)).size)
    }

    @Test
    fun `lo que mas se debe va primero`() {
        val grande = charge(number = 1, total = 12, perCharge = 100_000L, merchant = "PARIS")
        val chica = charge(number = 1, total = 3, perCharge = 10_000L, merchant = "LIDER")

        val plans = Installments.plans(listOf(chica, grande))

        assertEquals(listOf("PARIS", "LIDER"), plans.map { it.merchant })
    }

    @Test
    fun `si el banco no dijo el numero se cuentan las cuotas anotadas`() {
        val cuotas = (1..2).map { index ->
            charge(number = null, total = 6, perCharge = 10_000L, at = index.toLong())
        }

        val plan = Installments.plans(cuotas).single()

        assertEquals(2, plan.paid)
        assertEquals(4, plan.remaining)
        assertEquals(40_000L, plan.pending)
    }

    /** Una cuota de cartola: un gasto, del monto que se cobró ese mes. */
    private fun charge(
        number: Int?,
        total: Int?,
        perCharge: Long,
        merchant: String = "LIDER",
        origin: Long? = null,
        at: Long = 1L,
    ) = TransactionEntity(
        id = at * 100 + (number ?: 0),
        accountId = 1L,
        occurredAt = at,
        amount = perCharge,
        direction = Direction.DEBIT,
        merchantRaw = merchant,
        merchantNorm = merchant.lowercase(),
        installmentNumber = number,
        installmentTotal = total,
        originAmount = origin,
    )
}
