package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.domain.Dates

/**
 * Los archivos que salen de la app: CSV para mirar en una planilla y JSON para programar.
 *
 * Acá no hay Android: recibe los movimientos y los nombres ya resueltos y devuelve texto. Así
 * el formato se prueba en la JVM y el ViewModel solo se preocupa de escribir el archivo.
 *
 * El monto va como entero en pesos (sin puntos ni signo) y el sentido en la columna `tipo`:
 * una planilla puede sumar `monto` sin parsear nada y el signo no se pierde.
 */
object Export {

    const val APP = "Wenagastos"
    const val VERSION = 1

    private val HEADERS = listOf(
        "id",
        "fecha",
        "hora",
        "monto",
        "tipo",
        "cuenta",
        "cuenta_destino",
        "categoria",
        "comercio",
        "estado",
        "origen",
        "notas",
        "texto_aviso",
    )

    fun transactionsCsv(
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>,
    ): String = Csv.write(
        headers = HEADERS,
        rows = transactions.map { row(it, accounts, categories) },
    )

    fun transactionsJson(
        transactions: List<TransactionEntity>,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>,
        generatedAt: Long = System.currentTimeMillis(),
    ): String = Json.write(
        linkedMapOf(
            "app" to APP,
            "documento" to "movimientos",
            "version" to VERSION,
            "generadoEn" to Dates.isoDateTime(generatedAt),
            "resumen" to linkedMapOf(
                "movimientos" to transactions.size,
                "cuentas" to accounts.size,
                "categorias" to categories.size,
            ),
            "movimientos" to transactions.map { movement(it, accounts, categories) },
        ),
    )

    /** Una fila del CSV, con los nombres resueltos y el tipo ya decidido. */
    fun row(
        transaction: TransactionEntity,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>,
    ): List<String> = listOf(
        transaction.id.toString(),
        Dates.isoDate(transaction.occurredAt),
        Dates.clockLabel(transaction.occurredAt),
        transaction.amount.toString(),
        typeLabel(transaction),
        accountName(transaction.accountId, accounts),
        transaction.transferAccountId?.let { accountName(it, accounts) }.orEmpty(),
        categoryName(transaction.categoryId, categories),
        transaction.merchantRaw,
        transaction.status.label,
        transaction.source.label,
        transaction.notes.orEmpty(),
        transaction.rawText.orEmpty(),
    )

    /** El mismo movimiento como objeto JSON. Los campos vacíos no se escriben. */
    fun movement(
        transaction: TransactionEntity,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>,
    ): Map<String, Any> = linkedMapOf<String, Any>().apply {
        put("id", transaction.id)
        put("fecha", Dates.isoDate(transaction.occurredAt))
        put("hora", Dates.clockLabel(transaction.occurredAt))
        put("monto", transaction.amount)
        put("tipo", typeLabel(transaction))
        put("cuenta", accountName(transaction.accountId, accounts))
        transaction.transferAccountId?.let { put("cuentaDestino", accountName(it, accounts)) }
        put("categoria", categoryName(transaction.categoryId, categories))
        put("comercio", transaction.merchantRaw)
        put("estado", transaction.status.label)
        put("origen", transaction.source.label)
        transaction.notes?.takeIf { it.isNotBlank() }?.let { put("notas", it) }
        transaction.rawText?.takeIf { it.isNotBlank() }?.let { put("textoAviso", it) }
    }

    private fun typeLabel(transaction: TransactionEntity): String = when {
        transaction.isTransfer -> "Traspaso"
        transaction.direction == Direction.DEBIT -> "Gasto"
        else -> "Ingreso"
    }

    private fun accountName(id: Long, accounts: Map<Long, AccountEntity>): String =
        accounts[id]?.name ?: "Cuenta $id"

    private fun categoryName(id: Long?, categories: Map<Long, CategoryEntity>): String =
        id?.let { categories[it]?.name } ?: "Sin categoría"
}
