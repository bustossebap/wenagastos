package cl.wenasea.wenagastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import cl.wenasea.wenagastos.ui.common.LocalContainer
import cl.wenasea.wenagastos.ui.nav.WenagastosNavHost
import cl.wenasea.wenagastos.ui.theme.WenagastosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WenagastosApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                WenagastosTheme {
                    WenagastosNavHost()
                }
            }
        }
    }
}
