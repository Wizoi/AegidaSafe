package com.aegida.app.ui.setup

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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.semantics { heading() },
            )
            Body(Texts.Setup.WELCOME_BODY)
            BigButton(Texts.Setup.WELCOME_START, onStart)
        }
    }
}

/** What the Back button on a first-run step does, or null when there is nothing before it. Set once by the setup flow. */
val LocalSetupBack = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** One first-run step: a title, a few plain sentences, then the one thing to do. */
@Composable
private fun StepScreen(
    pictureId: String?,
    number: Int,
    total: Int,
    title: String,
    content: @Composable () -> Unit,
) {
    val back = LocalSetupBack.current
    AegidaScreen(pictureId) {
        Text(Texts.Setup.STEP.format(number, total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        content()
        if (back != null) QuietButton(Texts.Common.BACK, back)
    }
}

@Composable
fun AboutStep(pictureId: String?, number: Int, total: Int, initial: Guardians, onContinue: (name: String, guardian: String, email: String, phone: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.ownerName) }
    var guardian by rememberSaveable { mutableStateOf(initial.firstGuardianName) }
    var email by rememberSaveable { mutableStateOf(initial.firstGuardianEmail) }
    var phone by rememberSaveable { mutableStateOf(initial.firstGuardianPhone) }
    val valid = name.isNotBlank() && guardian.isNotBlank() && email.contains('@') && email.substringAfter('@').contains('.')
    StepScreen(pictureId, number, total, Texts.Setup.ABOUT_TITLE) {
        Field(name, { name = it }, Texts.Setup.YOUR_NAME, KeyboardType.Text)
        Body(Texts.Setup.GUARDIAN_BODY, centered = false)
        Field(guardian, { guardian = it }, Texts.Setup.GUARDIAN_NAME, KeyboardType.Text)
        Field(email, { email = it }, Texts.Setup.GUARDIAN_EMAIL, KeyboardType.Email)
        Field(phone, { phone = it }, Texts.Setup.GUARDIAN_PHONE, KeyboardType.Phone)
        BigButton(Texts.Common.CONTINUE, { onContinue(name, guardian, email, phone) }, enabled = valid)
    }
}

/** Picks a different guardian later. The owner decides; before saving she is asked to tell the previous guardian herself. */
@Composable
fun ChangeGuardianScreen(pictureId: String?, previousName: String, onSave: (name: String, email: String, phone: String) -> Unit) {
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
        Field(phone, { phone = it }, Texts.Setup.GUARDIAN_PHONE, KeyboardType.Phone)
        // With no previous guardian there is nobody to let know.
        BigButton(Texts.Common.SAVE, { if (previousName.isBlank()) onSave(guardian, email, phone) else asking = true }, enabled = valid)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_TITLE.format(previousName), style = MaterialTheme.typography.titleLarge) },
            text = { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_BODY.format(previousName), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton({ asking = false; onSave(guardian, email, phone) }) { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_OK, style = MaterialTheme.typography.titleMedium) } },
            dismissButton = { TextButton({ asking = false }) { Text(Texts.Settings.CHANGE_GUARDIAN_POPUP_BACK, style = MaterialTheme.typography.titleMedium) } },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The ten pictures in a grid. Used in setup and when she changes her picture later. */
