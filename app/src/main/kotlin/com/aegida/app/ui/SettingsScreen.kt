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
import com.aegida.core.model.Setup
import com.aegida.core.text.Texts

/** What the owner can see and change later. */
@Composable
fun SettingsScreen(
    setup: Setup,
    guardians: Guardians,
    filterOn: Boolean,
    onChangePicture: () -> Unit,
    onChangeGuardian: () -> Unit,
    onResendInvite: () -> Unit,
    onAlerts: (Boolean) -> Unit,
    onFilterHelp: () -> Unit,
    onHowItWorks: () -> Unit,
    listsUpdatedAt: java.time.Instant?,
    zone: java.time.ZoneId,
    locale: java.util.Locale,
    /** Things the owner told Aegida are fine, each with a way to take it back (Kevin, Oct 11). */
    allowed: List<com.aegida.core.notice.AllowedItem> = emptyList(),
    onStopAllowing: (com.aegida.core.notice.AllowedItem) -> Unit = {},
) {
    val picture = SafetyPicture.fromId(setup.pictureId)
    AegidaScreen(setup.pictureId) {
        Text(Texts.Settings.TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        SettingCard {
            ValueRow(Texts.Settings.NAME, guardians.ownerName.ifBlank { Texts.Settings.NOT_SET })
            ValueRow(Texts.Settings.GUARDIAN, guardians.firstGuardianName.ifBlank { Texts.Settings.NOT_SET })
            QuietButton(Texts.Settings.RESEND_INVITE, onResendInvite)
            QuietButton(Texts.Settings.CHANGE_GUARDIAN, onChangeGuardian)
        }
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (picture != null) PictureTile(picture, 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(Texts.Settings.PICTURE, style = MaterialTheme.typography.titleMedium)
                    Text(picture?.label ?: Texts.Settings.NOT_SET, style = MaterialTheme.typography.bodyLarge)
                }
            }
            QuietButton(Texts.Settings.CHANGE, onChangePicture)
        }
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Texts.Settings.TELL_GUARDIANS, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = guardians.alertsOn, onCheckedChange = onAlerts)
            }
        }
        SettingCard {
            ValueRow(Texts.Settings.WEBSITE_PROTECTION, if (filterOn) Texts.Settings.ON else Texts.Settings.OFF)
            if (!filterOn) BigButton(Texts.Settings.TURN_ON_WEBSITE_PROTECTION, onFilterHelp) // skipped in setup: an easy way to try again
        }
        if (allowed.isNotEmpty()) {
            SettingCard {
                Text(Texts.Settings.ALLOWED_TITLE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                allowed.forEach { item ->
                    Text(Texts.Home.cleanLabel(item.label).ifEmpty { item.subject }, style = MaterialTheme.typography.bodyLarge) // an app picks its own label (R17)
                    QuietButton(Texts.Settings.ALLOWED_REMOVE, { onStopAllowing(item) })
                }
            }
        }
        SettingCard {
            ValueRow(Texts.Settings.CREDITS_TITLE, Texts.Settings.CREDITS)
            Text(TimeText.listsLine(listsUpdatedAt, zone, locale), style = MaterialTheme.typography.bodyLarge)
        }
        QuietButton(Texts.Settings.HOW_IT_WORKS, onHowItWorks)
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
