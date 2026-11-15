package com.aegida.app.ui.setup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.aegida.app.ui.AegidaScreen
import com.aegida.app.ui.BigButton
import com.aegida.app.ui.Body
import com.aegida.core.text.GuardianPhoneWords
import com.aegida.core.text.Texts

/** "Sam's phone" in My settings: add, change or clear it. The picker never gives a name here (T4.23). Saved through GuardianDetails.setPhone. */
@Composable
fun GuardianPhoneScreen(pictureId: String?, guardianName: String, current: String, alertsOn: Boolean, onSave: (phone: String) -> Unit) {
    var phone by rememberSaveable { mutableStateOf(current) }
    AegidaScreen(pictureId) {
        Text(GuardianPhoneWords.phoneTitle(guardianName), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        PhoneInput(phone, { phone = it })
        // Only while alerts are on does Sam hear of it; with them off the note waits, and nothing is promised now (K7, K8b).
        if (alertsOn) Body(GuardianPhoneWords.phoneTold(guardianName), centered = false)
        BigButton(Texts.Common.SAVE, { onSave(phone) })
    }
}

/** "Fix a spelling": her first name and the guardian's name, nothing else (T5.2). Sends nothing and keeps the channel. */
@Composable
fun FixNamesScreen(pictureId: String?, ownerName: String, guardianName: String, onSave: (owner: String, guardian: String) -> Unit) {
    var owner by rememberSaveable { mutableStateOf(ownerName) }
    var guardian by rememberSaveable { mutableStateOf(guardianName) }
    AegidaScreen(pictureId) {
        Text(Texts.Settings.FIX_SPELLING, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Field(owner, { owner = it }, Texts.Setup.YOUR_NAME, KeyboardType.Text)
        Field(guardian, { guardian = it }, Texts.Setup.GUARDIAN_NAME, KeyboardType.Text, last = true)
        Body(Texts.Settings.FIX_SPELLING_NOTE, centered = false)
        BigButton(Texts.Common.SAVE, { onSave(owner, guardian) }, enabled = owner.isNotBlank() && guardian.isNotBlank())
    }
}
