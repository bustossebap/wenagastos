package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.CategoryNature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La lectura del diálogo de categorías. Se prueba sin pantalla porque los mensajes que ve el
 * usuario son el contrato: un nombre repetido o un presupuesto vacío tienen que decir qué pasó.
 */
class CategoryEditTest {

    private val supermarket = CategoryEntity(
        id = 1,
        name = "Supermercado",
        emoji = "🛒",
        colorHex = "#2E7D32",
        kind = CategoryKind.EXPENSE,
        sortOrder = 10,
    )

    private fun parse(
        name: String = "Arriendo",
        emoji: String = "🔑",
        colorHex: String = "#5E35B1",
        kind: CategoryKind = CategoryKind.EXPENSE,
        nature: CategoryNature = CategoryNature.FIXED,
        budget: String = "",
        others: List<CategoryEntity> = listOf(supermarket),
    ) = CategoryEdit.parse(name, emoji, colorHex, kind, nature, budget, others)

    @Test
    fun `una categoria completa se guarda con su naturaleza y su presupuesto`() {
        val result = parse(budget = "450000")

        assertNull(result.error)
        assertNotNull(result.fields)
        val fields = result.fields!!
        assertEquals("Arriendo", fields.name)
        assertEquals(CategoryNature.FIXED, fields.nature)
        assertEquals(450_000L, fields.monthlyBudget)
    }

    @Test
    fun `un nombre vacio no crea nada`() {
        val result = parse(name = "   ")

        assertNull(result.fields)
        assertEquals("Ponle un nombre", result.error)
    }

    @Test
    fun `un nombre repetido se rechaza sin importar mayusculas ni espacios`() {
        val result = parse(name = "  superMercado ")

        assertNull(result.fields)
        assertEquals("Ya hay una categoría llamada «superMercado»", result.error)
    }

    @Test
    fun `el presupuesto vacio es sin tope y un cero se rechaza`() {
        val without = parse(budget = "")
        assertNull(without.error)
        assertNull(without.fields?.monthlyBudget)

        // El campo del diálogo solo deja dígitos; si igual llega texto, no hay monto que leer.
        val noDigits = parse(budget = "abc")
        assertNull(noDigits.error)
        assertNull(noDigits.fields?.monthlyBudget)

        val zero = parse(budget = "0")
        assertNull(zero.fields)
        assertEquals("El presupuesto va en pesos y es mayor que cero", zero.error)
    }

    @Test
    fun `una categoria de ingreso no lleva naturaleza`() {
        val result = parse(kind = CategoryKind.INCOME, nature = CategoryNature.SAVINGS)

        assertEquals(CategoryNature.VARIABLE, result.fields?.nature)
        assertEquals(CategoryKind.INCOME, result.fields?.kind)
    }

    @Test
    fun `un emoji vacio y un color invalido caen en los valores de reserva`() {
        val result = parse(emoji = "  ", colorHex = "verde")

        assertEquals(CategoryEdit.FALLBACK_EMOJI, result.fields?.emoji)
        assertEquals(CategoryEdit.FALLBACK_COLOR, result.fields?.colorHex)
    }

    @Test
    fun `un traspaso no se puede crear como categoria`() {
        val result = parse(kind = CategoryKind.TRANSFER)

        assertNull(result.fields)
        assertEquals("Un traspaso no lleva categoría", result.error)
    }
}
