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

/** What the owner can see and change later. Changing the guardian is not here yet: it must tell the current guardian first. */
@Composable
fun SettingsScreen(
    setup: Setup,
    guardians: Guardians,
    filterOn: Boolean,
    onChangePicture: () -> Unit,
    onAlerts: (Boolean) -> Unit,
    onFilterHelp: () -> Unit,
) {
    val picture = SafetyPicture.fromId(setup.pictureId)
    AegidaScreen(setup.pictureId) {
        Text(Texts.Settings.TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        SettingCard {
            ValueRow(Texts.Settings.NAME, guardians.ownerName.ifBlank { Texts.Settings.NOT_SET })
            ValueRow(Texts.Settings.GUARDIAN, guardians.firstGuardianName.ifBlank { Texts.Settings.NOT_SET })
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
            if (!filterOn) QuietButton(Texts.Setup.FILTER_TITLE, onFilterHelp)
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
