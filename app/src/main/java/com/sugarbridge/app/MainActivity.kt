package com.sugarbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.SugarApp
import com.sugarbridge.app.ui.SugarTheme

class MainActivity : ComponentActivity(), AppActions {

    private val vm: MainViewModel by viewModels()

    private val healthPermission =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            vm.refreshStatus()
            if (WRITE_GLUCOSE !in granted) vm.notify("Без разрешения Health Connect сахар записывать некуда")
        }

    // минутный режим включаем в любом случае: без разрешения его уведомление просто не видно в шторке
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
        scheduleSync(this)   // безопасно вызывать при каждом запуске
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            SugarTheme { SugarApp(vm, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
    }

    /** CSV, открытый через SugarBridge из браузера, файлового менеджера или «Поделиться». */
    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) vm.importUri(uri)
    }

    override fun requestHealthPermission() {
        if (healthConnectState(this) == HealthConnectState.AVAILABLE) healthPermission.launch(setOf(WRITE_GLUCOSE))
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

    override fun openLibreView() = libreView.launch(Intent(this, LibreViewActivity::class.java))

    override fun pickCsv() =
        csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))

    /** Настройки канала уведомления: там его можно выключить, минутный режим продолжит работать. */
    override fun hideNotification() {
        val channel = if (vm.ui.settings.liveShowValue) LiveSyncService.CHANNEL_LIVE else LiveSyncService.CHANNEL_QUIET
        open(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        )
    }

    @SuppressLint("BatteryLife")   // приложение не для Google Play, а минутному режиму сон мешает
    override fun requestBatteryUnrestricted() =
        open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))

    override fun openSamsungHealth() {
        packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH_PACKAGE)?.let(::open)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            vm.notify("Не нашлось приложения, чтобы это открыть")
        } catch (e: SecurityException) {
            vm.notify("Android не дал это открыть")
        }
    }
}
