package cl.wenasea.wenagastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cl.wenasea.wenagastos.ui.home.HomeScreen
import cl.wenasea.wenagastos.ui.theme.WenagastosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WenagastosApp()
        }
    }
}

@Composable
fun WenagastosApp() {
    WenagastosTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            HomeScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WenagastosAppPreview() {
    WenagastosApp()
}
