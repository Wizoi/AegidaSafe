package com.aegida.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegida.alerts.CheckRunner
import com.aegida.app.ui.PictureBanner
import com.aegida.alerts.DailyCheck
import com.aegida.alerts.FindingActions
import com.aegida.alerts.GuardianAlerts
import com.aegida.alerts.GuardianChange
import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.HistoryRow
import com.aegida.core.alert.HistoryRows
import com.aegida.core.health.CheckLog
import com.aegida.core.health.Trigger
import com.aegida.core.model.AutoBlockerAnswer
import com.aegida.core.model.Finding
import com.aegida.core.model.HomeState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.Setup
import com.aegida.core.model.SetupStep
import com.aegida.core.model.Snapshot
import com.aegida.core.model.Snapshotter
import com.aegida.core.model.WebsiteFilter
import com.aegida.core.net.NtfyTopic
import com.aegida.core.oem.OemSource
import com.aegida.core.oem.Reading
import com.aegida.core.store.SetupStore
import com.aegida.core.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Everything the screens draw, read in one go so a screen never shows half of an old state. */
data class MainState(
    val loaded: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val setup: Setup = Setup.NotDone,
    val guardians: Guardians = Guardians.None,
    val home: HomeState = HomeState.CouldntCheck(null),
    val history: List<HistoryRow> = emptyList(),
    val snapshot: Snapshot? = null,
    /** The phone may have a maker feature worth asking about (Auto Blocker); false on a Pixel or the emulator. */
    val blockerRelevant: Boolean = false,
) {
    val filterOn: Boolean get() = snapshot?.privateDns == PrivateDns.Hostname(WebsiteFilter.HOST)
}

/**
 * Loads state and performs the owner's actions by calling the tested layers (CheckRunner, FindingActions, the
 * stores). It makes no decisions of its own: what Home says is HomeState.of, what a fix does is Problem.fix.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val setupStore: SetupStore,
    private val guardianSettings: GuardianSettings,
    private val findings: FindingStore,
    private val historyLog: HistoryLog,
    private val checkLog: CheckLog,
    private val snapshotter: Snapshotter,
    private val runner: CheckRunner,
    private val dailyCheck: DailyCheck,
    private val guardianAlerts: GuardianAlerts,
    private val guardianChange: GuardianChange,
    private val actions: FindingActions,
    private val oem: OemSource,
    private val clock: Clock,
    private val banner: PictureBanner,
) : ViewModel() {

    /**
     * Where the work runs. Reading every installed app and the database is slow on an old phone, so none of it
     * may run on the main thread (viewModelScope is Main) (R5). Tests may replace it.
     */
    internal var work: CoroutineDispatcher = Dispatchers.Default

    private val mutableState = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = mutableState.asStateFlow()

    /** Runs a check (so Home is current when she opens the app or comes back from Settings), then reloads. */
    fun refresh(runCheck: Boolean = true) {
        viewModelScope.launch(work) {
            if (runCheck) quietly { runner.run(Trigger.AppStart) }
            load()
            // First launch: do the first full check now (feed download included) and show its result.
            if (runCheck && !firstDailyTried) {
                firstDailyTried = true
                if (quietly { dailyCheck.runIfNeverRun() } != null) load()
            }
        }
    }

    private var firstDailyTried = false

    /** A failed check must not break the screen: it still shows what is stored, and HealthDetector reports a check that keeps failing. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun load() {
        val now = clock.now()
        val snapshot = snapshotter.take()
        val setup = setupStore.setup()
        mutableState.value = MainState(
            loaded = true,
            now = now,
            setup = setup,
            guardians = guardianSettings.current(),
            home = HomeState.of(findings.open(), checkLog.latest()),
            history = HistoryRows.of(historyLog.since(now.minus(HISTORY_SHOWN))),
            snapshot = snapshot,
            blockerRelevant = oem.installBlocker() != Reading.NotPresent,
        )
    }

    /** "Yes, that was me" to a was-that-you question: the baseline takes the change, and Home stops asking. */
    fun acceptChange(finding: Finding) {
        viewModelScope.launch(work) {
            quietly { actions.acceptChange(finding.key) }
            load()
        }
    }

    /** Marks a finding seen ("Got it", or after she has been to the screen that fixes it). */
    fun acknowledge(finding: Finding) {
        viewModelScope.launch(work) {
            actions.acknowledge(finding.key)
            load()
        }
    }

    // First-run setup and My settings. Each save writes through the store, then reloads.

    fun saveAbout(name: String, guardianName: String, email: String, phone: String) = change {
        val current = guardianSettings.current()
        guardianSettings.save(
            current.copy(ownerName = name.trim(), firstGuardianName = guardianName.trim(), firstGuardianEmail = email.trim(), firstGuardianPhone = phone.trim()),
        )
    }

    fun savePicture(picture: SafetyPicture) = change {
        setupStore.save(setupStore.setup().copy(pictureId = picture.id))
        banner.set(picture.id) // banners posted from now on carry the new picture, without reading the database
    }

    /** Makes her private alert topic the first time it is needed and returns the guardian settings to build the email from. */
    suspend fun prepareInvite(): Guardians = withContext(work) {
        val current = guardianSettings.current()
        if (current.firstTopic != null) return@withContext current
        val made = current.copy(firstTopic = NtfyTopic.generate())
        guardianSettings.save(made)
        load()
        made
    }

    /** Changes the guardian (the owner decides; nothing is sent to the old one). Returns the new settings to build the invite from. */
    suspend fun changeGuardian(name: String, email: String, phone: String): Guardians = withContext(work) {
        quietly { guardianChange.change(name, email, phone) }
        load()
        guardianSettings.current()
    }

    fun setAlerts(on: Boolean) = change { quietly { guardianAlerts.setAlertsOn(on) } }

    fun answerBlocker(answer: AutoBlockerAnswer) = change { setupStore.save(setupStore.setup().copy(autoBlocker = answer)) }

    /** Remembers the filter she set up, but only once Aegida has read it back as on. */
    fun rememberFilterIfOn() = change {
        if (mutableState.value.filterOn) setupStore.save(setupStore.setup().copy(websiteFilterHost = WebsiteFilter.HOST))
    }

    /** Marks [step] finished. The Samsung step is passed over on a phone that cannot have it. */
    fun finishStep(step: SetupStep) = change {
        val setup = setupStore.setup()
        val next = step.next(mutableState.value.blockerRelevant)
        val stepsDone = if (next == null) SetupStep.entries.size else maxOf(setup.stepsDone, next.ordinal)
        setupStore.save(setup.copy(stepsDone = stepsDone))
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch(work) {
            block()
            load()
        }
    }

    private companion object {
        val HISTORY_SHOWN: Duration = Duration.ofDays(90)
    }
}
