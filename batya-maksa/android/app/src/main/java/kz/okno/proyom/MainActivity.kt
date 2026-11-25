package kz.okno.proyom

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

val Accent = Color(0xFF127A8C)
val Deep = Color(0xFF0E204A)
val Danger = Color(0xFFFF3B30)
val Warn = Color(0xFFFF9500)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val dark = when (vm.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            // Значки статус-бара под тему
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            ProyomTheme(dark) {
                // Для автотеста на эмуляторе: --ez demo true, --es tab catalog
                App(vm, demo = intent.getBooleanExtra("demo", false), startTab = intent.getStringExtra("tab"))
            }
        }
    }
}

/** Тема как на iPhone: системные цвета iOS + Material-цвета для меню, листов и переключателей. */
@Composable
fun ProyomTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = Accent, onPrimary = Color.White, secondary = Accent, background = p.bg, surface = p.card,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerLow = p.bg, surfaceContainerHighest = p.fill,
        onSurface = p.text, onSurfaceVariant = p.secondary, outline = p.separator, surfaceVariant = p.fill,
    ) else lightColorScheme(
        primary = Accent, onPrimary = Color.White, secondary = Accent, background = p.bg, surface = p.card,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerLow = p.bg, surfaceContainerHighest = p.fill,
        onSurface = p.text, onSurfaceVariant = p.secondary, outline = p.separator, surfaceVariant = p.fill,
    )
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}

private data class TabInfo(val tab: Tab, val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabInfo(Tab.ESTIMATE, "Расчёт", Icons.Filled.CameraAlt),
    TabInfo(Tab.HISTORY, "История", Icons.Filled.History),
    TabInfo(Tab.CATALOG, "Каталог", Icons.Filled.Inventory2),
    TabInfo(Tab.SETTINGS, "Настройки", Icons.Filled.Settings),
)

@Composable
fun App(vm: AppViewModel, demo: Boolean = false, startTab: String? = null) {
    LaunchedEffect(Unit) {
        if (demo) vm.demo()
        when (startTab) {
            "history" -> vm.tab = Tab.HISTORY
            "catalog" -> vm.tab = Tab.CATALOG
            "settings" -> vm.tab = Tab.SETTINGS
        }
    }
    val p = pal()
    Scaffold(
        containerColor = p.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { TabBar(vm.tab) { vm.tab = it } },
    ) { padding ->
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .statusBarsPadding().imePadding().dismissKeyboard(),
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

/** Нижняя панель вкладок как UITabBar: без «таблетки», активная вкладка — акцентным цветом. */
@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val p = pal()
    Column(Modifier.fillMaxWidth().background(p.bar).navigationBarsPadding()) {
        HorizontalDivider(thickness = 0.5.dp, color = p.separator)
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val tint by animateColorAsState(if (current == t.tab) Accent else p.secondary, label = "tab")
                Column(
                    Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(t.tab) }
                        .padding(top = 6.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, contentDescription = t.title, tint = tint, modifier = Modifier.size(26.dp))
                    Text(t.title, color = tint, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
