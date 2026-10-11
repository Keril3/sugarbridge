package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.R
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.components.OutlinedTitle
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi

@Composable
fun LoginScreen(busy: Boolean, onSignIn: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }   // the password is not put into the saved screen state
    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !busy
    val xp = LocalXp.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Tenshi.ButtonBorder,
        unfocusedBorderColor = xp.dots,
        focusedLabelColor = xp.accent,
        cursorColor = xp.accent,
        focusedTextColor = xp.text,
        unfocusedTextColor = xp.text,
    )

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Mascot(Mood.HAPPY, size = 150.dp)
        OutlinedTitle("SugarBridge", style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        XpWindow(stringResource(R.string.login_title)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                stringResource(R.string.login_button),
                onClick = { onSignIn(email, password) },
                enabled = canSubmit,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(stringResource(R.string.login_hint))
        }
    }
}
