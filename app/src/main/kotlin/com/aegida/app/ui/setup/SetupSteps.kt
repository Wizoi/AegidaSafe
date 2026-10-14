package com.aegida.app.ui.setup

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aegida.app.ui.AegidaScreen
import com.aegida.app.ui.AegidaShield
import com.aegida.app.ui.BigButton
import com.aegida.app.ui.DoneRow
import com.aegida.app.ui.Body
import com.aegida.app.ui.PictureTile
import com.aegida.app.ui.QuietButton
import com.aegida.core.alert.Guardians
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.WebsiteFilter
import com.aegida.core.text.Texts

/** The first thing she sees: what Aegida is, in plain words, before any question. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    AegidaScreen(null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AegidaShield(120.dp)
            Text(
                Texts.Setup.WELCOME_TITLE, style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
            )
            Body(Texts.Setup.WELCOME_BODY)
            BigButton(Texts.Setup.WELCOME_START, onStart)
        }
    }
}

/** What the Back button on a first-run step does, or null when there is nothing before it. Set once by the setup flow. */
val LocalSetupBack = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Which picture is shown at the top and which step this is of how many. */
data class StepPosition(val pictureId: String?, val number: Int, val total: Int)

/** One first-run step: a title, a few plain sentences, then the one thing to do. */
@Composable
internal fun StepScreen(position: StepPosition, title: String, content: @Composable () -> Unit) {
    val back = LocalSetupBack.current
    AegidaScreen(position.pictureId) {
        Text(
            Texts.Setup.STEP.format(position.number, position.total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        content()
        if (back != null) QuietButton(Texts.Common.BACK, back)
    }
}

@Composable
fun AboutStep(
    position: StepPosition,
    initial: Guardians,
    onContinue: (name: String, guardian: String, email: String, phone: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.ownerName) }
    var guardian by rememberSaveable { mutableStateOf(initial.firstGuardianName) }
    var email by rememberSaveable { mutableStateOf(initial.firstGuardianEmail) }
    var phone by rememberSaveable { mutableStateOf(initial.firstGuardianPhone) }
    val valid = name.isNotBlank() && guardian.isNotBlank() && email.contains('@') && email.substringAfter('@').contains('.')
    StepScreen(position, Texts.Setup.ABOUT_TITLE) {
        Field(name, { name = it }, Texts.Setup.YOUR_NAME, KeyboardType.Text)
        Body(Texts.Setup.GUARDIAN_BODY, centered = false)
        Field(guardian, { guardian = it }, Texts.Setup.GUARDIAN_NAME, KeyboardType.Text)
        Field(email, { email = it }, Texts.Setup.GUARDIAN_EMAIL, KeyboardType.Email)
        Field(phone, { phone = it }, Texts.Setup.GUARDIAN_PHONE, KeyboardType.Phone, last = true)
        BigButton(Texts.Common.CONTINUE, { onContinue(name, guardian, email, phone) }, enabled = valid)
    }
}

/** Picks a different guardian later. The owner decides; before saving she is asked to tell the previous guardian herself. */
@Composable
fun ChangeGuardianScreen(
    pictureId: String?,
    previousName: String,
    onSave: (name: String, email: String, phone: String) -> Unit,
) {
    var guardian by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var asking by rememberSaveable { mutableStateOf(false) }
    val valid = guardian.isNotBlank() && email.contains('@') && email.substringAfter('@').contains('.')
    AegidaScreen(pictureId) {
        Text(Texts.Settings.CHANGE_GUARDIAN_TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Body(Texts.Setup.GUARDIAN_BODY, centered = false)
        Field(guardian, { guardian = it }, Texts.Setup.GUARDIAN_NAME, KeyboardType.Text)
        Field(email, { email = it }, Texts.Setup.GUARDIAN_EMAIL, KeyboardType.Email)
        Field(phone, { phone = it }, Texts.Setup.GUARDIAN_PHONE, KeyboardType.Phone, last = true)
        // With no previous guardian there is nobody to let know.
        BigButton(Texts.Common.SAVE, { if (previousName.isBlank()) onSave(guardian, email, phone) else asking = true }, enabled = valid)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_TITLE.format(previousName), style = MaterialTheme.typography.titleLarge) },
            text = { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_BODY.format(previousName), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton({ asking = false; onSave(guardian, email, phone) }) {
                    Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_OK, style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton({ asking = false }) { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_BACK, style = MaterialTheme.typography.titleMedium) }
            },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType, last: Boolean = false) {
    // The keyboard's Next button moves to the box below; on the last box it closes the keyboard (Kevin, Oct 9).
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun AlertsStep(position: StepPosition, onAnswer: (Boolean) -> Unit) {
    StepScreen(position, Texts.Setup.ALERTS_TITLE) {
        Body(Texts.Setup.ALERTS_BODY, centered = false)
        BigButton(Texts.Setup.ALERTS_YES, { onAnswer(true) })
        QuietButton(Texts.Setup.ALERTS_NO, { onAnswer(false) })
    }
}

@Composable
fun FinishStep(pictureId: String?, onDone: () -> Unit) {
    AegidaScreen(pictureId) {
        Text(Texts.Setup.DONE_TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Body(Texts.Setup.DONE_BODY, centered = false)
        BigButton(Texts.Common.DONE, onDone)
    }
}
