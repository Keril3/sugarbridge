package com.sugarbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.SAMSUNG_HEALTH_PACKAGE
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.healthConnectSettingsIntent
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.healthConnectStoreIntent
import com.sugarbridge.app.libreview.LibreViewActivity
import com.sugarbridge.app.settings.setAppLanguage
import com.sugarbridge.app.sync.LiveSyncService
import com.sugarbridge.app.sync.createNotificationChannels
import com.sugarbridge.app.sync.scheduleSync
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.SugarApp
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.SugarTheme
import com.sugarbridge.app.update.GITHUB_PAGE
import com.sugarbridge.app.update.Updater

class MainActivity : ComponentActivity(), AppActions {

    private val vm: MainViewModel by viewModels()

    private var permissionAskedAt = 0L

    private val healthPermission =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            vm.refreshStatus()
            when {
                WRITE_GLUCOSE in granted -> Unit
                // an answer this quick means no dialog was shown: after two refusals Health Connect stops asking,
                // and the permission can only be given in its own settings
                SystemClock.elapsedRealtime() - permissionAskedAt < SILENT_REFUSAL_MS -> {
                    Toast.makeText(this, R.string.msg_hc_no_dialog, Toast.LENGTH_LONG).show()
                    openHealthConnect()
                }
                else -> vm.notify(R.string.msg_hc_denied)
            }
        }

    // minute mode is turned on either way: without the permission its notification just isn't visible in the shade
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.updateSettings { it.copy(liveEnabled = true) }
        }

    private val libreView =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringExtra(LibreViewActivity.EXTRA_PATH)?.let(vm::importFile)
        }

    private val csvPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importUri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannels(this)
        scheduleSync(this)   // safe to call on every start
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            SugarTheme(vm.ui.settings.theme) {
                // status bar icons follow the chosen theme, not only the phone's
                val night = LocalNight.current
                LaunchedEffect(night) {
                    val bars = if (night) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                SugarApp(vm, this)
            }
        }
    }

    /**
     * The app language changed. The screen stays (see configChanges in the manifest) and Compose redraws
     * every text in the new language; only the notification channels need their new names.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        createNotificationChannels(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
    }

    /** A CSV opened with SugarBridge from a browser, a file manager or "Share"; or the package installer answering. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Updater.ACTION_INSTALL_STATUS) {
            onInstallStatus(intent)
            return
        }
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) vm.importUri(uri)
    }

    override fun requestHealthPermission() {
        if (healthConnectState(this) != HealthConnectState.AVAILABLE) return
        permissionAskedAt = SystemClock.elapsedRealtime()
        healthPermission.launch(setOf(WRITE_GLUCOSE))
    }

    override fun installHealthConnect() = open(healthConnectStoreIntent(this))

    override fun openHealthConnect() = open(healthConnectSettingsIntent())

    override fun setLive(enabled: Boolean) {
        val needsPermission = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            !enabled -> vm.updateSettings { it.copy(liveEnabled = false) }
            needsPermission && vm.ui.settings.liveShowValue -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> vm.updateSettings { it.copy(liveEnabled = true) }
        }
    }

    /** null follows the phone. The screen redraws in the new language in place (see onConfigurationChanged). */
    override fun setLanguage(tag: String?) {
        vm.languageChosen()
        setAppLanguage(this, tag)
    }

    override fun openGitHub() = open(Intent(Intent.ACTION_VIEW, GITHUB_PAGE.toUri()))

    /** Installing needs the "install unknown apps" permission once; Android's settings screen grants it. */
    override fun installUpdate() {
        if (Updater.canInstall(this)) {
            vm.installUpdate()
        } else {
            vm.notify(R.string.update_allow_unknown)
            open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
        }
    }

    /** First Android asks for the person's confirmation, then reports how the install went. */
    private fun onInstallStatus(intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let(::open)
            PackageInstaller.STATUS_SUCCESS -> Unit   // Android restarts the app in its new version
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit   // the person said no
            else -> vm.updateFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        }
    }

    override fun openLibreView() = libreView.launch(Intent(this, LibreViewActivity::class.java))

    override fun pickCsv() =
        csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))

    /** The notification channel settings: the notification can be turned off there, minute mode keeps working. */
    override fun hideNotification() {
        val channel = if (vm.ui.settings.liveShowValue) LiveSyncService.CHANNEL_LIVE else LiveSyncService.CHANNEL_QUIET
        open(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        )
    }

    @SuppressLint("BatteryLife")   // the app is not for Google Play, and sleep gets in the way of minute mode
    override fun requestBatteryUnrestricted() =
        open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))

    override fun openSamsungHealth() {
        packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH_PACKAGE)?.let(::open)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            vm.notify(R.string.msg_no_app)
        } catch (e: SecurityException) {
            vm.notify(R.string.msg_not_allowed)
        }
    }

    private companion object {
        /** Faster than anyone can read and answer the Health Connect dialog. */
        const val SILENT_REFUSAL_MS = 1_000L
    }
}
