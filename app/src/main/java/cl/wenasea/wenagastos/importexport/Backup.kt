package cl.wenasea.wenagastos.importexport

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.AccountSnapshotEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.CategoryNature
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.MerchantRuleEntity
import cl.wenasea.wenagastos.data.local.entity.SnapshotSource
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.domain.parseNumbers

/** Todo lo que viaja en un respaldo, ya en memoria. */
data class BackupData(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val rules: List<MerchantRuleEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    /** Cortes de saldo (Fase 7). Un respaldo viejo no los trae: se lee como lista vacía. */
    val snapshots: List<AccountSnapshotEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = accounts.isEmpty() &&
            categories.isEmpty() &&
            rules.isEmpty() &&
            transactions.isEmpty() &&
            snapshots.isEmpty()
}

/**
 * El contenido del respaldo: un JSON con las cuatro tablas, con los ids originales.
 *
 * Los ids son lo que permite que un traspaso siga apuntando a su otra pata, que una regla
 * aprendida siga apuntando a su categoría y que dos respaldos del mismo día no se mezclen.
 * Por eso restaurar es reemplazar todo, no fusionar: es el único way de que los ids calcen.
 *
 * El formato es de la app, no una API pública, pero es legible: si algún día hay que rescatar
 * datos a mano, el archivo se entiende sin mirar el código.
 */
object Backup {

    const val DOCUMENT = "respaldo"

    /**
     * v2 partió `last4` en el número de la cuenta y los de sus tarjetas. Un v1 se sigue leyendo:
     * el primer número de una corriente/ahorro pasa a ser el de la cuenta y el resto tarjetas, y
     * en débito/crédito todo es tarjeta.
     *
     * v3 agrega `saldos`, los cortes de la Fase 7. Un archivo v2 se lee igual: sin cortes, el
     * saldo de las cuentas queda sin saberse hasta que se escriba uno nuevo.
     *
     * v4 agrega `creditLimit`, `statementDay` y `paymentDay` a las cuentas (Fase 7.1). Un v3 se
     * lee igual: las tres quedan en `null`, que es "no se sabe", y la tarjeta se muestra como
     * antes hasta que se configure.
     *
     * v5 agrega `nature` a las categorías (Fase 9.5). Un v4 se lee igual: toda categoría sin el
     * campo queda `VARIABLE`, que es el mismo valor que le puso la migración de la base.
     *
     * v6 agrega `installmentNumber`, `installmentTotal` y `originAmount` a los movimientos (Fase
     * 9.3). Un v5 se lee igual: las tres quedan en `null`, que es "no va en cuotas".
     */
    const val VERSION = 6

    fun encode(
        data: BackupData,
        schemaVersion: Int,
        generatedAt: Long = System.currentTimeMillis(),
    ): String = Json.write(
        linkedMapOf(
            "app" to Export.APP,
            "documento" to DOCUMENT,
            "version" to VERSION,
            "schema" to schemaVersion,
            "generadoEn" to Dates.isoDateTime(generatedAt),
            "resumen" to linkedMapOf(
                "cuentas" to data.accounts.size,
                "categorias" to data.categories.size,
                "reglas" to data.rules.size,
                "movimientos" to data.transactions.size,
                "saldos" to data.snapshots.size,
            ),
            "cuentas" to data.accounts.map(::accountJson),
            "categorias" to data.categories.map(::categoryJson),
            "reglas" to data.rules.map(::ruleJson),
            "movimientos" to data.transactions.map(::transactionJson),
            "saldos" to data.snapshots.map(::snapshotJson),
        ),
    )

    private fun accountJson(account: AccountEntity): Map<String, Any?> = linkedMapOf(
        "id" to account.id,
        "name" to account.name,
        "institution" to account.institution.name,
        "kind" to account.kind.name,
        "accountLast4" to account.accountLast4,
        "cardLast4" to account.cardLast4,
        "colorHex" to account.colorHex,
        "isActive" to account.isActive,
        "createdAt" to account.createdAt,
        "creditLimit" to account.creditLimit,
        "statementDay" to account.statementDay,
        "paymentDay" to account.paymentDay,
    )

    private fun categoryJson(category: CategoryEntity): Map<String, Any?> = linkedMapOf(
        "id" to category.id,
        "name" to category.name,
        "emoji" to category.emoji,
        "colorHex" to category.colorHex,
        "kind" to category.kind.name,
        "monthlyBudget" to category.monthlyBudget,
        "sortOrder" to category.sortOrder,
        "isArchived" to category.isArchived,
        "nature" to category.nature.name,
    )

