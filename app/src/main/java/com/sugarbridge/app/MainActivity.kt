package com.sugarbridge.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var signedIn by mutableStateOf(false)
    private var email by mutableStateOf("")
    private var password by mutableStateOf("")
    private var status by mutableStateOf("")

    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            status = if (WRITE_GLUCOSE in granted) "Доступ к Health Connect есть" else "Без доступа к Health Connect записывать некуда"
        }

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            // сервис запускаем в любом случае: без разрешения его уведомление просто не видно в шторке
            startForegroundService(Intent(this, LiveSyncService::class.java))
            status = if (ok) "Сахар обновляется раз в минуту" else "Сахар обновляется раз в минуту, уведомление скрыто"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleSync(this)   // безопасно вызывать при каждом запуске благодаря KEEP
        signedIn = Libre.client(this) != null
        setContent {
            MaterialTheme {
                Column(
                    Modifier.safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (signedIn) MainScreen() else LoginScreen()
                    Text(status)
                }
            }
        }
    }

    @Composable
    private fun LoginScreen() {
        Text("Вход в LibreLinkUp", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { signIn() }, enabled = email.isNotBlank() && password.isNotEmpty()) {
            Text("Войти")
        }
    }

    @Composable
    private fun MainScreen() {
        Button(onClick = { requestPermissions.launch(setOf(WRITE_GLUCOSE)) }) { Text("Доступ к Health Connect") }
        Button(onClick = { syncNow() }) { Text("Синхронизировать сейчас") }
        Button(onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("Обновлять раз в минуту")
        }
        OutlinedButton(onClick = { stopService(Intent(this@MainActivity, LiveSyncService::class.java)) }) {
            Text("Остановить обновление")
        }
        OutlinedButton(onClick = { check() }) { Text("Проверить LibreLinkUp") }
        OutlinedButton(onClick = { signOut() }) { Text("Выйти") }
    }

    private fun signIn() {
        val e = email.trim()
        val p = password
        status = "Вхожу…"
        lifecycleScope.launch {
            status = try {
                withContext(Dispatchers.IO) { Libre.signIn(this@MainActivity, e, p) }
                signedIn = true
                password = ""
                "Вход выполнен"
            } catch (ex: Exception) {
                "Не получилось: ${ex.message}"
            }
        }
    }

    private fun check() {
        status = "Запрашиваю LibreLinkUp…"
        lifecycleScope.launch {
            status = try {
                val readings = withContext(Dispatchers.IO) { Libre.client(this@MainActivity)!!.readings() }
                "Замеров за 12 часов: ${readings.size}\nПоследний: ${readings.lastOrNull()?.text() ?: "—"}"
            } catch (ex: Exception) {
                "Ошибка: ${ex.message}"
            }
        }
    }

    private fun syncNow() {
        status = "Синхронизация…"
        lifecycleScope.launch {
            status = try {
                val result = syncOnce(this@MainActivity)
                "Записано в Health Connect: ${result.written}\nПоследний: ${result.latest?.text() ?: "—"}"
            } catch (ex: Exception) {
                "Ошибка: ${ex.message}"
            }
        }
    }

    private fun signOut() {
        stopService(Intent(this, LiveSyncService::class.java))
        Libre.signOut(this)
        signedIn = false
        status = ""
    }
}
