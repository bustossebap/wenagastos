package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureOutcome
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.TransactionEntity
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.MerchantRuleRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import cl.wenasea.wenagastos.domain.TextNorm
import cl.wenasea.wenagastos.domain.opposite
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Convierte notificaciones en movimientos.
 *
 * Política de confianza:
 * - Si hay monto, comercio, sentido y cuenta identificados → movimiento CONFIRMADO (suma en los totales).
 * - Si algo falta → queda PENDING ("Por revisar") con el motivo anotado, así no se pierde la plata
 *   pero tampoco ensucia los totales.
 * - Si ya existe el mismo gasto (±5 min) → se descarta, salvo que llegue primero Google Wallet
 *   y después el banco: en ese caso se completa el movimiento existente con los datos del banco.
 * - Un traspaso entre cuentas propias se registra con `isTransfer`: no suma en gastos ni ingresos,
 *   porque la plata no se gastó, solo cambió de cuenta.
 * - La categoría sale de la regla que el usuario enseñó para ese comercio (Fase 3) y, si no hay,
 *   de la semilla de `MerchantCategories`.
 *
 * [handle] está serializado con un [Mutex]: dos avisos que llegan juntos (el banco parte el
 * traspaso en "desde" y "hacia", o Wallet y el banco avisan lo mismo) se evalúan uno por uno,
 * así ninguno pasa la revisión de duplicados antes de que el otro se haya guardado.
 */
class CaptureEngine(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val rules: MerchantRuleRepository,
    private val logs: CaptureLogRepository,
) {

    private data class AccountChoice(
        val account: AccountEntity,
        val isPlaceholder: Boolean,
        val reason: String? = null,
    )

    private val gate = Mutex()

    suspend fun handle(raw: RawNotification): CaptureOutcome = gate.withLock { process(raw) }

    private suspend fun process(raw: RawNotification): CaptureOutcome {
        // Un correo llega por Gmail y el banco va en el título; las apps se reconocen por el paquete.
        // Si el remitente no es un banco, se descarta aquí y no se guarda nada.
        val isMail = raw.packageName == MailSource.PACKAGE
        val source = if (isMail) MailSource.of(raw) else BankSource.of(raw.packageName)
        if (source == null) return CaptureOutcome.IGNORED

        // Los resúmenes de grupo llegan vacíos: no hay nada que analizar ni que dejar en el registro.
        val hasContent = listOf(raw.title, raw.text, raw.bigText, raw.textLines)
            .any { !it.isNullOrBlank() }
        if (!hasContent) return CaptureOutcome.IGNORED

        val result = if (isMail) MailParser.parse(raw, source) else NotifParser.parse(raw, source)
        val parsed = when (result) {
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
                    isTransfer = parsed.isTransfer,
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

        val savedId = transactions.save(
            TransactionEntity(
                accountId = choice.account.id,
                occurredAt = parsed.occurredAt,
                amount = parsed.amount,
                direction = parsed.direction,
                merchantRaw = parsed.merchant,
                merchantNorm = parsed.merchant.lowercase(),
                categoryId = categoryId,
                status = status,
                isExpense = parsed.direction == Direction.DEBIT && !parsed.isTransfer,
                isTransfer = parsed.isTransfer,
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
        if (parsed.isTransfer) linkTransferLegs(savedId)

        val outcome = if (status == TransactionStatus.CONFIRMED) {
            CaptureOutcome.SAVED
        } else {
            CaptureOutcome.PENDING
        }
        log(raw, source, outcome, describe(parsed, choice))
        return outcome
    }

    /**
     * Enlaza las dos patas de un traspaso cuando el banco avisa las dos ("… desde tu cuenta" y
     * "… hacia tu cuenta"): cada una queda apuntando a la cuenta de la otra, así la lista puede
     * mostrar "Santander Cta. Cte. → Falabella Cta. Cte.". Solo se enlazan traspasos: un gasto
     * normal del mismo monto en otra cuenta no se toca.
     */
    private suspend fun linkTransferLegs(legId: Long) {
        val leg = transactions.get(legId) ?: return
        val counterpart = transactions.findOppositeLeg(
            amount = leg.amount,
            direction = leg.direction.opposite(),
            accountId = leg.accountId,
            excludeId = leg.id,
            at = leg.occurredAt,
        ) ?: return
        if (!counterpart.isTransfer) return
        transactions.linkTransfer(leg, counterpart)
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
            // Con una sola cuenta en ese banco no hay dónde equivocarse, así que pedir que se
            // asigne el número sería fricción sin ganancia. Con dos o más, el número desambigua.
            val ambiguous = last4 != null && candidates.size > 1
            return AccountChoice(
                account = chosen,
                isPlaceholder = ambiguous,
                reason = if (ambiguous) "Asigna el número *$last4 a una cuenta" else null,
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
        val active = categories.listActive()
        // Un traspaso no es gasto ni ingreso: va a la categoría de tipo TRANSFER ("Entre cuentas").
        if (parsed.isTransfer) {
            return active.firstOrNull { it.kind == CategoryKind.TRANSFER }?.id
        }
        // Lo que el usuario enseñó para ese comercio manda sobre la semilla del catálogo.
        if (parsed.merchantFound) {
            rules.resolve(parsed.merchant, parsed.direction)?.let { return it }
        }
        val name = MerchantCategories.categoryFor(parsed.merchant, parsed.direction, parsed.rawText)
            ?: return null
        val wanted = TextNorm.plain(name)
        return active.firstOrNull { TextNorm.plain(it.name) == wanted }?.id
    }

    private fun buildNotes(parsed: ParsedNotification, choice: AccountChoice): String? {
        val reasons = buildList {
            // Un traspaso no tiene comercio: avisar que falta sería ruido.
            if (!parsed.merchantFound && !parsed.isTransfer) add("No pude leer el comercio")
            choice.reason?.let(::add)
        }
        if (reasons.isEmpty()) return null
        return reasons.joinToString(". ") + ". Revisa y confirma."
    }

    private fun describe(parsed: ParsedNotification, choice: AccountChoice): String {
        val amount = "$" + parsed.amount.toString().reversed().chunked(3).joinToString(".").reversed()
        if (parsed.isTransfer) {
            val preposition = if (parsed.direction == Direction.DEBIT) "desde" else "hacia"
            return "traspaso de $amount $preposition ${choice.account.name}"
        }
        val isDebit = parsed.direction == Direction.DEBIT
        val kind = if (isDebit) "gasto" else "ingreso"
        val preposition = if (isDebit) "en" else "de"
        return "$kind de $amount $preposition ${parsed.merchant} → ${choice.account.name}"
    }

    private fun dedupeHash(packageName: String, parsed: ParsedNotification): String {
        val bucket = parsed.occurredAt / FIVE_MINUTES
        // El sentido y la cuenta entran al hash: un traspaso del mismo monto en el mismo minuto
        // sale de una cuenta y entra a otra, y son dos movimientos, no un duplicado.
        // La fuente es la institución, no la app: el mismo movimiento puede llegar por push
        // (`cl.android`) y por correo (Gmail), y debe caer en el mismo hash.
        val origin = parsed.source.name.ifBlank { packageName }
        val raw = "$origin|${parsed.amount}|${parsed.direction}|${parsed.last4.orEmpty()}|" +
            "${TextNorm.key(parsed.merchant)}|$bucket"
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
