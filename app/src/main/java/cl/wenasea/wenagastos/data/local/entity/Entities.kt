package cl.wenasea.wenagastos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["institution", "kind", "accountLast4"], unique = false)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val institution: Institution,
    val kind: AccountKind,
    /**
     * Número de la cuenta que el banco muestra en los avisos de una transferencia
     * (`cuenta 000095441483` → `1483`). Uno solo: una cuenta tiene varias tarjetas, pero un
     * único número de cuenta.
     */
    val accountLast4: String? = null,
    /**
     * Números de tarjeta de esta cuenta, separados por coma: los de la tarjeta de débito en las
     * compras (la plata es la misma que la de la cuenta) o el de la tarjeta cuando la cuenta es
     * de crédito. Ej. `"4321"`. El cruce con los avisos es por número exacto, no por parecido.
     */
    val cardLast4: String? = null,
    val colorHex: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Cupo de una tarjeta de crédito, en pesos (Fase 7.1). `null` es "no lo he configurado", que
     * no es lo mismo que cero: sin cupo no se puede decir cuánto queda disponible.
     */
    val creditLimit: Long? = null,
    /**
     * Día del mes en que el banco cierra la facturación (1–31). Con esto el detalle puede decir
     * cuánto se ha gastado en el ciclo que corre. Igual que [creditLimit], solo tiene sentido en
     * una tarjeta de crédito y `null` es "no se sabe", no el día 1.
     */
    val statementDay: Int? = null,
    /** Día del mes en que vence el pago de esa factura (1–31). Opcional. */
    val paymentDay: Int? = null,
)

/**
 * Saldo confirmado de una cuenta a una fecha: el corte desde el que se calcula el saldo actual
 * (Fase 7). No se guarda "el saldo de hoy", porque se vence al día siguiente: se guarda lo que el
 * banco dijo una vez y desde ahí se suman los movimientos confirmados posteriores.
 *
 * [asOf] es el instante del corte e **incluye** todo lo ocurrido hasta ahí; por eso el cálculo
 * suma solo los movimientos con `occurredAt` estrictamente mayor. [balance] puede ser negativo
 * (un crédito usado o un sobregiro son deudas, no errores). La cuenta —no la tarjeta— es la dueña
 * del saldo: la de débito gasta de la misma cuenta.
 */
@Entity(
    tableName = "account_snapshots",
    indices = [Index(value = ["accountId", "asOf"])],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AccountSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Epoch millis del corte: el saldo ya incluye lo que pasó hasta este instante. */
    val asOf: Long,
    /** Saldo en pesos chilenos tal como lo dice el banco. */
    val balance: Long,
    val source: SnapshotSource = SnapshotSource.MANUAL,
    /** Lo que anotó el usuario o la fila de la cartola de la que salió. */
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val kind: CategoryKind,
    val monthlyBudget: Long? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    /**
     * Fijo, variable o ahorro (Fase 9.5). El valor por defecto es el de la migración 7 → 8: una
     * categoría vieja que no se clasificó queda como variable, que es lo que menos supone. En una
     * categoría de ingreso se guarda [CategoryNature.VARIABLE] y no se lee.
     */
    @ColumnInfo(defaultValue = "VARIABLE") val nature: CategoryNature = CategoryNature.VARIABLE,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("occurredAt"),
        Index(value = ["dedupeHash"], unique = false),
        Index("transferAccountId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Epoch millis del movimiento (hora local de Chile al momento de registrarlo). */
    val occurredAt: Long,
    /** Monto en pesos chilenos, siempre positivo. */
    val amount: Long,
    val direction: Direction,
    val merchantRaw: String,
    val merchantNorm: String,
    val categoryId: Long? = null,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val isExpense: Boolean = true,
    val isReceivable: Boolean = false,
    val isTransfer: Boolean = false,
    /**
     * La otra pata del traspaso: la cuenta que recibe (si este movimiento sale) o la que manda
     * (si este movimiento entra). Se guarda sin FK a propósito, para que la migración sea solo
     * `ALTER TABLE`; una referencia huérfana se muestra como "Cuenta" y no rompe nada.
     */
    val transferAccountId: Long? = null,
    /** Identificador común de las dos patas de un mismo traspaso. */
    val transferGroupId: Long? = null,
    /**
     * Cuota que es este movimiento dentro de una compra en cuotas (Fase 9.3): `3` de
     * [installmentTotal]. `null` cuando la compra se pagó de una vez, que es el caso normal.
     *
     * Se guarda como número y no como el texto `3/12` de la cartola, para poder ordenar y agrupar
     * sin depender de cómo lo escribió el banco.
     */
    val installmentNumber: Int? = null,
    /** Cuántas cuotas tiene la compra; `null` cuando no va en cuotas. */
    val installmentTotal: Int? = null,
    /**
     * Precio de la compra completa, cuando la cartola lo informa (`MONTO ORIGEN`). La cuota de
     * [amount] es lo que se pagó este mes; esto es lo que costó la compra, y sirve para decir
     * cuánto falta pagar sin adivinar.
     */
    val originAmount: Long? = null,
    val notes: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val rawText: String? = null,
    val dedupeHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Regla aprendida: la categoría que el usuario eligió para un comercio.
 *
 * La clave es el comercio normalizado (`TextNorm.key`) **más el sentido**, porque el mismo
 * nombre puede ser gasto ("Transferiste a MARIA") e ingreso ("Recibiste de MARIA") y cada uno
 * tiene su propia categoría. `categoryId` es `SET_NULL`: si la categoría se borra, la regla
 * queda huérfana y el motor la ignora en vez de romper.
 */
@Entity(
    tableName = "merchant_rules",
    indices = [
        Index(value = ["merchantKey", "direction"], unique = true),
        // Room exige índice para la FK: sin él, borrar una categoría escanea la tabla completa.
        Index("categoryId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Comercio normalizado: `TextNorm.key(merchant)`. */
    val merchantKey: String,
    /** El comercio tal como aparece en el aviso, para mostrarlo en Ajustes. */
    val merchantLabel: String,
    val direction: Direction,
    val categoryId: Long? = null,
    /** Cuántas veces se ha aplicado o corregido la regla. Ordena la lista de Ajustes. */
    val uses: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
