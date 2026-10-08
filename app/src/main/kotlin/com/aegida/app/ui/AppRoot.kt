package com.aegida.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aegida.app.MainState
import com.aegida.app.MainViewModel
import com.aegida.app.ui.setup.AboutStep
import com.aegida.app.ui.setup.ChangeGuardianScreen
import com.aegida.app.ui.setup.AccessStep
import com.aegida.app.ui.setup.AlertsStep
import com.aegida.app.ui.setup.BlockerStep
import com.aegida.app.ui.setup.FilterGuideBody
import com.aegida.app.ui.setup.FilterStep
import com.aegida.app.ui.setup.FinishStep
import com.aegida.app.ui.setup.InviteStep
import com.aegida.app.ui.setup.PictureStep
import com.aegida.core.model.AutoBlockerAnswer
import com.aegida.core.model.Finding
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.SetupStep
import com.aegida.core.text.Fix
import com.aegida.core.text.Texts
import com.aegida.platform.invite.InviteEmail
import com.aegida.platform.notify.FixActions
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

/** The whole app: first-run setup until it is finished, then Home, History and My settings. */
@Composable
fun AppRoot(vm: MainViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Coming back from a Settings screen: look at the phone again so what she sees is current.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    when {
        !state.loaded -> AegidaScreen(null) {}
        !state.setup.done -> Box(Modifier.navigationBarsPadding()) { SetupFlow(state, vm) }
        else -> MainContent(state, vm)
    }
}

private fun Context.startSafely(intent: Intent?) {
    if (intent == null) return
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // A phone without that screen: nothing to open, and the owner is not shown an error she cannot act on.
    }
}

@Composable
private fun SetupFlow(state: MainState, vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(SetupStep.resumeAt(state.setup) ?: SetupStep.Finish) }
    var noEmailApp by rememberSaveable { mutableStateOf(false) }
    val pictureId = state.setup.pictureId
    val total = if (state.blockerRelevant) SetupStep.Blocker.ordinal + 1 else SetupStep.Blocker.ordinal
    val number = step.ordinal + 1

    fun advance() {
        vm.finishStep(step)
        step.next(state.blockerRelevant)?.let { step = it }
    }

    val bannerPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            context.startSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        vm.refresh(runCheck = false)
    }

    when (step) {
        SetupStep.About -> AboutStep(pictureId, number, total, state.guardians) { name, guardian, email, phone ->
            vm.saveAbout(name, guardian, email, phone)
            advance()
        }
        SetupStep.Picture -> PictureStep(pictureId, number, total, SafetyPicture.fromId(pictureId), vm::savePicture, onContinue = { advance() })
        SetupStep.Invite -> InviteStep(pictureId, number, total, state.guardians.firstGuardianName, noEmailApp, onWrite = {
            scope.launch {
                val intent = InviteEmail.intent(vm.prepareInvite())
                try {
                    if (intent != null) context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    noEmailApp = true
                }
            }
        }, onSent = { advance() })
        SetupStep.Alerts -> AlertsStep(pictureId, number, total) { yes ->
            vm.setAlerts(yes)
            advance()
        }
        SetupStep.Access -> AccessStep(
            pictureId, number, total,
            listenerOn = state.snapshot?.listenerEnabled == true,
            bannersOn = state.snapshot?.canPostNotifications == true,
            onOpenAccess = { context.startSafely(FixActions.notificationAccess()) },
            onAllowBanners = {
                if (Build.VERSION.SDK_INT >= 33) bannerPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onContinue = { advance() },
        )
        SetupStep.Filter -> FilterStep(
            pictureId, number, total, state.filterOn,
            onOpenSettings = { context.startSafely(FixActions.networkSettings()) },
            onCheck = { vm.refresh(runCheck = false) },
            onContinue = { vm.rememberFilterIfOn(); advance() },
        )
        SetupStep.Blocker -> BlockerStep(
            pictureId, number, total,
            onShow = { context.startSafely(FixActions.securitySettings()) },
            onOn = { vm.answerBlocker(AutoBlockerAnswer.UserSaidOn); advance() },
            onNotNow = { vm.answerBlocker(AutoBlockerAnswer.NotNow); advance() },
        )
        SetupStep.Finish -> FinishStep(pictureId) { advance() }
    }
}

private enum class Tab(val label: String) {
    Home(Texts.Nav.HOME), History(Texts.Nav.HISTORY), Settings(Texts.Nav.SETTINGS)
}

/** A page opened from Home or Settings; back returns to the tab. */
private sealed interface Page {
    data object None : Page
    data object FilterGuide : Page
    data object ChangePicture : Page
    data object ChangeGuardian : Page
    data class Advice(val finding: Finding) : Page
}

