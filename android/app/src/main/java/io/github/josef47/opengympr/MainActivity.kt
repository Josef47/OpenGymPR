package io.github.josef47.opengympr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.josef47.opengympr.data.Store
import io.github.josef47.opengympr.ui.DetailScreen
import io.github.josef47.opengympr.ui.Ico
import io.github.josef47.opengympr.ui.LogScreen
import io.github.josef47.opengympr.ui.OpenGymTheme
import io.github.josef47.opengympr.ui.PrSnackbar
import io.github.josef47.opengympr.ui.ProgressScreen
import io.github.josef47.opengympr.ui.SettingsScreen
import io.github.josef47.opengympr.ui.isDark

class MainActivity : ComponentActivity() {
    private val store by lazy { Store(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isDark(store.theme)
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            OpenGymTheme(store.theme) { App(store) }
        }
    }
}

private enum class Tab(val label: String) { LOG("Log"), PROGRESS("Progress"), SETTINGS("Settings") }

@Composable
private fun App(store: Store) {
    var tab by rememberSaveable { mutableStateOf(Tab.LOG) }
    var detail by rememberSaveable { mutableStateOf<Long?>(null) }
    val snack = remember { SnackbarHostState() }

    BackHandler(enabled = detail != null || tab != Tab.LOG) {
        if (detail != null) detail = null else tab = Tab.LOG
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) { PrSnackbar(it) } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && (detail == null || t == Tab.PROGRESS),
                        onClick = { tab = t; detail = null },
                        icon = { Icon(when (t) { Tab.LOG -> Ico.Plus; Tab.PROGRESS -> Ico.Chart; Tab.SETTINGS -> Ico.Gear }, null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        val open: (Long) -> Unit = { tab = Tab.PROGRESS; detail = it }
        when {
            detail != null -> DetailScreen(store, detail!!, snack, m, onBack = { detail = null },
                onLog = { store.select(it); detail = null; tab = Tab.LOG })
            tab == Tab.LOG -> LogScreen(store, snack, m, onOpen = open)
            tab == Tab.PROGRESS -> ProgressScreen(store, m, onOpen = open)
            else -> SettingsScreen(store, snack, m)
        }
    }
}
