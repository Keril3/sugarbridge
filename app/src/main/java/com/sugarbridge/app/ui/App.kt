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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.sugarbridge.app.ui.screens.LanguageScreen
import com.sugarbridge.app.ui.screens.LoginScreen
import com.sugarbridge.app.ui.screens.SettingsScreen

/** What only the Activity can do: permission dialogs, other apps, system settings and the app language. */
interface AppActions {
    fun requestHealthPermission()
    fun installHealthConnect()
    fun openHealthConnect()
    fun setLive(enabled: Boolean)
    fun setLanguage(tag: String?)
    fun openLibreView()
    fun pickCsv()
    fun hideNotification()
    fun requestBatteryUnrestricted()
    fun openSamsungHealth()
}

@Composable
fun SugarApp(vm: MainViewModel, actions: AppActions) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm, resources) { vm.messages.collect { snackbar.showSnackbar(it.resolve(resources)) } }

    AngelBackground {
        when {
            // the very first start: the language comes before everything else
            !vm.ui.signedIn && !vm.ui.languageChosen -> LanguageScreen(onPick = actions::setLanguage)
            !vm.ui.signedIn -> {
                LoginScreen(busy = vm.ui.signingIn, onSignIn = vm::signIn)
                TenshiSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
            else -> Scaffold(
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
            Triple(Tab.NOW, R.drawable.ic_stat_tenshi, R.string.tab_now),
            Triple(Tab.HISTORY, R.drawable.ic_tab_history, R.string.tab_history),
            Triple(Tab.SETTINGS, R.drawable.ic_tab_settings, R.string.tab_settings),
        )
        items.forEach { (tab, icon, label) ->
            val text = stringResource(label)
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(icon), contentDescription = text) },
                label = { Text(text) },
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
 * Without Health Connect SugarBridge is useless: the data has nowhere to go. The dialog can't be dismissed
 * until Health Connect becomes available; after it is installed, the dialog disappears by itself.
 */
@Composable
private fun HealthConnectGate(state: HealthConnectState, actions: AppActions) {
    val update = state == HealthConnectState.UPDATE_REQUIRED
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Mascot(Mood.SLEEPY, size = 88.dp) },
        title = { Text(stringResource(if (update) R.string.hc_update_title else R.string.hc_missing_title)) },
        text = {
            Column {
                Text(stringResource(R.string.hc_gate_text) + " " + stringResource(if (update) R.string.hc_gate_old else R.string.hc_gate_missing))
                Text(stringResource(R.string.hc_gate_auto), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = actions::installHealthConnect) { Text(stringResource(if (update) R.string.hc_update else R.string.hc_install)) }
        },
        dismissButton = {
            TextButton(onClick = actions::openHealthConnect) { Text(stringResource(R.string.hc_settings)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
