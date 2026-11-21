package kz.okno.proyom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

val Accent = Color(0xFF127A8C)
val Deep = Color(0xFF0E204A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    secondary = Deep,
                    primaryContainer = Color(0xFFD5EEF2),
                    onPrimaryContainer = Deep,
                    background = Color(0xFFF6F7F9),
                    surface = Color(0xFFF6F7F9),
                    surfaceContainer = Color.White,
                ),
            ) { App() }
        }
    }
}

private data class TabInfo(val tab: Tab, val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabInfo(Tab.ESTIMATE, "Расчёт", Icons.Filled.CameraAlt),
    TabInfo(Tab.HISTORY, "История", Icons.Filled.History),
    TabInfo(Tab.CATALOG, "Каталог", Icons.Filled.Inventory2),
    TabInfo(Tab.SETTINGS, "Настройки", Icons.Filled.Settings),
)

@Composable
fun App(vm: AppViewModel = viewModel()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = vm.tab == t.tab,
                        onClick = { vm.tab = t.tab },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "tabs",
        ) { tab ->
            when (tab) {
                Tab.ESTIMATE -> EstimateScreen(vm)
                Tab.HISTORY -> HistoryScreen(vm)
                Tab.CATALOG -> CatalogScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