    private fun ruleJson(rule: MerchantRuleEntity): Map<String, Any?> = linkedMapOf(
        "id" to rule.id,
        "merchantKey" to rule.merchantKey,
        "merchantLabel" to rule.merchantLabel,
        "direction" to rule.direction.name,
        "categoryId" to rule.categoryId,
        "uses" to rule.uses,
        "createdAt" to rule.createdAt,
        "updatedAt" to rule.updatedAt,
    )

    private fun transactionJson(transaction: TransactionEntity): Map<String, Any?> = linkedMapOf(
        "id" to transaction.id,
        "accountId" to transaction.accountId,
        "occurredAt" to transaction.occurredAt,
        "amount" to transaction.amount,
        "direction" to transaction.direction.name,
        "merchantRaw" to transaction.merchantRaw,
        "merchantNorm" to transaction.merchantNorm,
        "categoryId" to transaction.categoryId,
        "status" to transaction.status.name,
        "isExpense" to transaction.isExpense,
        "isReceivable" to transaction.isReceivable,
        "isTransfer" to transaction.isTransfer,
        "transferAccountId" to transaction.transferAccountId,
        "transferGroupId" to transaction.transferGroupId,
        // Fase 9.3: la cuota y el precio de la compra. Se escriben en `null` cuando no van en
        // cuotas, para que el archivo se lea tal como es.
        "installmentNumber" to transaction.installmentNumber,
        "installmentTotal" to transaction.installmentTotal,
        "originAmount" to transaction.originAmount,
        "notes" to transaction.notes,
        "source" to transaction.source.name,
        "rawText" to transaction.rawText,
        "dedupeHash" to transaction.dedupeHash,
        "createdAt" to transaction.createdAt,
        "updatedAt" to transaction.updatedAt,
    )

    private fun snapshotJson(snapshot: AccountSnapshotEntity): Map<String, Any?> = linkedMapOf(
        "id" to snapshot.id,
        "accountId" to snapshot.accountId,
        "asOf" to snapshot.asOf,
        "balance" to snapshot.balance,
        "source" to snapshot.source.name,
        "note" to snapshot.note,
        "createdAt" to snapshot.createdAt,
    )

    /** Lee un respaldo y valida que se pueda restaurar entero. Falla con un mensaje claro. */
    fun decode(text: String): BackupData {
        val root = root(text)
        // Un respaldo v1 guarda todos los números en `last4`: hay que repartirlos al leerlo.
        val legacyNumbers = (root.long("version") ?: 0) < 2
        val data = BackupData(
            accounts = root.children("cuentas").map { toAccount(it, legacyNumbers) },
            categories = root.children("categorias").map(::toCategory),
            rules = root.children("reglas").map(::toRule),
            transactions = root.children("movimientos").map(::toTransaction),
            snapshots = root.children("saldos").map(::toSnapshot),
        )
        validate(data)
        return data
    }

    private fun root(text: String): Map<String, Any?> {
        val parsed = try {
            Json.read(text)
        } catch (e: Json.JsonFormatException) {
            throw BackupException("El respaldo está dañado: ${e.message}", e)
        }
        val map = parsed as? Map<*, *> ?: throw BackupException("El respaldo no tiene el formato esperado")
        @Suppress("UNCHECKED_CAST")
        val root = map as Map<String, Any?>
        if (root.string("app") != Export.APP || root.string("documento") != DOCUMENT) {
            throw BackupException("El archivo no es un respaldo de Wenagastos")
        }
        if ((root.long("version") ?: 0) > VERSION) {
            throw BackupException("El respaldo es de una versión más nueva de la app")
        }
        return root
    }

    /** Los arreglos del respaldo; un campo que falta se lee como lista vacía. */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.children(key: String): List<Map<String, Any?>> {
        val list = this[key] as? List<*> ?: return emptyList()
        return list.map { item ->
            item as? Map<String, Any?> ?: throw BackupException("El respaldo tiene '$key' mal formado")
        }
    }

    private fun Map<String, Any?>.string(key: String): String? = when (val value = this[key]) {
        null -> null
        is String -> value
        else -> throw BackupException("El campo '$key' del respaldo no es texto")
    }

    private fun Map<String, Any?>.requiredString(key: String): String =
        string(key)?.takeIf { it.isNotBlank() } ?: throw BackupException("El respaldo no trae '$key'")

