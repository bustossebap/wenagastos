package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import java.time.LocalDate
import java.time.YearMonth

/** Un mes de la serie mensual del reporte. */
data class MonthPoint(
    val month: YearMonth,
    val expenses: Long,
    val income: Long,
)

/**
 * Qué tan cerca está una categoría de su presupuesto.
 * [OK] mientras va bajo el 80 %, [NEAR] desde el 80 % y [OVER] al llegar o pasarse.
 */
enum class BudgetLevel { OK, NEAR, OVER }

data class BudgetStatus(
    val budget: Long,
    val spent: Long,
    /** Lo que queda; negativo cuando ya se pasó. */
    val remaining: Long,
    /** `spent / budget`; puede pasar de 1. */
    val fraction: Float,
    val level: BudgetLevel,
)

/** Una categoría del reporte del mes, con su parte del total y su presupuesto si tiene. */
data class CategoryTotal(
    val category: CategoryEntity?,
    val spent: Long,
    val share: Float,
    val budget: BudgetStatus?,
)

/**
 * Los números de Reportes y presupuestos. Todo se calcula desde la lista de movimientos del mes,
 * así Reportes, Inicio y las alertas nunca dicen cosas distintas.
 *
 * Igual que en el inicio, **no cuentan** los movimientos *Por revisar* (todavía no se sabe con qué
 * cuenta se pagaron) ni los traspasos entre cuentas propias (la plata no se gastó).
 */
object Reports {

    /** Desde qué parte del presupuesto se avisa que se está cerca del límite. */
    const val NEAR_RATIO = 0.8f

    fun counted(transactions: List<TransactionEntity>): List<TransactionEntity> =
        transactions.filter { it.status == TransactionStatus.CONFIRMED && !it.isTransfer }

    fun expenses(transactions: List<TransactionEntity>): Long =
        counted(transactions).filter { it.direction == Direction.DEBIT }.sumOf { it.amount }

    fun income(transactions: List<TransactionEntity>): Long =
        counted(transactions).filter { it.direction == Direction.CREDIT }.sumOf { it.amount }

    /**
     * Gastos e ingresos de [months] (en orden, normalmente del más viejo al más nuevo). Los meses
     * sin movimientos quedan en cero: la serie siempre tiene el mismo largo.
     */
    fun series(
        transactions: List<TransactionEntity>,
        months: List<YearMonth>,
    ): List<MonthPoint> {
        val totals = counted(transactions).groupBy { YearMonth.from(Dates.toLocalDate(it.occurredAt)) }
        return months.map { month ->
            val ofMonth = totals[month].orEmpty()
            MonthPoint(
                month = month,
                expenses = ofMonth.filter { it.direction == Direction.DEBIT }.sumOf { it.amount },
                income = ofMonth.filter { it.direction == Direction.CREDIT }.sumOf { it.amount },
            )
        }
    }

    /**
     * Gastos del mes agrupados por categoría, de mayor a menor. Lo que no tiene categoría cae en
     * una fila propia (`category == null`) para que las partes siempre sumen el 100 %.
     */
    fun byCategory(
        monthTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): List<CategoryTotal> {
        val total = expenses(monthTransactions)
        val byId = categories.associateBy { it.id }
        return counted(monthTransactions)
            .filter { it.direction == Direction.DEBIT }
            .groupBy { it.categoryId }
            .map { (categoryId, group) ->
                val category = categoryId?.let { byId[it] }
                val spent = group.sumOf { it.amount }
                CategoryTotal(
                    category = category,
                    spent = spent,
                    share = if (total > 0) spent.toFloat() / total else 0f,
                    budget = budgetStatus(spent, category?.monthlyBudget),
                )
            }
            .sortedByDescending { it.spent }
    }

    /**
     * Promedio diario del mes: en el mes en curso divide por los días corridos (así el número es
     * comparable a mitad de mes) y en los meses pasados por los días que tiene el mes.
     */
    fun dailyAverage(
        monthTransactions: List<TransactionEntity>,
        yearMonth: YearMonth,
        today: LocalDate = Dates.today(),
    ): Long {
        val total = expenses(monthTransactions)
        if (total <= 0L) return 0L
        val current = YearMonth.from(today)
        val days = when {
            yearMonth.isAfter(current) -> return 0L
            yearMonth == current -> today.dayOfMonth
            else -> yearMonth.lengthOfMonth()
        }
        if (days <= 0) return 0L
        return total / days
    }

    /** Presupuesto de una categoría contra lo gastado; `null` cuando no hay presupuesto. */
    fun budgetStatus(spent: Long, budget: Long?): BudgetStatus? {
        if (budget == null || budget <= 0L) return null
        val fraction = spent.toFloat() / budget
        val level = when {
            spent >= budget -> BudgetLevel.OVER
            fraction >= NEAR_RATIO -> BudgetLevel.NEAR
            else -> BudgetLevel.OK
        }
        return BudgetStatus(
            budget = budget,
            spent = spent,
            remaining = budget - spent,
            fraction = fraction,
            level = level,
        )
    }

    /** Categorías que ya están cerca del límite o pasadas, la más avanzada primero. */
    fun budgetAlerts(
        monthTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): List<CategoryTotal> = byCategory(monthTransactions, categories)
        .filter { it.budget != null && it.budget!!.level != BudgetLevel.OK }
        .sortedByDescending { it.budget?.fraction ?: 0f }

    /** Suma de los presupuestos de las categorías de gasto activas. */
    fun totalBudget(categories: List<CategoryEntity>): Long = categories
        .filter { it.kind == CategoryKind.EXPENSE && !it.isArchived }
        .sumOf { it.monthlyBudget ?: 0L }
}

