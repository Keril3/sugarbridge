package com.sugarbridge.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.debug.LogEntry
import com.sugarbridge.app.debug.StatusLine
import com.sugarbridge.app.debug.collectStatus
import com.sugarbridge.app.debug.hasUsageAccess
import com.sugarbridge.app.debug.samsungHealthEvents
import com.sugarbridge.app.sync.LiveSyncService
import com.sugarbridge.app.sync.scheduleSync
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.OutlinedTitle
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.SkyBackground
import com.sugarbridge.app.ui.components.XpDialog
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SHOWN_ENTRIES = 300

/**
 * The developer tools: what decides how fast a reading travels, the event log with every sync and what Android
 * did around it, Samsung Health's own activity if usage access is given, and a few levers. Refreshes itself.
 */
@Composable
fun DeveloperDialog(vm: MainViewModel, actions: AppActions, onDismiss: () -> Unit) {
    XpDialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SkyBackground {
            val context = LocalContext.current
            var round by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(10_000)
                    round++
                }
            }
            val status by produceState<List<StatusLine>>(emptyList(), round) { value = collectStatus(context) }
            val log by produceState<List<LogEntry>>(emptyList(), round) {
                value = withContext(Dispatchers.IO) {
                    val since = Instant.now().minus(Duration.ofDays(2))
                    (DebugLog.read(context) + samsungHealthEvents(context, since)).sortedBy { it.at }
                }
            }
            val usage = remember(round) { hasUsageAccess(context) }

            LazyColumn(
                Modifier.fillMaxSize().safeDrawingPadding().testTag("developer"),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTitle(stringResource(R.string.dev_title), Modifier.weight(1f), MaterialTheme.typography.headlineSmall)
                        SecondaryButton(stringResource(R.string.close), onClick = onDismiss)
                    }
                }
                item {
                    XpWindow(stringResource(R.string.dev_status)) {
                        status.forEach { StatusRow(it) }
                        SecondaryButton(stringResource(R.string.dev_refresh), onClick = { round++ }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    XpWindow("Samsung Health") {
                        Text(stringResource(R.string.dev_sh_text), style = MaterialTheme.typography.bodyMedium)
                        ButtonRow {
                            SecondaryButton(stringResource(R.string.settings_open_hc), onClick = actions::openHealthConnect)
                            SecondaryButton(stringResource(R.string.settings_open_samsung), onClick = actions::openSamsungHealth)
                        }
                        if (!usage) {
                            Hint(stringResource(R.string.dev_usage_hint))
                            PrimaryButton(stringResource(R.string.dev_usage_grant), onClick = actions::openUsageAccess, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    XpWindow(stringResource(R.string.dev_tools)) {
                        PrimaryButton(stringResource(R.string.sync_now), onClick = { vm.syncNow() }, loading = vm.ui.syncing, modifier = Modifier.fillMaxWidth())
                        SecondaryButton(
                            stringResource(R.string.dev_restart_minute),
                            onClick = {
                                DebugLog.add(context, "service", "restarted by hand")
                                LiveSyncService.reload(context)
                                round++
                            },
                            enabled = vm.ui.settings.liveEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SecondaryButton(
                            stringResource(R.string.dev_reschedule),
                            onClick = {
                                DebugLog.add(context, "worker", "rescheduled by hand")
                                scheduleSync(context)
                                round++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    XpWindow(stringResource(R.string.dev_log)) {
                        Hint(stringResource(R.string.dev_log_hint))
                        ButtonRow {
                            SecondaryButton(stringResource(R.string.dev_copy), onClick = {
                                val clip = ClipData.newPlainText("SugarBridge", report(status, log))
                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                                vm.notify(R.string.dev_copied)
                            })
                            SecondaryButton(stringResource(R.string.dev_share), onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report(status, log))
                                context.startActivity(Intent.createChooser(send, null))
                            })
                            SecondaryButton(stringResource(R.string.dev_clear), onClick = {
                                DebugLog.clear(context)
                                round++
                            }, danger = true)
                        }
                        if (log.isEmpty()) Hint(stringResource(R.string.dev_log_empty))
                    }
                }
                items(log.takeLast(SHOWN_ENTRIES).asReversed()) { LogRow(it) }
            }
        }
    }
}

@Composable
private fun StatusRow(line: StatusLine) {
    val xp = LocalXp.current
    Column(Modifier.fillMaxWidth()) {
        Text(line.label, style = MaterialTheme.typography.labelMedium, color = xp.textMuted)
        Text(line.value, style = MaterialTheme.typography.bodyMedium, color = if (line.warn) Tenshi.Low else xp.text)
    }
}

/** A line of the log: the time, what kind of event, what happened. */
@Composable
private fun LogRow(entry: LogEntry) {
    val xp = LocalXp.current
    val time = entry.at.atZone(ZoneId.systemDefault())
    val today = time.toLocalDate() == LocalDate.now()
    val stamp = time.format(if (today) TIME else DAY_TIME)
    Row(
        Modifier.fillMaxWidth().background(xp.windowBody.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stamp, style = MaterialTheme.typography.labelSmall, color = xp.textMuted, modifier = Modifier.width(if (today) 64.dp else 96.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.kind, style = MaterialTheme.typography.labelSmall, color = kindColor(entry.kind, xp.accent))
            Text(entry.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun kindColor(kind: String, accent: Color): Color = when (kind) {
    "sync" -> Tenshi.InRange
    "error" -> Tenshi.Low
    "s.health" -> Tenshi.PinkDeep
    "phone" -> Tenshi.High
    else -> accent
}

/** The status and the log as text, for a bug report. */
private fun report(status: List<StatusLine>, log: List<LogEntry>): String =
    status.joinToString("\n") { "${it.label}: ${it.value}" } + "\n\n" + DebugLog.format(log)

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DAY_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
