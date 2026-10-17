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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
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
import com.sugarbridge.app.ui.components.TabItem
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
import com.sugarbridge.app.update.Release
import com.sugarbridge.app.update.Updater

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
    fun openNotificationAccess()
    fun openAppInfo()
    fun openLink(url: String)
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
                bottomBar = {
                    Column {
                        // minute mode switched background sync off: this stays until it is read and closed
                        if (vm.ui.settings.backgroundNotice) BackgroundNotice(vm::openBackgroundSettings, vm::dismissBackgroundNotice)
                        Dock(vm.tab) { vm.tab = it }
                    }
                },
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
        if (vm.privacyOpen) PrivacyDialog(onClose = { vm.privacyOpen = false })
        vm.updateOffer?.let { release ->
            UpdateOfferDialog(
                release,
                onInstall = {
                    vm.closeUpdateOffer()
                    actions.installUpdate()
                },
                onNotes = { actions.openLink(release.pageUrl) },
                onLater = vm::closeUpdateOffer,
            )
        }
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
            TabItem(stringResource(label), icon, selected = tab == current, onClick = { onSelect(tab) })
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
 * Minute mode switched background sync off. The pink notice says so and stays until its link to the setting
 * or its ✕ is tapped.
 */
@Composable
private fun BackgroundNotice(onOpen: () -> Unit, onClose: () -> Unit) {
    XpWindow(
        "SugarBridge ♡",
        Modifier.padding(horizontal = 12.dp).padding(top = 6.dp).testTag("background-notice"),
        bodyPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 12.dp),
        colors = pinkWindow(),
        onClose = onClose,
    ) {
        Text(stringResource(R.string.notice_background_off), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.notice_background_link),
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color = LocalXp.current.accent,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 4.dp).testTag("background-notice-link"),
        )
    }
}

/**
 * What SugarBridge does with the data: what it writes to and reads from Health Connect, where the password
 * goes and where nothing goes. Health Connect's "privacy policy" link opens it, and so does About.
 */
@Composable
private fun PrivacyDialog(onClose: () -> Unit) {
    XpDialog(onDismissRequest = onClose) {
        XpWindow(stringResource(R.string.privacy_title), Modifier.testTag("privacy")) {
            Text(stringResource(R.string.privacy_health), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.privacy_local), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.privacy_password), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.privacy_nothing_else), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * A new version is out: the window offers to download and install it, once for each version (see UpdateOffer).
 * "Later" closes it; the banner on the home screen and the About window still offer the version.
 */
@Composable
private fun UpdateOfferDialog(release: Release, onInstall: () -> Unit, onNotes: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { Updater.installedVersion(context).substringBefore('-') }
    XpDialog(onDismissRequest = onLater) {
        XpWindow(stringResource(R.string.update_title), Modifier.testTag("update-offer")) {
            Mascot(Mood.HAPPY, size = 88.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(stringResource(R.string.update_offer_text, release.version, installed), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(stringResource(R.string.update_install), onClick = onInstall, modifier = Modifier.fillMaxWidth())
            ButtonRow {
                SecondaryButton(stringResource(R.string.update_notes), onClick = onNotes, icon = R.drawable.ic_link_chain)
                SecondaryButton(stringResource(R.string.update_later), onClick = onLater)
            }
            Hint(stringResource(R.string.update_play_protect))
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
