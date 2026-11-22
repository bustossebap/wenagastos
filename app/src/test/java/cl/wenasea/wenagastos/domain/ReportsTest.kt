package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reportes: la serie mensual, las partes por categoría y los umbrales del presupuesto.
 * Todo tiene que cuadrar con los totales del inicio, así que los traspasos y lo *Por revisar*
 * nunca suman.
 */
class ReportsTest {

    private val noviembre = YearMonth.of(2026, 11)
    private val octubre = YearMonth.of(2026, 10)
    private val septiembre = YearMonth.of(2026, 9)

    @Test
    fun `la serie trae los meses pedidos y deja en cero los que no tienen movimientos`() {
        val points = Reports.series(
            listOf(
                tx(10_000L, date = LocalDate.of(2026, 11, 5)),
                tx(5_000L, date = LocalDate.of(2026, 9, 20), direction = Direction.CREDIT),
            ),
            months = listOf(septiembre, octubre, noviembre),
        )

        assertEquals(3, points.size)
        assertEquals(MonthPoint(septiembre, expenses = 0L, income = 5_000L), points[0])
        assertEquals(MonthPoint(octubre, expenses = 0L, income = 0L), points[1])
        assertEquals(MonthPoint(noviembre, expenses = 10_000L, income = 0L), points[2])
    }

    @Test
    fun `los traspasos y lo por revisar no entran en la serie`() {
        val points = Reports.series(
            listOf(
                tx(10_000L, date = LocalDate.of(2026, 11, 5)),
                tx(90_000L, date = LocalDate.of(2026, 11, 5), isTransfer = true),
                tx(70_000L, date = LocalDate.of(2026, 11, 6), status = TransactionStatus.PENDING),
            ),
            months = listOf(noviembre),
        )

        assertEquals(10_000L, points.single().expenses)
    }

