package com.aegida.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aegida.app.MainState
import com.aegida.app.MainViewModel
import com.aegida.app.ui.setup.AboutStep
import com.aegida.app.ui.setup.AccessStatus
import com.aegida.app.ui.setup.AccessStep
import com.aegida.app.ui.setup.AlertsStep
import com.aegida.app.ui.setup.BlockerStep
import com.aegida.app.ui.setup.FilterStep
import com.aegida.app.ui.setup.FinishStep
import com.aegida.app.ui.setup.InviteStep
import com.aegida.app.ui.setup.LocalSetupBack
import com.aegida.app.ui.setup.PictureStep
import com.aegida.app.ui.setup.StepPosition
import com.aegida.app.ui.setup.WelcomeScreen
import com.aegida.core.model.AutoBlockerAnswer
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.SetupStep
import com.aegida.platform.invite.InviteEmail
import com.aegida.platform.notify.FixActions
import kotlinx.coroutines.launch

@Composable
internal fun SetupFlow(state: MainState, vm: MainViewModel) {
    // Her first look at Aegida: say what it is before the first question. Skipped once she has started.
    var welcomed by rememberSaveable { mutableStateOf(state.setup.stepsDone > 0) }
    if (!welcomed) {
        WelcomeScreen(onStart = { welcomed = true })
        return
    }
    SetupSteps(state, vm)
}

/** What one first-run step needs from the flow around it. */
private class StepContext(
    val state: MainState,
    val vm: MainViewModel,
    val position: StepPosition,
    val advance: () -> Unit,
    val noEmailApp: MutableState<Boolean>,
)

@Composable
private fun SetupSteps(state: MainState, vm: MainViewModel) {
    var step by rememberSaveable { mutableStateOf(SetupStep.resumeAt(state.setup) ?: SetupStep.Finish) }
    val noEmailApp = rememberSaveable { mutableStateOf(false) }
    val total = if (state.blockerRelevant) SetupStep.Blocker.ordinal + 1 else SetupStep.Blocker.ordinal
    val advance: () -> Unit = {
        val finished = step // the save runs later on a background thread, after `step` has moved on (finding #123)
        vm.edit { finishStep(finished, it.blockerRelevant) }
        step.next(state.blockerRelevant)?.let { step = it }
    }
    val c = StepContext(state, vm, StepPosition(state.setup.pictureId, step.ordinal + 1, total), advance, noEmailApp)
    val back: (() -> Unit)? = step.previous(state.blockerRelevant)?.let { before -> { step = before } }
    CompositionLocalProvider(LocalSetupBack provides back) {
        when (step) {
            SetupStep.About -> AboutPage(c)
            SetupStep.Picture -> PicturePage(c)
            SetupStep.Invite -> InvitePage(c)
            SetupStep.Alerts -> AlertsStep(c.position) { yes ->
                vm.edit { setAlerts(yes) }
                advance()
            }
            SetupStep.Access -> AccessPage(c)
            SetupStep.Filter -> FilterPage(c)
            SetupStep.Blocker -> BlockerPage(c)
            SetupStep.Finish -> FinishStep(state.setup.pictureId) { advance() }
        }
    }
}

@Composable
private fun AboutPage(c: StepContext) {
    AboutStep(c.position, c.state.guardians) { name, guardian, email, phone ->
        c.vm.edit { saveAbout(name, guardian, email, phone) }
        c.advance()
    }
}

@Composable
private fun PicturePage(c: StepContext) {
    PictureStep(c.position, SafetyPicture.fromId(c.position.pictureId), { picture -> c.vm.edit { savePicture(picture) } }, onContinue = { c.advance() })
}

@Composable
private fun InvitePage(c: StepContext) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    InviteStep(c.position, c.state.guardians.firstGuardianName, c.noEmailApp.value, onWrite = {
        scope.launch {
            val intent = InviteEmail.intent(c.vm.editNow { prepareInvite() }, InviteEmail.emailApps(context))
            if (!context.startSafely(intent)) c.noEmailApp.value = true
        }
    }, onSent = { c.advance() })
}

@Composable
private fun AccessPage(c: StepContext) {
    val context = LocalContext.current
    val bannerPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.openNotificationSettings()
        c.vm.refresh(runCheck = false)
    }
    val snapshot = c.state.snapshot
    AccessStep(
        c.position,
        AccessStatus(listenerOn = snapshot?.listenerEnabled == true, bannersOn = snapshot?.canPostNotifications == true),
        onOpenAccess = { context.startSafely(FixActions.notificationAccess()) },
        onAllowBanners = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bannerPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
        onContinue = { c.advance() },
    )
}

@Composable
private fun FilterPage(c: StepContext) {
    val context = LocalContext.current
    FilterStep(
        c.position, c.state.filterOn,
        onOpenSettings = { context.openNetworkSettingsWithHost() },
        onCheck = { c.vm.refresh(runCheck = false) },
        onContinue = {
            c.vm.edit { rememberFilterIfOn(it.filterOn) }
            c.advance()
        },
    )
}

@Composable
private fun BlockerPage(c: StepContext) {
    val context = LocalContext.current
    BlockerStep(
        c.position,
        onShow = { context.startSafely(FixActions.securitySettings()) },
        onOn = { c.vm.edit { answerBlocker(AutoBlockerAnswer.UserSaidOn) }; c.advance() },
        onNotNow = { c.vm.edit { answerBlocker(AutoBlockerAnswer.NotNow) }; c.advance() },
    )
}
