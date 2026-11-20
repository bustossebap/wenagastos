package cl.wenasea.wenagastos.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Últimos avisos que vio el listener, para calibrar la lectura de cada banco. */
class CaptureDiagnosticsViewModel(private val logs: CaptureLogRepository) : ViewModel() {

    val entries: StateFlow<List<CaptureLogEntity>> = logs.observeRecent(100).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun clear() {
        viewModelScope.launch { logs.clear() }
    }
}