    @Test
    fun `las partes por categoria dejan fuera los ingresos y suman uno`() {
        val totals = Reports.byCategory(
            listOf(
                tx(30_000L, categoryId = 1L),
                tx(10_000L, categoryId = 1L),
                tx(60_000L, categoryId = 2L),
                tx(20_000L, categoryId = null),
                tx(50_000L, categoryId = 2L, direction = Direction.CREDIT),
            ),
            listOf(category(1L, "Supermercado"), category(2L, "Bencina")),
        )

        assertEquals(listOf("Bencina", "Supermercado", "Sin categoría"), totals.map { it.category?.name ?: "Sin categoría" })
        assertEquals(60_000L, totals[0].spent)
        assertEquals(40_000L, totals[1].spent)
        assertEquals(20_000L, totals[2].spent)
        assertNull(totals[2].category)
        assertEquals(1f, totals.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `el grafico de partes se queda con las cinco que mas gastan y suma las demas`() {
        val totals = (1L..8L).map { id ->
            val parte = (9 - id)
            CategoryTotal(
                category = category(id, "C$id"),
                spent = parte * 10L,
                share = parte / 36f,
                budget = null,
            )
        }

        val top = Reports.topCategories(totals)

        assertEquals(5, top.head.size)
        assertEquals(
            listOf("C1", "C2", "C3", "C4", "C5"),
            top.head.map { it.category?.name },
        )
        assertEquals(3, top.restCount)
        assertEquals(6f / 36f, top.restShare, 0.001f)
        assertEquals(
            1f,
            top.head.sumOf { it.share.toDouble() }.toFloat() + top.restShare,
            0.001f,
        )
    }

    @Test
    fun `sin categorias que sumar el agregado queda vacio`() {
        val totals = Reports.byCategory(
            listOf(tx(30_000L, categoryId = 1L), tx(10_000L, categoryId = 2L)),
            listOf(category(1L, "Supermercado"), category(2L, "Bencina")),
        )

        val top = Reports.topCategories(totals)

        assertEquals(2, top.head.size)
        assertEquals(0, top.restCount)
        assertEquals(0f, top.restShare, 0.001f)
    }

    @Test
    fun `sin presupuesto no hay estado`() {
        assertNull(Reports.budgetStatus(10_000L, null))
        assertNull(Reports.budgetStatus(10_000L, 0L))
    }

    @Test
    fun `el semaforo del presupuesto esta OK bajo 80 y avisa desde 80`() {
        val bajo = Reports.budgetStatus(79_000L, 100_000L)!!
        assertEquals(BudgetLevel.OK, bajo.level)
        assertEquals(21_000L, bajo.remaining)

        assertEquals(BudgetLevel.NEAR, Reports.budgetStatus(80_000L, 100_000L)!!.level)
        assertEquals(BudgetLevel.NEAR, Reports.budgetStatus(99_000L, 100_000L)!!.level)

        val justo = Reports.budgetStatus(100_000L, 100_000L)!!
        assertEquals(BudgetLevel.OVER, justo.level)
        assertEquals(0L, justo.remaining)

        val pasado = Reports.budgetStatus(120_000L, 100_000L)!!
        assertEquals(BudgetLevel.OVER, pasado.level)
        assertEquals(-20_000L, pasado.remaining)
        assertEquals(1.2f, pasado.fraction, 0.001f)
    }

    @Test
    fun `las alertas son solo las que estan cerca o pasadas la mas avanzada primero`() {
        val alerts = Reports.budgetAlerts(
            listOf(
                tx(90_000L, categoryId = 1L),
                tx(60_000L, categoryId = 2L),
                tx(10_000L, categoryId = 3L),
            ),
            listOf(
                category(1L, "Delivery", budget = 100_000L),
                category(2L, "Bencina", budget = 50_000L),
                category(3L, "Salud", budget = 100_000L),
                category(4L, "Sin presupuesto"),
            ),
        )

        assertEquals(listOf("Bencina", "Delivery"), alerts.map { it.category?.name })
        assertEquals(BudgetLevel.OVER, alerts[0].budget?.level)
        assertEquals(BudgetLevel.NEAR, alerts[1].budget?.level)
    }

    @Test
    fun `el presupuesto total suma solo categorias de gasto activas`() {
        assertEquals(
            150_000L,
            Reports.totalBudget(
                listOf(
                    category(1L, "Delivery", budget = 100_000L),
                    category(2L, "Bencina", budget = 50_000L),
                    category(3L, "Sueldo", budget = 30_000L, kind = CategoryKind.INCOME),
                    category(4L, "Vieja", budget = 70_000L, archived = true),
                ),
            ),
        )
    }

    @Test
    fun `el promedio diario usa los dias corridos en el mes en curso`() {
        val movimientos = listOf(tx(100_000L, date = LocalDate.of(2026, 11, 3)))

        assertEquals(
            10_000L,
            Reports.dailyAverage(movimientos, noviembre, today = LocalDate.of(2026, 11, 10)),
        )
        assertEquals(
            3_333L,
            Reports.dailyAverage(movimientos, noviembre, today = LocalDate.of(2026, 12, 10)),
        )
        assertEquals(
            0L,
            Reports.dailyAverage(movimientos, noviembre, today = LocalDate.of(2026, 9, 1)),
        )
    }

    @Test
    fun `lo que cuenta para los totales es lo confirmado y no traspaso`() {
        val contados = Reports.counted(
            listOf(
                tx(1_000L),
                tx(2_000L, status = TransactionStatus.PENDING),
                tx(4_000L, isTransfer = true),
                tx(8_000L, status = TransactionStatus.IGNORED),
            ),
        )

        assertEquals(listOf(1_000L), contados.map { it.amount })
    }

    private fun tx(
        amount: Long,
        direction: Direction = Direction.DEBIT,
        categoryId: Long? = null,
        date: LocalDate = LocalDate.of(2026, 11, 10),
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        isTransfer: Boolean = false,
    ) = TransactionEntity(
        accountId = 1L,
        occurredAt = Dates.startOfDay(date),
        amount = amount,
        direction = direction,
        merchantRaw = "Comercio",
        merchantNorm = "comercio",
        categoryId = categoryId,
        status = status,
        isTransfer = isTransfer,
    )

    private fun category(
        id: Long,
        name: String,
        budget: Long? = null,
        kind: CategoryKind = CategoryKind.EXPENSE,
        archived: Boolean = false,
    ) = CategoryEntity(
        id = id,
        name = name,
        emoji = "🍔",
        colorHex = "#FF0000",
        kind = kind,
        monthlyBudget = budget,
        isArchived = archived,
    )
}

