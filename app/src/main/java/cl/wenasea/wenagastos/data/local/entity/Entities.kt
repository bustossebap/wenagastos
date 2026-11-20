package cl.wenasea.wenagastos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["institution", "kind", "last4"], unique = false)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val institution: Institution,
    val kind: AccountKind,
    val last4: String? = null,
    val colorHex: String,
    val isActive: Boolean = true,
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
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("occurredAt"),
        Index(value = ["dedupeHash"], unique = false),
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
    val transferGroupId: Long? = null,
    val notes: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val rawText: String? = null,
    val dedupeHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
