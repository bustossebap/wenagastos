package cl.wenasea.wenagastos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Qué pasó con una notificación que vio el listener. */
enum class CaptureOutcome(val label: String) {
    SAVED("Registrado"),
    PENDING("Por revisar"),
    DUPLICATE("Duplicado"),
    MERGED("Completado con el banco"),
    IGNORED("Otra app"),
    REJECTED("No es un movimiento"),
    ERROR("Error"),
}

/**
 * Bitácora de captura: la última foto de lo que vieron las notificaciones.
 * Sirve para la pantalla de diagnóstico y para ajustar los parsers.
 * Se recorta a [MAX_ROWS] filas.
 */
@Entity(tableName = "capture_logs", indices = [Index("postedAt")])
data class CaptureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val sourceLabel: String? = null,
    val title: String,
    val text: String,
    val postedAt: Long,
    val outcome: CaptureOutcome,
    val detail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MAX_ROWS = 200
    }
}
