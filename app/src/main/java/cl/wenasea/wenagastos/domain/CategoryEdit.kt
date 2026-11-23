package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.CategoryNature

/**
 * Lo que el diálogo de una categoría dejó escrito, ya leído: nombre sin espacios de más, emoji,
 * color y presupuesto. No lleva id: el ViewModel sabe si está creando o editando.
 */
data class CategoryFields(
    val name: String,
    val emoji: String,
    val colorHex: String,
    val kind: CategoryKind,
    val nature: CategoryNature,
    val monthlyBudget: Long?,
)

/** Resultado de leer el diálogo: o `error` con el primer problema, o los campos. Nunca los dos. */
data class CategoryParse(val fields: CategoryFields? = null, val error: String? = null)

/**
 * Lectura y validación del alta y la edición de una categoría (Fase 9.5).
 *
 * Los mensajes viven acá y no en el diálogo para poder probarlos sin pantalla, igual que
 * [Credit.parse]. La lista de las demás categorías entra como parámetro para poder rechazar un
 * nombre repetido sin que el diálogo tenga que saber de la base.
 */
object CategoryEdit {

    /** Lo que cabe en una fila de la lista sin cortarse. */
    const val MAX_NAME = 32

    /** Emoji de reserva cuando el campo queda vacío: una categoría sin ícono no se encuentra. */
    const val FALLBACK_EMOJI = "🏷️"

    /** Color de reserva, el mismo gris que usa el respaldo cuando falta. */
    const val FALLBACK_COLOR = "#607D8B"

    fun parse(
        name: String,
        emoji: String,
        colorHex: String,
        kind: CategoryKind,
        nature: CategoryNature,
        budget: String,
        others: List<CategoryEntity> = emptyList(),
    ): CategoryParse {
        val cleanName = name.trim().replace(WHITESPACE, " ")
        if (cleanName.isEmpty()) return CategoryParse(error = "Ponle un nombre")
        if (cleanName.length > MAX_NAME) {
            return CategoryParse(error = "El nombre no puede pasar de $MAX_NAME letras")
        }
        // Un traspaso no se clasifica: no puede haber una categoría de ese tipo creada a mano.
        if (kind == CategoryKind.TRANSFER) {
            return CategoryParse(error = "Un traspaso no lleva categoría")
        }
        if (others.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return CategoryParse(error = "Ya hay una categoría llamada «$cleanName»")
        }

        val budgetText = budget.filter(Char::isDigit)
        val monthlyBudget = if (budgetText.isBlank()) null else Money.parse(budgetText)
        if (budgetText.isNotBlank() && monthlyBudget == null) {
            return CategoryParse(error = "El presupuesto va en pesos y es mayor que cero")
        }

        return CategoryParse(
            fields = CategoryFields(
                name = cleanName,
                emoji = emoji.trim().ifEmpty { FALLBACK_EMOJI },
                colorHex = colorHex.takeIf { HEX.matches(it) } ?: FALLBACK_COLOR,
                kind = kind,
                // La naturaleza es de los gastos: en un ingreso se guarda la de menos suposición
                // y no se muestra en ninguna parte.
                nature = if (kind == CategoryKind.EXPENSE) nature else CategoryNature.VARIABLE,
                monthlyBudget = monthlyBudget,
            ),
        )
    }

    private val WHITESPACE = Regex("\\s+")

    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
}
