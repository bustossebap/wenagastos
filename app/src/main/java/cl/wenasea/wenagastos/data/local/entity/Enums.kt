package cl.wenasea.wenagastos.data.local.entity

/**
 * Instituciones soportadas. Google Wallet se usa como fuente de datos, no como cuenta.
 * [colorHex] es el color por defecto de una cuenta nueva de esa institución, así el alta no
 * tiene que pedir un color aparte.
 */
enum class Institution(val label: String, val emoji: String, val colorHex: String) {
    SANTANDER("Santander", "🏦", "#EC0000"),
    FALABELLA("Falabella", "🏬", "#7CB342"),
    MERCADOPAGO("MercadoPago", "💙", "#00A1E0"),
    CASH("Efectivo", "💵", "#FFB300"),
    OTHER("Otra", "🏛️", "#607D8B"),
}

enum class AccountKind(val label: String) {
    CHECKING("Cuenta corriente"),
    DEBIT("Tarjeta de débito"),
    CREDIT("Tarjeta de crédito"),
    SAVINGS("Ahorro / billetera"),
    CASH("Efectivo"),
}

/** DEBIT = sale plata de la cuenta. CREDIT = entra plata. */
enum class Direction { DEBIT, CREDIT }

enum class CategoryKind(val label: String) {
    EXPENSE("Gasto"),
    INCOME("Ingreso"),
    TRANSFER("Transferencia"),
}

/**
 * De qué clase es un gasto (Fase 9.5): lo que se paga sí o sí, lo que depende del mes y la plata
 * que se guarda. Una categoría de gasto tiene una sola naturaleza y el editor la usa para ofrecer
 * solo las categorías del tipo elegido: quien anota un arriendo no quiere ver «Gustitos» al lado.
 *
 * El ahorro está acá aunque no sea un gasto: sale de la cuenta igual y hay que verlo aparte, no
 * mezclado con lo que se consumió.
 */
enum class CategoryNature(val label: String) {
    FIXED("Fijo"),
    VARIABLE("Variable"),
    SAVINGS("Ahorro"),
}

enum class TransactionSource(val label: String) {
    MANUAL("Manual"),
    NOTIFICATION("Notificación"),
    WALLET("Google Wallet"),
    SMS("SMS"),
    CSV("Cartola"),
}

enum class TransactionStatus(val label: String) {
    PENDING("Por revisar"),
    CONFIRMED("Confirmado"),
    IGNORED("Ignorado"),
}

/**
 * De dónde salió un saldo confirmado: escrito a mano en la app o leído de una cartola del banco
 * (Fase 8). La diferencia es solo para mostrarla: los dos son cortes iguales de fiar.
 */
enum class SnapshotSource(val label: String) {
    MANUAL("A mano"),
    CARTOLA("Cartola"),
}
