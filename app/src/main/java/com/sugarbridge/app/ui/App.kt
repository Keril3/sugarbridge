package com.sugarbridge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.Tab
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.SkyBackground
import com.sugarbridge.app.ui.components.XpDialog
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.components.pinkWindow
import com.sugarbridge.app.ui.screens.HistoryScreen
import com.sugarbridge.app.ui.screens.HomeScreen
import com.sugarbridge.app.ui.screens.LanguageScreen
import com.sugarbridge.app.ui.screens.LoginScreen
import com.sugarbridge.app.ui.screens.SettingsScreen
import com.sugarbridge.app.ui.screens.UpdateBanner
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi

/** What only the Activity can do: permission dialogs, other apps, system settings, the app language and updates. */
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
    fun openGitHub()
    fun openUsageAccess()
    fun installUpdate()
}

@Composable
fun SugarApp(vm: MainViewModel, actions: AppActions) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm, resources) { vm.messages.collect { snackbar.showSnackbar(it.resolve(resources)) } }

    SkyBackground {
        when {
            // the very first start: the language comes before everything else
            !vm.ui.signedIn && !vm.ui.languageChosen -> LanguageScreen(onContinue = actions::setLanguage)
            !vm.ui.signedIn -> {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    // a new version is offered before signing in too
                    UpdateBanner(vm.update, actions, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    LoginScreen(busy = vm.ui.signingIn, onSignIn = vm::signIn)
                }
                TenshiSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
            else -> Scaffold(
                containerColor = Color.Transparent,
                topBar = { Spacer(Modifier.statusBarsPadding().height(8.dp)) },
                bottomBar = { Dock(vm.tab) { vm.tab = it } },
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

/** The tab bar: a translucent bar like the address bar, the open tab is a pressed glossy button. */
@Composable
private fun Dock(current: Tab, onSelect: (Tab) -> Unit) {
    val xp = LocalXp.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(6.dp, shape, spotColor = xp.shadow)
            .clip(shape)
            .border(1.dp, xp.statusBorder, shape)
            .background(Brush.verticalGradient(xp.statusBar))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Triple(Tab.NOW, R.drawable.ic_tab_home, R.string.tab_now),
            Triple(Tab.HISTORY, R.drawable.ic_tab_history, R.string.tab_history),
            Triple(Tab.SETTINGS, R.drawable.ic_tab_settings, R.string.tab_settings),
        ).forEach { (tab, icon, label) ->
            val on = tab == current
            val itemShape = RoundedCornerShape(9.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .then(
                        if (on) {
                            Modifier
                                .border(1.dp, Tenshi.ButtonBorder, itemShape)
                                .background(Brush.verticalGradient(0f to Tenshi.ButtonGloss[0], 0.48f to Tenshi.ButtonGloss[1], 0.5f to Tenshi.ButtonGloss[2], 1f to Tenshi.ButtonGloss[3]))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val color = if (on) Tenshi.ButtonText else xp.text
                Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

/**
 * Messages pop up as a small pink window: shaped like the others but in Tenshi-chan's pink, so it doesn't melt
 * into the screen behind it. A tap closes it early.
 */
@Composable
private fun TenshiSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        XpWindow(
            "SugarBridge ♡",
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp).testTag("popup"),
            bodyPadding = PaddingValues(start = 14.dp, top = 16.dp, end = 14.dp, bottom = 14.dp),
            onBodyClick = data::dismiss,
            colors = pinkWindow(),
        ) {
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Without Health Connect SugarBridge is useless: the data has nowhere to go. The window can't be dismissed
 * until Health Connect becomes available; after it is installed, the window disappears by itself.
 */
@Composable
private fun HealthConnectGate(state: HealthConnectState, actions: AppActions) {
    val update = state == HealthConnectState.UPDATE_REQUIRED
    XpDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        XpWindow(stringResource(if (update) R.string.hc_update_title else R.string.hc_missing_title)) {
            Mascot(Mood.SLEEPY, size = 96.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                stringResource(R.string.hc_gate_text) + " " + stringResource(if (update) R.string.hc_gate_old else R.string.hc_gate_missing),
                style = MaterialTheme.typography.bodyMedium,
            )
            Hint(stringResource(R.string.hc_gate_auto))
            ButtonRow {
                PrimaryButton(stringResource(if (update) R.string.hc_update else R.string.hc_install), onClick = actions::installHealthConnect)
                SecondaryButton(stringResource(R.string.hc_settings), onClick = actions::openHealthConnect)
            }
        }
    }
}