@Composable
fun PictureStep(pictureId: String?, number: Int, total: Int, chosen: SafetyPicture?, onChoose: (SafetyPicture) -> Unit, onContinue: () -> Unit, buttonText: String = Texts.Common.CONTINUE) {
    var picked by rememberSaveable { mutableStateOf(chosen?.id) }
    StepScreen(picked ?: pictureId, number, total, Texts.Setup.PICTURE_TITLE) {
        Body(Texts.Setup.PICTURE_BODY, centered = false)
        SafetyPicture.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { picture ->
                    val isPicked = picked == picture.id
                    Column(
                        Modifier.weight(1f).clickable { picked = picture.id; onChoose(picture) }
                            .semantics { contentDescription = picture.label; selected = isPicked },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PictureTile(picture, 96.dp, selected = isPicked)
                        Text(picture.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        BigButton(buttonText, onContinue, enabled = picked != null)
    }
}

@Composable
fun InviteStep(pictureId: String?, number: Int, total: Int, guardianName: String, noEmailApp: Boolean, onWrite: () -> Unit, onSent: () -> Unit) {
    var written by rememberSaveable { mutableStateOf(false) }
    StepScreen(pictureId, number, total, Texts.Setup.INVITE_TITLE) {
        Body(Texts.Setup.INVITE_BODY.format(guardianName), centered = false)
        BigButton(Texts.Setup.INVITE_WRITE, { written = true; onWrite() })
        if (noEmailApp) Body(Texts.Setup.INVITE_NO_EMAIL_APP, centered = false)
        QuietButton(Texts.Setup.INVITE_SENT, onSent, enabled = written)
    }
}

@Composable
fun AlertsStep(pictureId: String?, number: Int, total: Int, onAnswer: (Boolean) -> Unit) {
    StepScreen(pictureId, number, total, Texts.Setup.ALERTS_TITLE) {
        Body(Texts.Setup.ALERTS_BODY, centered = false)
        BigButton(Texts.Setup.ALERTS_YES, { onAnswer(true) })
        QuietButton(Texts.Setup.ALERTS_NO, { onAnswer(false) })
    }
}

@Composable
fun AccessStep(
    pictureId: String?, number: Int, total: Int,
    listenerOn: Boolean, bannersOn: Boolean,
    onOpenAccess: () -> Unit, onAllowBanners: () -> Unit, onContinue: () -> Unit,
) {
    // Skipping this step turns off Aegida's main job, so she is asked first (findings #65).
    var askingToSkip by rememberSaveable { mutableStateOf(false) }
    StepScreen(pictureId, number, total, Texts.Setup.ACCESS_TITLE) {
        Body(Texts.Setup.ACCESS_BODY, centered = false)
        Text(Texts.Setup.ACCESS_PART_1, style = MaterialTheme.typography.titleLarge)
        if (listenerOn) DoneRow(Texts.Setup.ACCESS_DONE) else {
            Body(Texts.Setup.ACCESS_STEP, centered = false)
            BigButton(Texts.Setup.ACCESS_OPEN, onOpenAccess)
        }
        Text(Texts.Setup.ACCESS_PART_2, style = MaterialTheme.typography.titleLarge)
        if (bannersOn) DoneRow(Texts.Setup.BANNERS_DONE) else {
            if (!listenerOn) Body(Texts.Setup.ACCESS_FIRST, centered = false)
            BigButton(Texts.Setup.ACCESS_ALLOW_BANNERS, onAllowBanners, enabled = listenerOn)
        }
        if (listenerOn && bannersOn) BigButton(Texts.Common.CONTINUE, onContinue) else QuietButton(Texts.Common.NOT_NOW, { askingToSkip = true })
    }
    if (askingToSkip) {
        AlertDialog(
            onDismissRequest = { askingToSkip = false },
            title = { Text(Texts.Setup.ACCESS_SKIP_TITLE, style = MaterialTheme.typography.titleLarge) },
            text = { Text(Texts.Setup.ACCESS_SKIP_BODY, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton({ askingToSkip = false; onContinue() }) { Text(Texts.Setup.ACCESS_SKIP_ANYWAY, style = MaterialTheme.typography.titleMedium) } },
            dismissButton = { TextButton({ askingToSkip = false }) { Text(Texts.Setup.ACCESS_SKIP_BACK, style = MaterialTheme.typography.titleMedium) } },
        )
    }
}

/** Website protection (Private DNS). Aegida cannot switch it; it opens the page, then reads the result. */
@Composable
fun FilterStep(pictureId: String?, number: Int, total: Int, filterOn: Boolean, onOpenSettings: () -> Unit, onCheck: () -> Unit, onContinue: () -> Unit) {
    StepScreen(pictureId, number, total, Texts.Setup.FILTER_TITLE) { FilterGuideBody(filterOn, onOpenSettings, onCheck, onContinue) }
}

@Composable
fun FilterGuideBody(filterOn: Boolean, onOpenSettings: () -> Unit, onCheck: () -> Unit, onContinue: () -> Unit, continueText: String = Texts.Common.CONTINUE) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (filterOn) {
            // Already on: nothing to explain, just say so and move on.
            DoneRow(Texts.Setup.FILTER_ON)
            BigButton(continueText, onContinue)
        } else {
            Body(Texts.Setup.FILTER_BODY, centered = false)
            Body(Texts.Setup.FILTER_STEPS.format(WebsiteFilter.HOST), centered = false)
            Body(Texts.Setup.FILTER_NOT_YET, centered = false)
            BigButton(Texts.Setup.FILTER_COPY_AND_OPEN, onOpenSettings)
            QuietButton(Texts.Setup.FILTER_CHECK, onCheck)
            QuietButton(Texts.Common.NOT_NOW, onContinue)
        }
    }
}

@Composable
fun BlockerStep(pictureId: String?, number: Int, total: Int, onShow: () -> Unit, onOn: () -> Unit, onNotNow: () -> Unit) {
    StepScreen(pictureId, number, total, Texts.Setup.BLOCKER_TITLE) {
        Body(Texts.Setup.BLOCKER_BODY, centered = false)
        BigButton(Texts.Setup.BLOCKER_SHOW, onShow)
        QuietButton(Texts.Setup.BLOCKER_IS_ON, onOn)
        QuietButton(Texts.Common.NOT_NOW, onNotNow)
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
