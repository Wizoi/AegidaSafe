package com.aegida.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.aegida.app.ui.BigButton
import com.aegida.app.ui.Body
import com.aegida.app.ui.DoneRow
import com.aegida.app.ui.QuietButton
import com.aegida.core.model.WebsiteFilter
import com.aegida.core.text.Texts

@Composable
fun InviteStep(position: StepPosition, guardianName: String, noEmailApp: Boolean, onWrite: () -> Unit, onSent: () -> Unit) {
    var written by rememberSaveable { mutableStateOf(false) }
    StepScreen(position, Texts.Setup.INVITE_TITLE) {
        Body(Texts.Setup.INVITE_BODY.format(guardianName), centered = false)
        BigButton(Texts.Setup.INVITE_WRITE, { written = true; onWrite() })
        if (noEmailApp) Body(Texts.Setup.INVITE_NO_EMAIL_APP, centered = false)
        QuietButton(Texts.Setup.INVITE_SENT, onSent, enabled = written)
    }
}

/** What Aegida has read about its two permissions: the notification listener and the banners. */
data class AccessStatus(val listenerOn: Boolean, val bannersOn: Boolean)

@Composable
fun AccessStep(position: StepPosition, status: AccessStatus, onOpenAccess: () -> Unit, onAllowBanners: () -> Unit, onContinue: () -> Unit) {
    // Skipping this step turns off Aegida's main job, so she is asked first (findings #65).
    var askingToSkip by rememberSaveable { mutableStateOf(false) }
    StepScreen(position, Texts.Setup.ACCESS_TITLE) {
        Body(Texts.Setup.ACCESS_BODY, centered = false)
        Text(Texts.Setup.ACCESS_PART_1, style = MaterialTheme.typography.titleLarge)
        if (status.listenerOn) DoneRow(Texts.Setup.ACCESS_DONE) else {
            Body(Texts.Setup.ACCESS_STEP, centered = false)
            BigButton(Texts.Setup.ACCESS_OPEN, onOpenAccess)
        }
        Text(Texts.Setup.ACCESS_PART_2, style = MaterialTheme.typography.titleLarge)
        if (status.bannersOn) DoneRow(Texts.Setup.BANNERS_DONE) else {
            if (!status.listenerOn) Body(Texts.Setup.ACCESS_FIRST, centered = false)
            BigButton(Texts.Setup.ACCESS_ALLOW_BANNERS, onAllowBanners, enabled = status.listenerOn)
        }
        if (status.listenerOn && status.bannersOn) {
            BigButton(Texts.Common.CONTINUE, onContinue)
        } else {
            QuietButton(Texts.Common.NOT_NOW, { askingToSkip = true })
        }
    }
    if (askingToSkip) {
        AlertDialog(
            onDismissRequest = { askingToSkip = false },
            title = { Text(Texts.Setup.ACCESS_SKIP_TITLE, style = MaterialTheme.typography.titleLarge) },
            text = { Text(Texts.Setup.ACCESS_SKIP_BODY, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton({ askingToSkip = false; onContinue() }) {
                    Text(Texts.Setup.ACCESS_SKIP_ANYWAY, style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton({ askingToSkip = false }) { Text(Texts.Setup.ACCESS_SKIP_BACK, style = MaterialTheme.typography.titleMedium) }
            },
        )
    }
}

/** Website protection (Private DNS). Aegida cannot switch it; it opens the page, then reads the result. */
@Composable
fun FilterStep(position: StepPosition, filterOn: Boolean, onOpenSettings: () -> Unit, onCheck: () -> Unit, onContinue: () -> Unit) {
    StepScreen(position, Texts.Setup.FILTER_TITLE) { FilterGuideBody(filterOn, onOpenSettings, onCheck, onContinue) }
}

@Composable
fun FilterGuideBody(
    filterOn: Boolean,
    onOpenSettings: () -> Unit,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    continueText: String = Texts.Common.CONTINUE,
) {
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
fun BlockerStep(position: StepPosition, onShow: () -> Unit, onOn: () -> Unit, onNotNow: () -> Unit) {
    StepScreen(position, Texts.Setup.BLOCKER_TITLE) {
        Body(Texts.Setup.BLOCKER_BODY, centered = false)
        BigButton(Texts.Setup.BLOCKER_SHOW, onShow)
        QuietButton(Texts.Setup.BLOCKER_IS_ON, onOn)
        QuietButton(Texts.Common.NOT_NOW, onNotNow)
    }
}