    private fun Map<String, Any?>.long(key: String): Long? = when (val value = this[key]) {
        null -> null
        is Long -> value
        is Int -> value.toLong()
        is Double -> if (value == Math.floor(value)) value.toLong() else null
        else -> throw BackupException("El campo '$key' del respaldo no es un número")
    }

    private fun Map<String, Any?>.requiredLong(key: String): Long =
        long(key) ?: throw BackupException("El respaldo no trae '$key'")

    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = when (val value = this[key]) {
        null -> default
        is Boolean -> value
        else -> throw BackupException("El campo '$key' del respaldo no es verdadero/falso")
    }

    private fun toAccount(map: Map<String, Any?>, legacyNumbers: Boolean): AccountEntity {
        val kind = AccountKind.entries.firstOrNull { it.name == map.string("kind") }
            ?: AccountKind.CHECKING
        val accountLast4: String?
        val cardLast4: String?
        if (legacyNumbers) {
            val numbers = parseNumbers(map.string("last4"))
            val cardOnly = kind == AccountKind.DEBIT || kind == AccountKind.CREDIT
            accountLast4 = if (cardOnly) null else numbers.firstOrNull()
            cardLast4 = (if (cardOnly) numbers else numbers.drop(1))
                .joinToString(",")
                .ifEmpty { null }
        } else {
            accountLast4 = map.string("accountLast4")
            cardLast4 = map.string("cardLast4")
        }
        return AccountEntity(
            id = map.requiredLong("id"),
            name = map.requiredString("name"),
            institution = Institution.entries.firstOrNull { it.name == map.string("institution") }
                ?: Institution.OTHER,
            kind = kind,
            accountLast4 = accountLast4,
            cardLast4 = cardLast4,
            colorHex = map.string("colorHex") ?: Institution.OTHER.colorHex,
            isActive = map.bool("isActive", true),
            createdAt = map.long("createdAt") ?: 0L,
            creditLimit = map.long("creditLimit"),
            statementDay = map.long("statementDay")?.toInt(),
            paymentDay = map.long("paymentDay")?.toInt(),
        )
    }

    private fun toCategory(map: Map<String, Any?>): CategoryEntity = CategoryEntity(
        id = map.requiredLong("id"),
        name = map.requiredString("name"),
        emoji = map.string("emoji").orEmpty(),
        colorHex = map.string("colorHex") ?: "#607D8B",
        kind = CategoryKind.entries.firstOrNull { it.name == map.string("kind") }
            ?: CategoryKind.EXPENSE,
        monthlyBudget = map.long("monthlyBudget"),
        sortOrder = map.long("sortOrder")?.toInt() ?: 0,
        isArchived = map.bool("isArchived", false),
        nature = CategoryNature.entries.firstOrNull { it.name == map.string("nature") }
            ?: CategoryNature.VARIABLE,
    )

    private fun toRule(map: Map<String, Any?>): MerchantRuleEntity = MerchantRuleEntity(
        id = map.requiredLong("id"),
        merchantKey = map.requiredString("merchantKey"),
        merchantLabel = map.string("merchantLabel").orEmpty(),
        direction = Direction.entries.firstOrNull { it.name == map.string("direction") }
            ?: Direction.DEBIT,
        categoryId = map.long("categoryId"),
        uses = map.long("uses")?.toInt() ?: 0,
        createdAt = map.long("createdAt") ?: 0L,
        updatedAt = map.long("updatedAt") ?: 0L,
    )

