package cl.wenasea.wenagastos.domain

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity

/**
 * Un traspaso entre cuentas propias se puede registrar desde cualquiera de sus dos patas:
 *
 * - la que **sale** (`direction = DEBIT`), con `accountId` = cuenta de origen;
 * - la que **entra** (`direction = CREDIT`), con `accountId` = cuenta de destino.
 *
 * En los dos casos `transferAccountId` guarda el otro extremo. Estas funciones traducen entre
 * esa representación y el par "origen → destino", que es el que se muestra y el que se edita:
 * así "Santander → Falabella" se lee igual en las dos filas.
 */
data class TransferEnds(
    val originId: Long?,
    val destinationId: Long?,
)

/** Cuenta de origen y de destino de este movimiento, mirando su sentido. */
fun TransactionEntity.transferEnds(): TransferEnds =
    if (direction == Direction.DEBIT) {
        TransferEnds(originId = accountId, destinationId = transferAccountId)
    } else {
        TransferEnds(originId = transferAccountId, destinationId = accountId)
    }

/** Las dos columnas que se guardan, a partir del origen y destino que eligió el usuario. */
data class TransferColumns(
    val accountId: Long,
    val transferAccountId: Long,
)

/** Traduce "origen → destino" a las columnas `accountId` / `transferAccountId`. */
fun transferColumns(
    direction: Direction,
    originId: Long,
    destinationId: Long,
): TransferColumns = if (direction == Direction.DEBIT) {
    TransferColumns(accountId = originId, transferAccountId = destinationId)
} else {
    TransferColumns(accountId = destinationId, transferAccountId = originId)
}

/** El sentido contrario: la otra pata del mismo traspaso. */
fun Direction.opposite(): Direction =
    if (this == Direction.DEBIT) Direction.CREDIT else Direction.DEBIT
