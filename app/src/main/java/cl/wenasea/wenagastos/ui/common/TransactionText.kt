package cl.wenasea.wenagastos.ui.common

import androidx.compose.ui.graphics.Color
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.domain.transferEnds

/**
 * Cómo se lee un movimiento en Inicio, Movimientos y el detalle de Reportes. Recibe los mapas de
 * categorías y cuentas en vez de un estado concreto, para que las tres pantallas usen el mismo
 * texto sin duplicar la lógica.
 */
fun categoryEmoji(transaction: TransactionEntity, categories: Map<Long, CategoryEntity>): String =
    transaction.categoryId?.let { categories[it]?.emoji } ?: "❓"

fun categoryColor(transaction: TransactionEntity, categories: Map<Long, CategoryEntity>): Color =
    transaction.categoryId?.let { categories[it]?.colorHex?.asColor() } ?: Color.Gray

fun transactionSubtitle(
    transaction: TransactionEntity,
    categories: Map<Long, CategoryEntity>,
    accounts: Map<Long, AccountEntity>,
): String {
    val category = transaction.categoryId?.let { categories[it]?.name } ?: "Sin categoría"
    val account = accounts[transaction.accountId]?.name ?: "Cuenta"
    // Un traspaso se lee igual desde las dos patas: "Santander → Falabella".
    if (transaction.isTransfer && transaction.transferAccountId != null) {
        val ends = transaction.transferEnds()
        val origin = ends.originId?.let { accounts[it]?.name }
        val destination = ends.destinationId?.let { accounts[it]?.name }
        if (origin != null && destination != null) return "$category · $origin → $destination"
    }
    return "$category · $account"
}
