package cl.wenasea.wenagastos.data.local.entity

/** Instituciones soportadas. Google Wallet se usa como fuente de datos, no como cuenta. */
enum class Institution(val label: String, val emoji: String) {
    SANTANDER("Santander", "🏦"),
    FALABELLA("Falabella", "🏬"),
    MERCADOPAGO("MercadoPago", "💙"),
    CASH("Efectivo", "💵"),
    OTHER("Otra", "🏛️"),
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
