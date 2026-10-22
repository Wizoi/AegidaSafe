package com.aegida.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aegida.app.MainState
import com.aegida.app.MainViewModel
import com.aegida.app.ui.setup.ChangeGuardianScreen
import com.aegida.app.ui.setup.FilterGuideBody
import com.aegida.app.ui.setup.PictureStep
import com.aegida.app.ui.setup.StepPosition
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Fix
import com.aegida.core.text.Texts
import com.aegida.platform.invite.InviteEmail
import com.aegida.platform.notify.FixActions
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun MainContent(state: MainState, vm: MainViewModel, asked: FindingKey? = null, onAsked: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var page by remember { mutableStateOf<Page>(Page.None) }
    // A tapped "Was that you?" banner: its question page, or Home when that warning is already closed (#164, Kevin Oct 22). Aegida may
    // be open with a screen from before the warning, so the request waits for a reload that started after the tap (#165).
    var waiting by remember { mutableStateOf<Pair<FindingKey, Long>?>(null) }
    LaunchedEffect(asked) {
        if (asked != null) {
            waiting = asked to vm.loadsStarted
            onAsked()
            vm.refresh(runCheck = false)
        }
    }
    LaunchedEffect(waiting, state.loadNumber) {
        val (key, since) = waiting ?: return@LaunchedEffect
        if (state.loadNumber > since) {
            waiting = null
            tab = Tab.Home
            page = questionPageFor(key, state.home)
        }
    }
    BackHandler(enabled = page != Page.None) { page = Page.None }
    MainFrame(tab, page == Page.None, { tab = it; page = Page.None }) {
        val current = page
        if (current == Page.None) TabContent(tab, state, vm) { page = it } else PageContent(current, state, vm) { page = it }
    }
}

@Composable
private fun TabContent(tab: Tab, state: MainState, vm: MainViewModel, show: (Page) -> Unit) {
    val context = LocalContext.current
    val shown = ShownAt(state.now, ZoneId.systemDefault(), Locale.getDefault())
    when (tab) {
        Tab.Home -> HomeScreen(
            state.home, state.setup.pictureId, shown,
            onFix = { fixFinding(it, state, vm, context, show) },
            onCall = { context.startSafely(FixActions.callGuardian(state.guardians.firstGuardianPhone)) },
        )
        Tab.History -> HistoryScreen(state.history, state.setup.pictureId, state.now, shown.zone, shown.locale)
        Tab.Settings -> SettingsTab(state, vm, shown, show)
    }
}

private fun fixFinding(finding: Finding, state: MainState, vm: MainViewModel, context: android.content.Context, show: (Page) -> Unit) {
    val problem = finding.key.problem
    val screen = FixActions.settingsIntentFor(finding, context.packageName)
    when (problem.fix) {
        Fix.Settings -> context.startSafely(screen)
        Fix.SettingsThenAcknowledge -> { context.startSafely(screen); vm.acknowledge(finding) }
        Fix.WebsiteFilterGuide -> show(Page.FilterGuide)
        Fix.Acknowledge -> show(Page.Advice(finding))
        Fix.AppNotices -> show(Page.NoisyApp(finding))
        Fix.ConfirmChange, Fix.ConfirmEvent -> show(Page.Confirm(finding))
        Fix.CallGuardian -> context.startSafely(FixActions.callGuardian(state.guardians.firstGuardianPhone))
        Fix.None -> Unit
    }
}

@Composable
private fun SettingsTab(state: MainState, vm: MainViewModel, shown: ShownAt, show: (Page) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsScreen(
        SettingsContent(state.setup, state.guardians, state.filterOn, state.listsUpdatedAt, shown.zone, shown.locale, state.allowed),
        SettingsActions(
            onChangePicture = { show(Page.ChangePicture) },
            onChangeGuardian = { show(Page.ChangeGuardian) },
            onResendInvite = {
                scope.launch {
                    // The same email as in setup, to the same guardian, for when the first one never arrived.
                    context.startSafely(InviteEmail.intent(vm.editNow { prepareInvite() }, InviteEmail.emailApps(context)))
                    vm.edit { resendWelcome() } // the email promises a first message (findings #95)
                }
            },
            onAlerts = { on -> vm.edit { setAlerts(on) } },
            onFilterHelp = { show(Page.FilterGuide) },
            onHowItWorks = { context.startSafely(FixActions.howItWorks()) },
            onStopAllowing = { item -> vm.answer { stopAllowing(item.problem, item.subject) } },
        ),
    )
}

