package cl.wenasea.wenagastos.data.repo

import cl.wenasea.wenagastos.capture.fakes.FakeCategoryDao
import cl.wenasea.wenagastos.capture.fakes.FakeMerchantRuleDao
import cl.wenasea.wenagastos.data.Seeder
import cl.wenasea.wenagastos.data.local.entity.Direction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Reglas aprendidas por comercio: la llave es comercio + sentido, y volver a enseñar el mismo
 * comercio corrige la fila en vez de crear otra.
 */
class MerchantRuleRepositoryTest {

    private lateinit var ruleDao: FakeMerchantRuleDao
    private lateinit var categories: FakeCategoryDao
    private lateinit var rules: MerchantRuleRepository

    private var saludId = 0L
    private var restaurantesId = 0L
    private var otrosIngresosId = 0L

    @Before
    fun setUp() {
        ruleDao = FakeMerchantRuleDao()
        categories = FakeCategoryDao()
        categories.seedAll(Seeder.defaultCategories())
        saludId = categories.stored.first { it.name == "Salud" }.id
        restaurantesId = categories.stored.first { it.name == "Restaurantes" }.id
        otrosIngresosId = categories.stored.first { it.name == "Otros ingresos" }.id
        rules = MerchantRuleRepository(ruleDao, CategoryRepository(categories))
    }

    @Test
    fun `recordar dos veces el mismo comercio corrige la regla y suma un uso`() = runBlocking {
        rules.remember("FARMACIA AHUMADA", Direction.DEBIT, saludId)
        rules.remember("Farmacia  Ahumada", Direction.DEBIT, restaurantesId)

        val row = ruleDao.stored.single()
        assertEquals("farmacia ahumada", row.merchantKey)
        assertEquals(restaurantesId, row.categoryId)
        assertEquals(2, row.uses)
    }

    @Test
    fun `el mismo comercio en otro sentido es otra regla`() = runBlocking {
        rules.remember("JUAN PEREZ", Direction.DEBIT, restaurantesId)
        rules.remember("JUAN PEREZ", Direction.CREDIT, otrosIngresosId)

        assertEquals(2, ruleDao.stored.size)
        assertEquals(restaurantesId, rules.resolve("JUAN PEREZ", Direction.DEBIT))
        assertEquals(otrosIngresosId, rules.resolve("JUAN PEREZ", Direction.CREDIT))
    }

    @Test
    fun `una regla cuya categoria se archivo no se resuelve`() = runBlocking {
        rules.remember("LIDER EXPRESS", Direction.DEBIT, saludId)
        assertEquals(saludId, rules.resolve("LIDER EXPRESS", Direction.DEBIT))
        assertNull(rules.resolve("OTRO COMERCIO", Direction.DEBIT))

        categories.update(categories.stored.first { it.id == saludId }.copy(isArchived = true))

        assertNull(rules.resolve("LIDER EXPRESS", Direction.DEBIT))
    }

    @Test
    fun `olvidar borra la regla`() = runBlocking {
        rules.remember("LIDER EXPRESS", Direction.DEBIT, saludId)
        val rule = ruleDao.stored.single()

        rules.forget(rule.id)

        assertEquals(0, rules.count())
        assertNull(rules.resolve("LIDER EXPRESS", Direction.DEBIT))
    }
}
