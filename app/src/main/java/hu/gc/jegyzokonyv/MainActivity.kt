package hu.gc.jegyzokonyv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import hu.gc.jegyzokonyv.ui.nav.AppNavHost
import hu.gc.jegyzokonyv.ui.theme.JegyzokonyvTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JegyzokonyvTheme {
                AppNavHost()
            }
        }
    }
}