@Composable
private fun PageContent(page: Page, state: MainState, vm: MainViewModel, show: (Page) -> Unit) {
    val pictureId = state.setup.pictureId
    when (page) {
        Page.None -> Unit
        Page.FilterGuide -> FilterGuidePage(state, vm, show)
        Page.ChangePicture -> PictureStep(
            StepPosition(pictureId, 0, 0), SafetyPicture.fromId(pictureId), { picture -> vm.edit { savePicture(picture) } },
            onContinue = { show(Page.None) }, buttonText = Texts.Common.SAVE,
        )
        Page.ChangeGuardian -> ChangeGuardianPage(state, vm, show)
        is Page.Confirm -> ConfirmPage(page.finding, pictureId, ShownAt(state.now, ZoneId.systemDefault(), Locale.getDefault()), vm, show)
        Page.KeptHidden -> KeptHiddenPage(pictureId) { show(Page.None) }
        is Page.NoisyApp -> NoisyAppScreen(page.finding, state, vm, show)
        is Page.Advice -> AdvicePage(
            pictureId, page.finding, ShownAt(state.now, ZoneId.systemDefault(), Locale.getDefault()),
            onGotIt = { vm.acknowledge(page.finding); show(Page.None) },
            onFine = { vm.answer { allow(page.finding.key) }; show(Page.None) },
        )
    }
}

@Composable
private fun FilterGuidePage(state: MainState, vm: MainViewModel, show: (Page) -> Unit) {
    val context = LocalContext.current
    AegidaScreen(state.setup.pictureId) {
        Headline(Texts.Setup.FILTER_TITLE)
        FilterGuideBody(
            state.filterOn,
            onOpenSettings = { context.openNetworkSettingsWithHost() },
            onCheck = { vm.refresh(runCheck = true) },
            onContinue = { vm.edit { rememberFilterIfOn(it.filterOn) }; show(Page.None) },
            continueText = Texts.Common.DONE,
        )
    }
}

@Composable
private fun ChangeGuardianPage(state: MainState, vm: MainViewModel, show: (Page) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ChangeGuardianScreen(state.setup.pictureId, state.guardians.firstGuardianName) { name, email, phone ->
        scope.launch {
            // The new guardian gets the same email as in setup, with their own new private link.
            val intent = InviteEmail.intent(vm.editNow { changeGuardian(name, email, phone) }, InviteEmail.emailApps(context))
            show(Page.None)
            context.startSafely(intent)
        }
    }
}

@Composable
private fun ConfirmPage(finding: Finding, pictureId: String?, shown: ShownAt, vm: MainViewModel, show: (Page) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Yes and No stay pinned above the tab bar; the words above them scroll (#171).
    AegidaScreen(
        pictureId,
        pinned = {
            BigButton(Texts.Home.CONFIRM_YES, {
                scope.launch { show(if (vm.answerNow { acceptChange(finding.key); keptHidden(finding.key) } == true) Page.KeptHidden else Page.None) }
            })
            QuietButton(Texts.Home.CONFIRM_NO, {
                context.startSafely(FixActions.settingsIntentFor(finding, context.packageName))
                // Something that happened once is never detected again, so "no" counts it as seen once she has been shown the switch.
                if (finding.key.problem.fix == Fix.ConfirmEvent) vm.acknowledge(finding)
                show(Page.None)
            })
        },
    ) {
        Headline(finding.key.problem.forMom)
        WhatItIsLines.of(finding, shown).forEach { Body(it) }
        Body(Texts.Home.CONFIRM_NO_HELP)
    }
}

@Composable
private fun NoisyAppScreen(finding: Finding, state: MainState, vm: MainViewModel, show: (Page) -> Unit) {
    val context = LocalContext.current
    val app = finding.key.subject
    val shown = ShownAt(state.now, ZoneId.systemDefault(), Locale.getDefault())
    NoisyAppPage(
        state.setup.pictureId, finding, shown,
        NoisyAppActions(
            onTurnOff = { context.startSafely(FixActions.appNotificationSettings(app)); vm.acknowledge(finding); show(Page.None) },
            onRemove = { context.startSafely(FixActions.appDetails(app)); vm.acknowledge(finding); show(Page.None) },
            onFine = { vm.answer { allow(finding.key) }; show(Page.None) },
            onGotIt = { vm.acknowledge(finding); show(Page.None) },
        ),
    )
}
