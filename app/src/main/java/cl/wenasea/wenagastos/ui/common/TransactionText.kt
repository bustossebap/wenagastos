package cl.wenasea.wenagastos.ui.common

import androidx.compose.ui.graphics.Color
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.domain.Installments
import cl.wenasea.wenagastos.domain.transferEnds

/**
 * Cómo se lee un movimiento en Inicio, Movimientos y el detalle de Reportes. Recibe los mapas de
 * categorías y cuentas en vez de un estado concreto, para que las tres pantallas usen el mismo
 * texto sin duplicar la lógica.
 *
 * Un traspaso no tiene categoría —la plata no se gastó, cambió de cuenta— así que se dibuja con
 * `↔️` y no con el `❓` de los gastos que quedaron sin clasificar.
 */
fun categoryEmoji(transaction: TransactionEntity, categories: Map<Long, CategoryEntity>): String {
    if (transaction.isTransfer) return TRANSFER_EMOJI
    return transaction.categoryId?.let { categories[it]?.emoji } ?: "❓"
}

fun categoryColor(transaction: TransactionEntity, categories: Map<Long, CategoryEntity>): Color =
    transaction.categoryId?.let { categories[it]?.colorHex?.asColor() } ?: Color.Gray

fun transactionSubtitle(
    transaction: TransactionEntity,
    categories: Map<Long, CategoryEntity>,
    accounts: Map<Long, AccountEntity>,
): String {
    val account = accounts[transaction.accountId]?.name ?: "Cuenta"
    // Un traspaso se lee igual desde las dos patas: "Santander → Falabella", sin categoría.
    if (transaction.isTransfer) {
        val ends = transaction.transferEnds()
        val origin = ends.originId?.let { accounts[it]?.name }
        val destination = ends.destinationId?.let { accounts[it]?.name }
        if (origin != null && destination != null) return "$origin → $destination"
        return "Traspaso · $account"
    }
    val category = transaction.categoryId?.let { categories[it]?.name } ?: "Sin categoría"
    val base = "$category · $account"
    // Una cuota se anota como cualquier gasto del mes, así que la fila dice en cuál va: sin eso,
    // «Cuotas por pagar» en Reportes y la lista de Movimientos no se podrían cuadrar a ojo.
    val installment = Installments.shortLabel(transaction) ?: return base
    return "$base · $installment"
}

/** El mismo par de cuentas que se lee en la lista, para el globo del avatar. */
const val TRANSFER_EMOJI = "↔️"
