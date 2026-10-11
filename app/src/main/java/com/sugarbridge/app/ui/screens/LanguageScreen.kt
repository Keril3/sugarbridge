package com.sugarbridge.app.ui.screens

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.LocaleList
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.R
import com.sugarbridge.app.settings.LANGUAGES
import com.sugarbridge.app.settings.appLanguage
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Mascot
import com.sugarbridge.app.ui.components.Mood
import com.sugarbridge.app.ui.components.OutlinedTitle
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.theme.Tenshi

/**
 * The first screen on the first start: pick the app language. A tap only marks the choice, and the texts here
 * already show in it; "Continue" applies it and moves on to sign-in.
 */
@Composable
fun LanguageScreen(onContinue: (String?) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    var chosen by rememberSaveable { mutableStateOf(appLanguage(context)?.tag) }
    val phone = remember {
        val locale = context.getSystemService(LocaleManager::class.java).systemLocales[0]
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
    // the screen's own words in the marked language, before it is applied
    val preview = remember(chosen, configuration) {
        val tag = chosen
        if (tag == null) {
            resources
        } else {
            val config = Configuration(configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
            context.createConfigurationContext(config).resources
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Mascot(Mood.HAPPY, size = 130.dp)
        OutlinedTitle("SugarBridge", style = MaterialTheme.typography.displaySmall)
        XpWindow(preview.getString(R.string.language_title), bodyPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Column {
                LanguageRow(preview.getString(R.string.language_system), phone, selected = chosen == null) { chosen = null }
                LANGUAGES.forEach { language ->
                    DottedDivider()
                    LanguageRow(language.name, null, selected = chosen == language.tag) { chosen = language.tag }
                }
            }
            Hint(preview.getString(R.string.language_hint))
            PrimaryButton(preview.getString(R.string.continue_button), onClick = { onContinue(chosen) }, modifier = Modifier.fillMaxWidth())
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
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Tenshi.ButtonBorder))
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Hint(detail)
        }
    }
}
