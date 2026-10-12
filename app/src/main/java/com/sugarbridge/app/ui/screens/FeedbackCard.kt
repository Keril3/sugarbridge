package com.sugarbridge.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.R
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.LinkButton
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.theme.LocalXp

/** One way to reach the developer, the same block as on keril.art's "Contact me" page. */
private class Contact(@param:DrawableRes val icon: Int, val name: String, val value: String, val url: String, val linkLabel: Int)

private const val SITE = "https://keril.art"

/**
 * "Feedback": how to reach the developer, then the developer's website. As on the site, the names and
 * addresses are plain text and only the chain button after each one opens it.
 */
@Composable
fun FeedbackCard(actions: AppActions) {
    val contacts = listOf(
        Contact(R.drawable.ic_contact_mail, stringResource(R.string.feedback_email), "hello@keril.art", "mailto:hello@keril.art", R.string.feedback_email_link),
        Contact(R.drawable.ic_contact_discord, "Discord", "keril3", "https://discord.com/users/249232022483107840", R.string.feedback_discord_link),
        Contact(R.drawable.ic_contact_telegram, "Telegram", "@Minimalnaya_dlina_5_simvolov", "https://t.me/Minimalnaya_dlina_5_simvolov", R.string.feedback_telegram_link),
    )
    XpWindow(stringResource(R.string.feedback_title), Modifier.testTag("feedback")) {
        Text(stringResource(R.string.feedback_intro), style = MaterialTheme.typography.bodyLarge)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            contacts.forEach { ContactRow(it, actions) }
        }
        DottedDivider()
        ContactRow(Contact(R.drawable.ic_link_globe, stringResource(R.string.feedback_site), "keril.art", SITE, R.string.feedback_site_link), actions)
    }
}

@Composable
private fun ContactRow(c: Contact, actions: AppActions) {
    val xp = LocalXp.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(c.icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.labelMedium, color = xp.textMuted)
            Text(c.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 0.dp))
        }
        LinkButton(stringResource(c.linkLabel), onClick = { actions.openLink(c.url) })
    }
}
