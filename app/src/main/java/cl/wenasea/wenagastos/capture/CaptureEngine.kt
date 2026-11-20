package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureOutcome
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import java.security.MessageDigest

/**
 * Convierte notificaciones en movimientos.
 *
 * Política de confianza:
 * - Si hay monto, comercio, sentido y cuenta identificados → movimiento CONFIRMADO (suma en los totales).
 * - Si algo falta → queda PENDING ("Por revisar") con el motivo anotado, así no se pierde la plata
 *   pero tampoco ensucia los totales.
 * - Si ya existe el mismo gasto (±5 min) → se descarta, salvo que llegue primero Google Wallet
 *   y después el banco: en ese caso se completa el movimiento existente con los datos del banco.
 */
class CaptureEngine(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val logs: CaptureLogRepository,
) {

    private data class AccountChoice(
        val account: AccountEntity,
        val isPlaceholder: Boolean,
        val reason: String? = null,
    )

    suspend fun handle(raw: RawNotification): CaptureOutcome {
        val source = BankSource.of(raw.packageName) ?: return CaptureOutcome.IGNORED

        // Los resúmenes de grupo llegan vacíos: no hay nada que analizar ni que dejar en el registro.
        val hasContent = listOf(raw.title, raw.text, raw.bigText, raw.textLines)
            .any { !it.isNullOrBlank() }
        if (!hasContent) return CaptureOutcome.IGNORED

        val parsed = when (val result = NotifParser.parse(raw, source)) {
            is ParseResult.Rejected -> {
                log(raw, source, CaptureOutcome.REJECTED, result.reason)
                return CaptureOutcome.REJECTED
            }

            is ParseResult.Parsed -> result.notification
        }

        val choice = resolveAccount(parsed)
        if (choice == null) {
            log(raw, source, CaptureOutcome.REJECTED, "No hay cuentas activas para asignar el movimiento")
            return CaptureOutcome.REJECTED
        }

        val categoryId = resolveCategoryId(parsed)
        val hash = dedupeHash(raw.packageName, parsed)

        if (transactions.existsByHash(hash)) {
            log(raw, source, CaptureOutcome.DUPLICATE, "Ya estaba registrado")
            return CaptureOutcome.DUPLICATE
        }

        val existing = transactions.findNearby(parsed.amount, parsed.direction, parsed.occurredAt)
        val status = if (parsed.isConfident && !choice.isPlaceholder) {
            TransactionStatus.CONFIRMED
        } else {
            TransactionStatus.PENDING
        }
        val notes = buildNotes(parsed, choice)

        if (existing != null) {
            if (!isUpgradeable(existing, source)) {
                log(
                    raw,
                    source,
                    CaptureOutcome.DUPLICATE,
                    "Mismo monto que un movimiento ya registrado",
                )
                return CaptureOutcome.DUPLICATE
            }
            transactions.save(
                existing.copy(
                    accountId = choice.account.id,
                    categoryId = existing.categoryId ?: categoryId,
                    merchantRaw = parsed.merchant,
                    merchantNorm = parsed.merchant.lowercase(),
                    notes = notes,
                    status = status,
                    source = TransactionSource.NOTIFICATION,
                    rawText = parsed.rawText,
                    dedupeHash = hash,
                ),
            )
            log(
                raw,
                source,
                CaptureOutcome.MERGED,
                "Completé el movimiento que había quedado de Google Wallet",
            )
            return CaptureOutcome.MERGED
        }

        transactions.save(
            TransactionEntity(
                accountId = choice.account.id,
                occurredAt = parsed.occurredAt,
                amount = parsed.amount,
                direction = parsed.direction,
                merchantRaw = parsed.merchant,
                merchantNorm = parsed.merchant.lowercase(),
                categoryId = categoryId,
                status = status,
                isExpense = parsed.direction == Direction.DEBIT,
                notes = notes,
                source = if (source == BankSource.WALLET) {
                    TransactionSource.WALLET
                } else {
                    TransactionSource.NOTIFICATION
                },
                rawText = parsed.rawText,
                dedupeHash = hash,
            ),
        )

        val outcome = if (status == TransactionStatus.CONFIRMED) {
            CaptureOutcome.SAVED
        } else {
            CaptureOutcome.PENDING
        }
        log(raw, source, outcome, describe(parsed, choice))
        return outcome
    }

    /** Google Wallet (prioridad 1) se puede completar con el aviso del banco (prioridad 3). */
    private fun isUpgradeable(existing: TransactionEntity, source: BankSource): Boolean =
        existing.source == TransactionSource.WALLET && source.priority > BankSource.WALLET.priority

    private suspend fun resolveAccount(parsed: ParsedNotification): AccountChoice? {
        val active = accounts.listActive()
        if (active.isEmpty()) return null

        val last4 = parsed.last4
        if (last4 != null) {
            accounts.findByLast4(last4)?.let { return AccountChoice(it, isPlaceholder = false) }
        }

        val institution = parsed.source.institution
        val candidates = if (institution == null) {
            emptyList()
        } else {
            active.filter { it.institution == institution }
        }

        if (candidates.isNotEmpty()) {
            val preferredKind = if (parsed.cardIsCredit) AccountKind.CREDIT else null
            val chosen = candidates.firstOrNull { it.kind == preferredKind } ?: candidates.first()
            return AccountChoice(
                account = chosen,
                isPlaceholder = last4 != null,
                reason = last4?.let { "Asigna el número *$it a una cuenta" },
            )
        }

        return AccountChoice(
            account = active.first(),
            isPlaceholder = true,
            reason = if (parsed.direction == Direction.DEBIT) {
                "No supe con qué cuenta se pagó"
            } else {
                "No supe en qué cuenta entró la plata"
            },
        )
    }

    private suspend fun resolveCategoryId(parsed: ParsedNotification): Long? {
        val name = MerchantCategories.categoryFor(parsed.merchant, parsed.direction, parsed.rawText)
            ?: return null
        val wanted = TextNorm.plain(name)
        return categories.listActive()
            .firstOrNull { TextNorm.plain(it.name) == wanted }
            ?.id
    }

    private fun buildNotes(parsed: ParsedNotification, choice: AccountChoice): String? {
        val reasons = buildList {
            if (!parsed.merchantFound) add("No pude leer el comercio")
            choice.reason?.let(::add)
        }
        if (reasons.isEmpty()) return null
        return reasons.joinToString(". ") + ". Revisa y confirma."
    }

    private fun describe(parsed: ParsedNotification, choice: AccountChoice): String {
        val amount = "$" + parsed.amount.toString().reversed().chunked(3).joinToString(".").reversed()
        val isDebit = parsed.direction == Direction.DEBIT
        val kind = if (isDebit) "gasto" else "ingreso"
        val preposition = if (isDebit) "en" else "de"
        return "$kind de $amount $preposition ${parsed.merchant} → ${choice.account.name}"
    }

    private fun dedupeHash(packageName: String, parsed: ParsedNotification): String {
        val bucket = parsed.occurredAt / FIVE_MINUTES
        val raw = "$packageName|${parsed.amount}|${TextNorm.key(parsed.merchant)}|$bucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun log(
        raw: RawNotification,
        source: BankSource,
        outcome: CaptureOutcome,
        detail: String?,
    ) {
        logs.add(
            CaptureLogEntity(
                packageName = raw.packageName,
                sourceLabel = source.label,
                title = raw.title,
                text = listOf(raw.text, raw.bigText, raw.textLines)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · "),
                postedAt = raw.postedAt,
                outcome = outcome,
                detail = detail,
            ),
        )
    }

    private companion object {
        const val FIVE_MINUTES = 5 * 60_000L
    }
}
