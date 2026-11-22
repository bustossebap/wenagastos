package cl.wenasea.wenagastos.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * La actividad detrás de cualquier contexto de Compose. Hace falta para lo que solo existe a nivel
 * de actividad: el diálogo de credenciales del sistema y volver a dibujar la pantalla al cambiar de
 * tema.
 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
