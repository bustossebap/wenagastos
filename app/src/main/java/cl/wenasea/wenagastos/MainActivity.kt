package cl.wenasea.wenagastos

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.lock.LockScreen
import cl.wenasea.wenagastos.ui.nav.WenagastosNavHost
import cl.wenasea.wenagastos.ui.theme.WenagastosTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WenagastosApp).container
        setContent {
            val lock = container.appLock
            val lifecycleOwner = LocalLifecycleOwner.current

            // Al volver de segundo plano (o del selector de archivos) se decide si se pide otra vez.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> lock.onLeave()
                        Lifecycle.Event.ON_START -> lock.onReturn()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            CompositionLocalProvider(LocalContainer provides container) {
                WenagastosTheme {
                    if (lock.locked) {
                        // Tapa la app entera: no hay pestañas ni datos detrás de la cortina.
                        LockScreen(onUnlock = lock::unlock)
                    } else {
                        WenagastosNavHost()
                    }
                }
            }
        }
    }
}
