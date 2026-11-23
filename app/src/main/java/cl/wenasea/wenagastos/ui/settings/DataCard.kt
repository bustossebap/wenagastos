package cl.wenasea.wenagastos.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.domain.Dates
import cl.wenasea.wenagastos.importexport.BackupCrypto
import cl.wenasea.wenagastos.importexport.DocumentFiles
import cl.wenasea.wenagastos.ui.common.BankLogo
import cl.wenasea.wenagastos.ui.common.SectionHeader

/**
 * La tarjeta "Datos": exportar a planilla, respaldar cifrado y restaurar.
 *
 * Todo pasa por el selector de archivos de Android (SAF): la app nunca pide permiso de
 * almacenamiento ni sabe en qué carpeta quedó el archivo. La contraseña del respaldo se pide acá
 * y viaja al ViewModel, que la borra de memoria apenas termina de cifrar.
 */
@Composable
internal fun DataCard(
    state: SettingsUiState,
    onExportCsv: (Uri) -> Unit,
    onExportJson: (Uri) -> Unit,
    onCreateBackup: (Uri, String) -> Unit,
    onRestoreBackup: (Uri, String) -> Unit,
    onImportStatement: (AccountEntity, Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var askBackupPassword by remember { mutableStateOf(false) }
    var askRestorePassword by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<Uri?>(null) }

    // La contraseña se escribe antes de abrir el selector: el archivo se crea al confirmar.
    var pendingPassword by remember { mutableStateOf<String?>(null) }

    // La cartola es de una cuenta, y hay que saberlo antes de abrir el archivo.
    var askStatementAccount by remember { mutableStateOf(false) }
    var statementTarget by remember { mutableStateOf<AccountEntity?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DocumentFiles.CSV_MIME),
    ) { uri -> if (uri != null) onExportCsv(uri) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DocumentFiles.JSON_MIME),
    ) { uri -> if (uri != null) onExportJson(uri) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DocumentFiles.BACKUP_MIME),
    ) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) onCreateBackup(uri, password)
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            restoreTarget = uri
            askRestorePassword = true
        }
    }

    // Los bancos no coinciden en el tipo que declaran: el selector abre con varios mime de texto.
    val statementLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val account = statementTarget
        statementTarget = null
        if (uri != null && account != null) onImportStatement(account, uri)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "Datos")
            Text(
                text = "Los archivos salen por el selector de Android: elegís la carpeta y la app " +
                    "no pide permiso de almacenamiento. El respaldo va con contraseña; sin ella " +
                    "no se puede abrir, ni siquiera en otro teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(
                    onClick = { csvLauncher.launch(fileName("csv")) },
                    enabled = !state.busy,
                ) {
                    Text("Exportar CSV")
                }
                TextButton(
                    onClick = { jsonLauncher.launch(fileName("json")) },
                    enabled = !state.busy,
                ) {
                    Text("Exportar JSON")
                }
            }
            Row {
                TextButton(
                    onClick = { askBackupPassword = true },
                    enabled = !state.busy,
                ) {
                    Text("Crear respaldo")
                }
                TextButton(
                    onClick = { openLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.busy,
                ) {
                    Text("Restaurar")
                }
            }
            SectionHeader(
                title = "Cartola",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "La cartola del mes trae los gastos que el aviso del banco no alcanzó a " +
                    "contar; sirve la de CSV, la de PDF y la planilla de Excel del banco. Se cruza " +
                    "por día, monto y sentido: lo que ya está anotado no se duplica, y el saldo del " +
                    "cierre queda como corte de la cuenta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row {
                TextButton(
                    onClick = { askStatementAccount = true },
                    enabled = !state.busy && state.accounts.any { it.isActive },
                ) {
                    Text("Importar cartola")
                }
            }
            if (state.busy) {
                Text(
                    text = "Trabajando…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            state.notice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.noticeIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (askBackupPassword) {
        PasswordDialog(
            title = "Contraseña del respaldo",
            message = "El archivo se cifra con esta contraseña. No queda guardada en ninguna " +
                "parte: si la pierdes, el respaldo no se puede abrir.",
            confirmLabel = "Elegir dónde guardarlo",
            askConfirmation = true,
            onDismiss = { askBackupPassword = false },
            onConfirm = { password ->
                askBackupPassword = false
                pendingPassword = password
                backupLauncher.launch(fileName("wena"))
            },
        )
    }

    val target = restoreTarget
    if (askRestorePassword && target != null) {
        PasswordDialog(
            title = "Restaurar respaldo",
            message = "Se reemplazan todos los movimientos, cuentas, categorías y reglas de este " +
                "teléfono por los del archivo. Lo que hay ahora se pierde: la app no guarda una " +
                "copia previa.",
            confirmLabel = "Restaurar",
            destructive = true,
            onDismiss = {
                askRestorePassword = false
                restoreTarget = null
            },
            onConfirm = { password ->
                askRestorePassword = false
                restoreTarget = null
                onRestoreBackup(target, password)
            },
        )
    }

    if (askStatementAccount) {
        AlertDialog(
            onDismissRequest = { askStatementAccount = false },
            title = { Text("¿De qué cuenta es la cartola?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Elige la cuenta cuyos movimientos trae el archivo. Lo que ya llegó " +
                            "por aviso del banco no se anota dos veces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.accounts.filter { it.isActive }.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    askStatementAccount = false
                                    statementTarget = account
                                    statementLauncher.launch(DocumentFiles.TEXT_MIMES)
                                }
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BankLogo(
                                institution = account.institution,
                                colorHex = account.colorHex,
                                size = 28,
                            )
                            Column {
                                Text(text = account.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "${account.institution.label} · ${account.kind.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { askStatementAccount = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun fileName(extension: String): String = "wenagastos-${Dates.today()}.$extension"

/**
 * Contraseña del respaldo. Al crearlo se pide dos veces (una errata en la contraseña deja el
 * archivo inservible para siempre); al restaurar, una sola.
 */
@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    askConfirmation: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val tooWeak = askConfirmation && BackupCrypto.isWeakPassword(password)
    val mismatch = askConfirmation && repeated != password

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                if (askConfirmation) {
                    OutlinedTextField(
                        value = repeated,
                        onValueChange = { repeated = it },
                        label = { Text("Repetir contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                if (password.isNotEmpty() && tooWeak) {
                    Text(
                        text = "Mínimo ${BackupCrypto.MIN_PASSWORD} caracteres, y no todos " +
                            "números: una frase de varias palabras es fácil de recordar y no " +
                            "está en ninguna lista.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (mismatch) {
                    Text(
                        text = "Las contraseñas no coinciden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = !tooWeak && !mismatch,
            ) {
                if (destructive) {
                    Text(confirmLabel, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
