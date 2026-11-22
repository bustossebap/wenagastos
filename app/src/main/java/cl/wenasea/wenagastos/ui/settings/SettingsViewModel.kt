package cl.wenasea.wenagastos.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.BackupStamp
import cl.wenasea.wenagastos.data.local.StatementFormats
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.repo.AccountMergeRepository
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.BackupRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.MergePlan
import cl.wenasea.wenagastos.data.repo.StatementImportRepository
import cl.wenasea.wenagastos.domain.Credit
import cl.wenasea.wenagastos.domain.numbers
import cl.wenasea.wenagastos.domain.parseNumbers
import cl.wenasea.wenagastos.domain.splitNumbers
import cl.wenasea.wenagastos.importexport.Backup
import cl.wenasea.wenagastos.importexport.BackupCrypto
import cl.wenasea.wenagastos.importexport.BackupException
import cl.wenasea.wenagastos.importexport.Csv
import cl.wenasea.wenagastos.importexport.DocumentFiles
import cl.wenasea.wenagastos.importexport.Export
import cl.wenasea.wenagastos.importexport.Json
import cl.wenasea.wenagastos.importexport.StatementException
import cl.wenasea.wenagastos.importexport.StatementImport
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val capturedCount: Int = 0,
    /** Movimientos por cuenta: una cuenta con movimientos se archiva en vez de borrarse. */
    val movementCounts: Map<Long, Int> = emptyMap(),
    val error: String? = null,
    /** Se pone en true cuando una acción terminó bien; la pantalla cierra el diálogo. */
    val saved: Boolean = false,
    /** Último aviso de la tarjeta "Datos": export listo, respaldo restaurado o el problema. */
    val notice: String? = null,
    val noticeIsError: Boolean = false,
    /** Hay un archivo en camino: los botones de la tarjeta "Datos" se desactivan. */
    val busy: Boolean = false,
    /** Fusión pendiente de confirmar: la pantalla muestra qué va a pasar antes de tocar nada. */
    val mergePlan: MergePlan? = null,
) {
    fun movementCount(account: AccountEntity): Int = movementCounts[account.id] ?: 0
}