@Composable
private fun MainContent(state: MainState, vm: MainViewModel) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var page by remember { mutableStateOf<Page>(Page.None) }
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val pictureId = state.setup.pictureId
    BackHandler(enabled = page != Page.None) { page = Page.None }

    fun fix(finding: Finding) {
        val problem = finding.key.problem
        val screen = FixActions.settingsIntentFor(finding, context.packageName)
        when (problem.fix) {
            Fix.Settings -> context.startSafely(screen)
            Fix.SettingsThenAcknowledge -> { context.startSafely(screen); vm.acknowledge(finding) }
            Fix.WebsiteFilterGuide -> page = Page.FilterGuide
            Fix.Acknowledge -> page = Page.Advice(finding)
            Fix.CallGuardian -> context.startSafely(FixActions.callGuardian(state.guardians.firstGuardianPhone))
            Fix.None -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // AegidaScreen adds the status bar space; the bar handles its own
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry && page == Page.None,
                        onClick = { tab = entry; page = Page.None },
                        icon = { TabIcon(entry) },
                        // At very large text "My settings" wraps; give every label two lines then, so the icons stay level.
                        label = {
                            Text(
                                entry.label,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                minLines = if (LocalDensity.current.fontScale > 1.4f) 2 else 1,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val current = page) {
                Page.None -> when (tab) {
                    Tab.Home -> HomeScreen(state.home, pictureId, state.now, zone, locale, onFix = ::fix, onCall = { context.startSafely(FixActions.callGuardian(state.guardians.firstGuardianPhone)) })
                    Tab.History -> HistoryScreen(state.history, pictureId, state.now, zone, locale)
                    Tab.Settings -> SettingsScreen(
                        state.setup, state.guardians, state.filterOn,
                        onChangePicture = { page = Page.ChangePicture },
                        onChangeGuardian = { page = Page.ChangeGuardian },
                        onAlerts = vm::setAlerts,
                        onFilterHelp = { page = Page.FilterGuide },
                    )
                }
                Page.FilterGuide -> AegidaScreen(pictureId) {
                    Headline(Texts.Setup.FILTER_TITLE)
                    FilterGuideBody(
                        state.filterOn,
                        onOpenSettings = { context.startSafely(FixActions.networkSettings()) },
                        onCheck = { vm.refresh(runCheck = true) },
                        onContinue = { vm.rememberFilterIfOn(); page = Page.None },
                        continueText = Texts.Common.DONE,
                    )
                }
                Page.ChangePicture -> PictureStep(pictureId, 0, 0, SafetyPicture.fromId(pictureId), vm::savePicture, onContinue = { page = Page.None }, buttonText = Texts.Common.SAVE)
                Page.ChangeGuardian -> ChangeGuardianScreen(pictureId, state.guardians.firstGuardianName) { name, email, phone ->
                    scope.launch {
                        // The new guardian gets the same email as in setup, with their own new private link.
                        val intent = InviteEmail.intent(vm.changeGuardian(name, email, phone))
                        page = Page.None
                        context.startSafely(intent)
                    }
                }
                is Page.Advice -> AegidaScreen(pictureId) {
                    Headline(current.finding.key.problem.forMom)
                    Body(Texts.Home.SCAM_ADVICE)
                    BigButton(Texts.Home.GOT_IT, { vm.acknowledge(current.finding); page = Page.None })
                }
            }
        }
    }
}

/** Three plain icons drawn in code: a house, a clock, and three sliders. */
@Composable
private fun TabIcon(tab: Tab) {
    val colour = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(28.dp)) {
        val s = size.minDimension
        val line = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        when (tab) {
            Tab.Home -> {
                drawPath(
                    Path().apply {
                        moveTo(s * 0.12f, s * 0.48f); lineTo(s * 0.5f, s * 0.14f); lineTo(s * 0.88f, s * 0.48f)
                        moveTo(s * 0.22f, s * 0.42f); lineTo(s * 0.22f, s * 0.86f); lineTo(s * 0.78f, s * 0.86f); lineTo(s * 0.78f, s * 0.42f)
                    },
                    colour, style = line,
                )
            }
            Tab.History -> {
                drawCircle(colour, s * 0.4f, center, style = line)
                drawLine(colour, center, Offset(s * 0.5f, s * 0.24f), line.width, StrokeCap.Round)
                drawLine(colour, center, Offset(s * 0.68f, s * 0.58f), line.width, StrokeCap.Round)
            }
            Tab.Settings -> listOf(0.25f, 0.5f, 0.75f).forEachIndexed { i, y ->
                drawLine(colour, Offset(s * 0.12f, s * y), Offset(s * 0.88f, s * y), line.width, StrokeCap.Round)
                drawCircle(colour, s * 0.09f, Offset(s * listOf(0.65f, 0.35f, 0.6f)[i], s * y))
            }
        }
    }
}