    private fun toTransaction(map: Map<String, Any?>): TransactionEntity {
        val amount = map.requiredLong("amount")
        if (amount <= 0) throw BackupException("El respaldo trae un movimiento con monto inválido")
        val occurredAt = map.requiredLong("occurredAt")
        if (occurredAt <= 0) throw BackupException("El respaldo trae un movimiento sin fecha")
        return TransactionEntity(
            id = map.requiredLong("id"),
            accountId = map.requiredLong("accountId"),
            occurredAt = occurredAt,
            amount = amount,
            direction = Direction.entries.firstOrNull { it.name == map.string("direction") }
                ?: Direction.DEBIT,
            merchantRaw = map.string("merchantRaw").orEmpty(),
            merchantNorm = map.string("merchantNorm").orEmpty(),
            categoryId = map.long("categoryId"),
            status = TransactionStatus.entries.firstOrNull { it.name == map.string("status") }
                ?: TransactionStatus.CONFIRMED,
            isExpense = map.bool("isExpense", true),
            isReceivable = map.bool("isReceivable", false),
            isTransfer = map.bool("isTransfer", false),
            transferAccountId = map.long("transferAccountId"),
            transferGroupId = map.long("transferGroupId"),
            // Un respaldo v5 no trae cuotas: el movimiento se lee como una compra pagada de una vez.
            installmentNumber = map.long("installmentNumber")?.toInt(),
            installmentTotal = map.long("installmentTotal")?.toInt(),
            originAmount = map.long("originAmount"),
            notes = map.string("notes"),
            source = TransactionSource.entries.firstOrNull { it.name == map.string("source") }
                ?: TransactionSource.MANUAL,
            rawText = map.string("rawText"),
            dedupeHash = map.string("dedupeHash"),
            createdAt = map.long("createdAt") ?: 0L,
            updatedAt = map.long("updatedAt") ?: 0L,
        )
    }

    /**
     * Un corte de saldo. El monto puede ser negativo (deuda de la tarjeta o sobregiro) y cero; lo
     * que sí no puede faltar es la fecha, porque el saldo cuelga de ella.
     */
    private fun toSnapshot(map: Map<String, Any?>): AccountSnapshotEntity {
        val asOf = map.requiredLong("asOf")
        if (asOf <= 0) throw BackupException("El respaldo trae un saldo sin fecha")
        return AccountSnapshotEntity(
            id = map.requiredLong("id"),
            accountId = map.requiredLong("accountId"),
            asOf = asOf,
            balance = map.requiredLong("balance"),
            source = SnapshotSource.entries.firstOrNull { it.name == map.string("source") }
                ?: SnapshotSource.MANUAL,
            note = map.string("note"),
            createdAt = map.long("createdAt") ?: 0L,
        )
    }

    /**
     * Un respaldo a medio escribir es peor que ninguno: antes de borrar nada se comprueba que
     * cada movimiento apunte a una cuenta que viene en el archivo y que no haya ids repetidos
     * (la inserción fallaría a mitad de camino y la transacción se revertiría igual, pero así el
     * mensaje dice qué pasa).
     */
    private fun validate(data: BackupData) {
        val accountIds = data.accounts.mapTo(HashSet()) { it.id }
        val categoryIds = data.categories.mapTo(HashSet()) { it.id }

        if (data.accounts.any { it.id <= 0 }) throw BackupException("El respaldo trae una cuenta sin id")
        if (data.categories.any { it.id <= 0 }) throw BackupException("El respaldo trae una categoría sin id")
        if (accountIds.size != data.accounts.size) throw BackupException("El respaldo repite cuentas")
        if (categoryIds.size != data.categories.size) throw BackupException("El respaldo repite categorías")
        if (data.rules.map { it.id }.toSet().size != data.rules.size) {
            throw BackupException("El respaldo repite reglas")
        }
        if (data.transactions.map { it.id }.toSet().size != data.transactions.size) {
            throw BackupException("El respaldo repite movimientos")
        }
        if (data.transactions.any { it.accountId !in accountIds }) {
            throw BackupException("El respaldo trae movimientos de una cuenta que no incluye")
        }
        if (data.transactions.any { tx -> tx.categoryId?.let { it !in categoryIds } == true }) {
            throw BackupException("El respaldo trae movimientos con una categoría que no incluye")
        }
        if (data.transactions.any { tx -> tx.transferAccountId?.let { it !in accountIds } == true }) {
            throw BackupException("El respaldo trae un traspaso hacia una cuenta que no incluye")
        }
        if (data.rules.any { rule -> rule.categoryId?.let { it !in categoryIds } == true }) {
            throw BackupException("El respaldo trae reglas con una categoría que no incluye")
        }
        if (data.snapshots.any { it.id <= 0 }) {
            throw BackupException("El respaldo trae un saldo sin id")
        }
        if (data.snapshots.map { it.id }.toSet().size != data.snapshots.size) {
            throw BackupException("El respaldo repite saldos")
        }
        // Un corte sin su cuenta no se puede restaurar: la llave foránea lo rechazaría a mitad de
        // camino y el mensaje no diría cuál es el problema.
        if (data.snapshots.any { it.accountId !in accountIds }) {
            throw BackupException("El respaldo trae saldos de una cuenta que no incluye")
        }
    }
}
