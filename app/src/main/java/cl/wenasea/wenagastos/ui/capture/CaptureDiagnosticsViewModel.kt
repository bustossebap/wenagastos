package cl.wenasea.wenagastos.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.wenasea.wenagastos.capture.AppLookup
import cl.wenasea.wenagastos.capture.SourceRow
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Últimos avisos que vio el listener, y cómo están las apps de los bancos en el teléfono. */
class CaptureDiagnosticsViewModel(
    private val logs: CaptureLogRepository,
    private val lookup: AppLookup,
) : ViewModel() {

    val entries: StateFlow<List<CaptureLogEntity>> = logs.observeRecent(100).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val statuses = MutableStateFlow<List<SourceRow>>(emptyList())

    /** Instalada, habilitada y con avisos: lo que hay que mirar cuando un movimiento no aparece. */
    val sources: StateFlow<List<SourceRow>> = statuses

    init {
        refresh()
    }

    /**
     * El permiso de notificaciones de un banco se cambia en Ajustes del sistema, así que la
     * pantalla vuelve a preguntar al volver (ON_RESUME) en vez de confiar en lo que leyó al abrir.
     */
    fun refresh() {
        viewModelScope.launch {
            statuses.value = withContext(Dispatchers.IO) { lookup.rows() }
        }
    }

    fun clear() {
        viewModelScope.launch { logs.clear() }
    }
}
