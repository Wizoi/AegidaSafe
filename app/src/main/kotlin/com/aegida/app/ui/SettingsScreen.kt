package com.aegida.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aegida.core.alert.Guardians
import com.aegida.core.model.SafetyPicture
import com.aegida.core.net.PublicPages
import com.aegida.core.model.Setup
import com.aegida.core.text.Texts

/** What My settings shows. */
data class SettingsContent(
    val setup: Setup,
    val guardians: Guardians,
    val filterOn: Boolean,
    val listsUpdatedAt: java.time.Instant?,
    val zone: java.time.ZoneId,
    val locale: java.util.Locale,
    /** Things the owner told Aegida are fine, each with a way to take it back (Kevin, Oct 11). */
    val allowed: List<com.aegida.core.notice.AllowedItem> = emptyList(),
    /** "Tell a friend" and "Rate" show only once Aegida is public on Play (closed testing would give a friend "not found"). */
    val listedOnPlay: Boolean = PublicPages.LISTED_ON_PLAY,
)

/** What the buttons and the switch in My settings do. */
data class SettingsActions(
    val onChangePicture: () -> Unit,
    val onChangeGuardian: () -> Unit,
    val onResendInvite: () -> Unit,
    val onAlerts: (Boolean) -> Unit,
    val onFilterHelp: () -> Unit,
    val onHowItWorks: () -> Unit,
    val onStopAllowing: (com.aegida.core.notice.AllowedItem) -> Unit = {},
    val onWhatYouSee: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onRate: () -> Unit = {},
)

/** What the owner can see and change later. */
@Composable
fun SettingsScreen(content: SettingsContent, actions: SettingsActions) {
    val setup = content.setup
    val guardians = content.guardians
    val filterOn = content.filterOn
    val allowed = content.allowed
    val picture = SafetyPicture.fromId(setup.pictureId)
    AegidaScreen(setup.pictureId) {
        Text(Texts.Settings.TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        QuietButton(Texts.Setup.SEE_TITLE, actions.onWhatYouSee)
        SettingCard {
            ValueRow(Texts.Settings.NAME, guardians.ownerName.ifBlank { Texts.Settings.NOT_SET })
            ValueRow(Texts.Settings.GUARDIAN, guardians.firstGuardianName.ifBlank { Texts.Settings.NOT_SET })
            QuietButton(Texts.Settings.RESEND_INVITE, actions.onResendInvite)
            QuietButton(Texts.Settings.CHANGE_GUARDIAN, actions.onChangeGuardian)
        }
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (picture != null) PictureTile(picture, 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(Texts.Settings.PICTURE, style = MaterialTheme.typography.titleMedium)
                    Text(picture?.label ?: Texts.Settings.NOT_SET, style = MaterialTheme.typography.bodyLarge)
                }
            }
            QuietButton(Texts.Settings.CHANGE, actions.onChangePicture)
        }
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Texts.Settings.TELL_GUARDIANS, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = guardians.alertsOn, onCheckedChange = actions.onAlerts)
            }
        }
        SettingCard {
            ValueRow(Texts.Settings.WEBSITE_PROTECTION, if (filterOn) Texts.Settings.ON else Texts.Settings.OFF)
            // Skipped in setup: an easy way to try again.
            if (!filterOn) BigButton(Texts.Settings.TURN_ON_WEBSITE_PROTECTION, actions.onFilterHelp)
        }
        if (allowed.isNotEmpty()) {
            SettingCard {
                Text(Texts.Settings.ALLOWED_TITLE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                allowed.forEach { item ->
                    Text(Texts.Settings.allowedLine(item.problem, item.label, item.subject), style = MaterialTheme.typography.bodyLarge)
                    QuietButton(Texts.Settings.ALLOWED_REMOVE, { actions.onStopAllowing(item) })
                }
            }
        }
        SettingCard {
            ValueRow(Texts.Settings.CREDITS_TITLE, Texts.Settings.CREDITS)
            Text(TimeText.listsLine(content.listsUpdatedAt, content.zone, content.locale), style = MaterialTheme.typography.bodyLarge)
        }
        QuietButton(Texts.Settings.HOW_IT_WORKS, actions.onHowItWorks)
        if (content.listedOnPlay) {
            QuietButton(Texts.Settings.SHARE_BUTTON, actions.onShare)
            Text(Texts.Settings.RATE_NOTE, style = MaterialTheme.typography.bodyLarge)
            QuietButton(Texts.Settings.RATE_BUTTON, actions.onRate)
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