class SettingsViewModel(
    private val accounts: AccountRepository,
    private val merger: AccountMergeRepository,
    categories: CategoryRepository,
    captureLogs: CaptureLogRepository,
    private val backup: BackupRepository,
    private val statements: StatementImportRepository,
    private val formats: StatementFormats,
    private val files: DocumentFiles,
    private val stamp: BackupStamp,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(
        accounts.observeAll(),
        categories.observeAll(),
        captureLogs.observeCount(),
        accounts.observeMovementCounts(),
        local,
    ) { accountList, categoryList, captured, counts, mine ->
        mine.copy(
            accounts = accountList,
            categories = categoryList,
            capturedCount = captured,
            movementCounts = counts.associate { it.accountId to it.total },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /**
     * Crea la cuenta ([editing] `null`) o la actualiza. El color sale de la institución, así el
     * alta no pide un color aparte.
     *
     * [accountNumber] es el número de la cuenta (el que aparece en las transferencias) y [cards]
     * las tarjetas que gastan de ella; en una tarjeta de crédito o de débito el número es de la
     * tarjeta, no de una cuenta que la respalde. Los números se validan contra las otras cuentas
     * activas: dos cuentas no pueden reclamar el mismo número, porque el cruce de los avisos es
     * por número exacto y ganaría siempre la de menor id.
     */
    fun saveAccount(
        editing: AccountEntity?,
        name: String,
        institution: Institution,
        kind: AccountKind,
        accountNumber: String?,
        cards: List<String>,
        credit: CreditInput = CreditInput(),
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            fail("Ponle un nombre a la cuenta")
            return
        }
        // El cupo y los días de corte/pago son de la tarjeta de crédito. `Credit.parse` los ignora
        // en otra cuenta —una cuenta corriente no tiene facturación— y valida cupo y días.
        val creditParse = Credit.parse(kind, credit.limit, credit.statementDay, credit.paymentDay)
        creditParse.error?.let { problem ->
            fail(problem)
            return
        }
        val creditFields = creditParse.fields
        val (accountValue, cardValue) = splitNumbers(kind, accountNumber, cards)
        val wanted = (listOfNotNull(accountValue) + parseNumbers(cardValue)).distinct()
        viewModelScope.launch {
            val other = accounts.listActive().firstOrNull { account ->
                account.id != editing?.id && account.numbers().any { it in wanted }
            }
            val repeated = wanted.firstOrNull { number -> other?.numbers()?.contains(number) == true }
            if (other != null && repeated != null) {
                fail("El número $repeated ya está en ${other.name}")
                return@launch
            }
            val base = editing ?: AccountEntity(
                name = cleanName,
                institution = institution,
                kind = kind,
                colorHex = institution.colorHex,
            )
            val entity = base.copy(
                name = cleanName,
                institution = institution,
                kind = kind,
                accountLast4 = accountValue,
                cardLast4 = cardValue,
                colorHex = institution.colorHex,
                creditLimit = creditFields.limit,
                statementDay = creditFields.statementDay,
                paymentDay = creditFields.paymentDay,
            )
            if (editing == null) accounts.add(entity) else accounts.update(entity)
            local.update { it.copy(error = null, saved = true) }
        }
    }

    /** Archiva (deja de usarse en la captura y desaparece del editor) o la reactiva. */
    fun setActive(account: AccountEntity, active: Boolean) {
        viewModelScope.launch {
            accounts.update(account.copy(isActive = active))
            local.update { it.copy(error = null, saved = true) }
        }
    }

    /**
     * Borra la cuenta solo si no tiene movimientos. Si tiene, la archiva: la FK de `transactions`
     * es `CASCADE` y borrarla se llevaría el historial completo.
     */
    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch {
            val movements = accounts.countMovements(account.id)
            if (movements > 0) {
                accounts.update(account.copy(isActive = false))
            } else {
                accounts.delete(account.id)
            }
            local.update { it.copy(error = null, saved = true) }
        }
    }

    fun clearError() {
        local.update { it.copy(error = null) }
    }

    /**
     * Prepara la fusión de dos cuentas: la pantalla muestra el resumen y pide confirmación antes
     * de mover nada. Si no se pueden unir, el mensaje sale en el mismo diálogo.
     */
    fun previewMerge(keep: AccountEntity, merging: AccountEntity) {
        viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.IO) { merger.plan(keep.id, merging.id) }
                local.update { it.copy(mergePlan = plan, error = null) }
            } catch (e: IllegalArgumentException) {
                local.update { it.copy(error = e.message ?: "No se pueden unir esas cuentas") }
            }
        }
    }

    fun confirmMerge() {
        val plan = local.value.mergePlan ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { merger.merge(plan) }
                local.update { it.copy(error = null, mergePlan = null, saved = true) }
            } catch (e: IllegalArgumentException) {
                local.update { it.copy(mergePlan = null, error = e.message ?: "No se pudieron unir") }
            }
        }
    }

    fun cancelMerge() {
        local.update { it.copy(mergePlan = null) }
    }

    /** Exporta los movimientos a CSV (planilla) o JSON (programas). */
    fun exportCsv(uri: Uri) = runTask {
        val data = backup.snapshot()
        val text = Export.transactionsCsv(
            data.transactions,
            data.accounts.associateBy { it.id },
            data.categories.associateBy { it.id },
        )
        files.writeText(uri, text)
        "CSV listo con ${plural(data.transactions.size, "movimiento", "movimientos")}."
    }

    fun exportJson(uri: Uri) = runTask {
        val data = backup.snapshot()
        val text = Export.transactionsJson(
            data.transactions,
            data.accounts.associateBy { it.id },
            data.categories.associateBy { it.id },
        )
        files.writeText(uri, text)
        "JSON listo con ${plural(data.transactions.size, "movimiento", "movimientos")}."
    }

    /**
     * Respaldo cifrado con contraseña. La clave no se guarda en ninguna parte: si se olvida, el
     * archivo no se puede abrir. Por eso el mínimo de caracteres, que no sea de puros números y
     * el aviso en pantalla. Al restaurar no se exige nada: un respaldo viejo con contraseña débil
     * tiene que poder abrirse igual.
     */
    fun createBackup(uri: Uri, password: String) {
        if (BackupCrypto.isWeakPassword(password)) {
            local.update {
                it.copy(
                    notice = "La contraseña necesita al menos ${BackupCrypto.MIN_PASSWORD} " +
                        "caracteres y no puede ser solo números",
                    noticeIsError = true,
                )
            }
            return
        }
        runTask {
            val data = backup.snapshot()
            val plain = Backup.encode(data, BackupRepository.SCHEMA).toByteArray(Charsets.UTF_8)
            val chars = password.toCharArray()
            try {
                files.write(uri, BackupCrypto.encrypt(plain, chars))
            } finally {
                // La contraseña no se queda en memoria más de lo necesario.
                chars.fill('\u0000')
            }
            // El archivo quedó escrito: recién acá corre el plazo del recordatorio del inicio.
            stamp.mark()
            "Respaldo listo con ${plural(data.transactions.size, "movimiento", "movimientos")}. " +
                "Guárdalo fuera del teléfono: sin la contraseña no hay forma de abrirlo."
        }
    }

    /** Reemplaza todo con el contenido del respaldo. La pantalla pide confirmación antes. */
    fun restoreBackup(uri: Uri, password: String) = runTask {
        val envelope = files.read(uri)
        if (!BackupCrypto.isBackup(envelope)) {
            throw BackupException("El archivo no es un respaldo de Wenagastos")
        }
        val chars = password.toCharArray()
        val text = try {
            String(BackupCrypto.decrypt(envelope, chars), Charsets.UTF_8)
        } finally {
            chars.fill('\u0000')
        }
        val data = Backup.decode(text)
        backup.restore(data)
        "Restaurado: ${plural(data.transactions.size, "movimiento", "movimientos")} y " +
            "${plural(data.accounts.size, "cuenta", "cuentas")}."
    }

    fun clearNotice() {
        local.update { it.copy(notice = null, noticeIsError = false) }
    }

    /**
     * Importa la cartola de una cuenta: la lee con la plantilla que se recuerda de ese banco (o la
     * deduce del archivo la primera vez), anota lo que falta y deja el saldo del cierre como corte.
     * Lo que ya había llegado por aviso no se duplica: se cruza por día, monto y sentido.
     */
    fun importStatement(account: AccountEntity, uri: Uri) = runTask {
        val text = files.readText(uri)
        val sheet = StatementImport.read(Csv.split(text), formats.templateFor(account.institution))
            ?: throw StatementException(
                "No reconocí la cartola: no encontré la fila de títulos con la fecha y el monto. " +
                    "Exporta la cartola del mes como CSV desde el sitio del banco.",
            )
        // Acertó la lectura: la próxima cartola de este banco se lee con la misma plantilla.
        formats.remember(account.institution, sheet.template)
        val report = statements.import(account.id, sheet, files.name(uri))
        report.describe()
    }

    /**
     * Todo el trabajo de archivo corre en IO y termina en un aviso, nunca en una excepción que
     * tumbe la pantalla: si el selector devolvió una URI que ya no existe, se dice y se sigue.
     */
    private fun runTask(block: suspend () -> String) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, notice = null, noticeIsError = false) }
            val (message, isError) = try {
                withContext(Dispatchers.IO) { block() } to false
            } catch (e: BackupException) {
                (e.message ?: "No se pudo leer el respaldo") to true
            } catch (e: StatementException) {
                (e.message ?: "No se pudo leer la cartola") to true
            } catch (e: Json.JsonFormatException) {
                (e.message ?: "El JSON está dañado") to true
            } catch (e: IOException) {
                ("No se pudo leer o escribir el archivo: ${e.message}") to true
            } catch (e: SecurityException) {
                ("Android no dio permiso sobre ese archivo") to true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ("No se pudo completar: ${e.message ?: e::class.java.simpleName}") to true
            }
            local.update { it.copy(busy = false, notice = message, noticeIsError = isError) }
        }
    }

    private fun plural(count: Int, singular: String, plural: String): String =
        if (count == 1) "1 $singular" else "$count $plural"

    /** La pantalla ya cerró el diálogo por haber guardado. */
    fun consumeSaved() {
        local.update { it.copy(saved = false) }
    }

    private fun fail(message: String) {
        local.update { it.copy(error = message, saved = false) }
    }
}


