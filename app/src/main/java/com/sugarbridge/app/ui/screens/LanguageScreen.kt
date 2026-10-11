package com.sugarbridge.app.ui.screens

import android.app.LocaleManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.R
import com.sugarbridge.app.settings.LANGUAGES
import com.sugarbridge.app.settings.appLanguage
import com.sugarbridge.app.ui.components.CardTitle
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.components.TenshiCard
import com.sugarbridge.app.ui.theme.Tenshi

/** The first screen on the first start: pick the app language. One tap applies it and moves on to sign-in. */
@Composable
fun LanguageScreen(onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val current = remember { appLanguage(context)?.tag }
    val phone = remember {
        val locale = context.getSystemService(LocaleManager::class.java).systemLocales[0]
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Mascot(Mood.HAPPY, size = 130.dp)
        Text("SugarBridge", style = MaterialTheme.typography.displaySmall.copy(brush = Brush.linearGradient(Tenshi.Rainbow)))
        TenshiCard {
            CardTitle(stringResource(R.string.language_title))
            Column {
                LanguageRow(stringResource(R.string.language_system), phone, selected = current == null) { onPick(null) }
                LANGUAGES.forEach { language ->
                    LanguageRow(language.name, null, selected = current == language.tag) { onPick(language.tag) }
                }
            }
            Hint(stringResource(R.string.language_hint))
        }
    }
}

@Composable
private fun LanguageRow(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (detail != null) Hint(detail)
        }
    }
}
