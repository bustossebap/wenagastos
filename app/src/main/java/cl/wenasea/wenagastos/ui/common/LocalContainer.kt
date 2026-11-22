package cl.wenasea.wenagastos.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.wenasea.wenagastos.AppContainer
import cl.wenasea.wenagastos.ui.balances.BalancesViewModel
import cl.wenasea.wenagastos.ui.capture.CaptureDiagnosticsViewModel
import cl.wenasea.wenagastos.ui.editor.EditorViewModel
import cl.wenasea.wenagastos.ui.ledger.LedgerViewModel
import cl.wenasea.wenagastos.ui.reports.ReportsViewModel
import cl.wenasea.wenagastos.ui.settings.MerchantRulesViewModel
import cl.wenasea.wenagastos.ui.settings.SettingsViewModel

/** Acceso al contenedor de dependencias desde Compose. */
val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no fue provisto")
}

object VmFactories {

    fun ledger(container: AppContainer) = viewModelFactory {
        initializer {
            LedgerViewModel(container.transactions, container.categories, container.accounts)
        }
    }

    fun editor(container: AppContainer, txId: Long) = viewModelFactory {
        initializer {
            EditorViewModel(
                txId,
                container.transactions,
                container.accounts,
                container.categories,
                container.rules,
            )
        }
    }

    fun settings(container: AppContainer) = viewModelFactory {
        initializer {
            SettingsViewModel(
                container.accounts,
                container.accountMerge,
                container.categories,
                container.captureLogs,
                container.backup,
                container.files,
                container.backupStamp,
            )
        }
    }

    fun reports(container: AppContainer) = viewModelFactory {
        initializer {
            ReportsViewModel(container.transactions, container.categories, container.accounts)
        }
    }

    fun merchantRules(container: AppContainer) = viewModelFactory {
        initializer {
            MerchantRulesViewModel(container.rules, container.categories)
        }
    }

    fun captureDiagnostics(container: AppContainer) = viewModelFactory {
        initializer {
            CaptureDiagnosticsViewModel(container.captureLogs, container.apps)
        }
    }

    fun balances(container: AppContainer) = viewModelFactory {
        initializer {
            BalancesViewModel(container.accounts, container.snapshots, container.transactions)
        }
    }
}
