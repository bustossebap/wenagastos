package cl.wenasea.wenagastos.importexport

import java.time.LocalDate

/**
 * Lo que el estado de cuenta dice de la cuenta **fuera** de la tabla de movimientos (Fase 9.6).
 *
 * Una cartola de cuenta corriente no trae nada de esto: su saldo viene en una columna, al lado de
 * cada movimiento. Un estado de cuenta de tarjeta, en cambio, no informa saldo —no hay plata en una
 * tarjeta— pero sí imprime, arriba y en letra grande, cuánto llegó la factura, cuánto se debe hoy y
 * de cuánto es el cupo. Son las cifras que el banco ya calculó y que la app no puede deducir de las
 * filas, porque las filas son sólo el período que ese estado de cuenta detalla.
 *
 * Los tres montos van en positivo: el sentido (deuda, cupo) lo pone quien los lee.
 */
data class StatementSummary(
    /**
     * Lo que se debe hoy según el banco: el **cupo utilizado**. Es más que la factura del mes,
     * porque incluye las cuotas que todavía no se facturan, y es el número que sirve para el saldo
     * de la tarjeta.
     */
    val debt: Long? = null,
    /** El **monto total facturado a pagar** de este período: la factura que vence. */
    val billed: Long? = null,
    /** El **cupo total** de la línea. */
    val limit: Long? = null,
    /** La fecha de facturación: a ese día corresponden las cifras de arriba. */
    val asOf: LocalDate? = null,
    /** El vencimiento del pago de la factura ("Pagar Hasta"), si el banco lo imprime. */
    val due: LocalDate? = null,
) {

    /** `true` cuando el archivo no traía ninguna de las cifras. */
    val isEmpty: Boolean
        get() = debt == null && billed == null && limit == null && asOf == null && due == null
}

/**
 * Un archivo de cartola ya leído: su tabla de movimientos y, cuando el banco las imprime, las
 * cifras de su resumen. El CSV y el Excel traen sólo la tabla.
 */
data class StatementFile(
    val sheet: Csv.Sheet,
    val summary: StatementSummary? = null,
)
