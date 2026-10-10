package com.sugarbridge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.Tab
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.ui.components.AngelBackground
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.screens.HistoryScreen
import com.sugarbridge.app.ui.screens.HomeScreen
import com.sugarbridge.app.ui.screens.LoginScreen
import com.sugarbridge.app.ui.screens.SettingsScreen

/** То, что умеет только Activity: окна разрешений, другие приложения и системные настройки. */
interface AppActions {
    fun requestHealthPermission()
    fun installHealthConnect()
    fun openHealthConnect()
    fun setLive(enabled: Boolean)
    fun openLibreView()
    fun pickCsv()
    fun hideNotification()
    fun requestBatteryUnrestricted()
    fun openSamsungHealth()
}

@Composable
fun SugarApp(vm: MainViewModel, actions: AppActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    AngelBackground {
        if (!vm.ui.signedIn) {
            LoginScreen(busy = vm.ui.signingIn, onSignIn = vm::signIn)
            TenshiSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { TenshiNavBar(vm.tab) { vm.tab = it } },
                snackbarHost = { TenshiSnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (vm.tab) {
                        Tab.NOW -> HomeScreen(vm, actions)
                        Tab.HISTORY -> HistoryScreen(vm, actions)
                        Tab.SETTINGS -> SettingsScreen(vm, actions)
                    }
                }
            }
        }
        if (vm.ui.healthConnect != HealthConnectState.AVAILABLE) HealthConnectGate(vm.ui.healthConnect, actions)
    }
}

@Composable
private fun TenshiNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), tonalElevation = 0.dp) {
        val items = listOf(
            Triple(Tab.NOW, R.drawable.ic_stat_tenshi, "Сейчас"),
            Triple(Tab.HISTORY, R.drawable.ic_tab_history, "История"),
            Triple(Tab.SETTINGS, R.drawable.ic_tab_settings, "Настройки"),
        )
        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(icon), contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun TenshiSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data,
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Без Health Connect SugarBridge бесполезен: данным некуда идти. Окно не закрывается,
 * пока Health Connect не станет доступен — после установки оно исчезнет само.
 */
@Composable
private fun HealthConnectGate(state: HealthConnectState, actions: AppActions) {
    val update = state == HealthConnectState.UPDATE_REQUIRED
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Mascot(Mood.SLEEPY, size = 88.dp) },
        title = { Text(if (update) "Обновите Health Connect" else "Нужен Health Connect") },
        text = {
            Column {
                Text(
                    "SugarBridge передаёт сахар в Samsung Health через Health Connect — без него данным некуда идти. " +
                        if (update) "Установленная версия слишком старая." else "На этом телефоне он не найден или выключен."
                )
                Text("Это окно закроется само, как только Health Connect заработает.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = actions::installHealthConnect) { Text(if (update) "Обновить" else "Установить") }
        },
        dismissButton = {
            TextButton(onClick = actions::openHealthConnect) { Text("Настройки Health Connect") }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
