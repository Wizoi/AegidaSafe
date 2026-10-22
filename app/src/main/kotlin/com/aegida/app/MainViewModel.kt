package com.aegida.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aegida.alerts.CheckRunner
import com.aegida.alerts.DailyCheck
import com.aegida.alerts.FindingActions
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryRow
import com.aegida.core.health.Trigger
import com.aegida.core.model.Finding
import com.aegida.core.model.HomeState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.model.WebsiteFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** What the owner told Aegida is fine, listed in My settings. */
    val allowed: List<com.aegida.core.notice.AllowedItem> = emptyList(),
    /** The phone may have a maker feature worth asking about (Auto Blocker); false on a Pixel or the emulator. */
    val blockerRelevant: Boolean = false,
    /** When the scam lists on the phone were built (the feed's own date), or null before the first download. */
    val listsUpdatedAt: Instant? = null,
    /** Which reload this is ([MainViewModel.loadsStarted] when it started), so a tapped banner can wait for one that began after it (#165). */
    val loadNumber: Long = 0,
) {
    val filterOn: Boolean get() = snapshot?.privateDns == PrivateDns.Hostname(WebsiteFilter.HOST)
}

/**
 * Loads state and performs the owner's actions by calling the tested layers (CheckRunner, FindingActions, SetupCommands). It makes
 * no decisions of its own: what Home says is HomeState.of, what a fix does is Problem.fix. Every action runs on [work] and then reloads.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val loader: MainStateLoader,
    private val runner: CheckRunner,
    private val dailyCheck: DailyCheck,
    private val actions: FindingActions,
    private val setup: SetupCommands,
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

    private val loadCounter = AtomicLong()

    /** How many reloads have started; a state with a higher [MainState.loadNumber] was read after this was asked. */
    val loadsStarted: Long get() = loadCounter.get()

    private suspend fun load() {
        val number = loadCounter.incrementAndGet()
        val loaded = loader.load().copy(loadNumber = number)
        // A reload that started earlier but finished later never puts an older screen back.
        mutableState.update { if (it.loadNumber > number) it else loaded }
    }

    /** Marks a finding seen ("Got it", or after she has been to the screen that fixes it). */
    fun acknowledge(finding: Finding) {
        viewModelScope.launch(work) {
            actions.acknowledge(finding.key)
            load()
        }
    }

    /**
     * An answer to a question on a finding ("Yes, that was me", "This app is fine", taking an app off the list); a failure
     * leaves the screen as it was.
     */
    fun answer(block: suspend FindingActions.() -> Unit) = change { quietly { actions.block() } }

    /**
     * An answer whose result chooses the next page (a Yes to a website Aegida keeps hiding, findings #149); null if it failed. The work
     * runs in the view model's scope, so leaving the page (back) stops only the wait, never the answer half way.
     */
    suspend fun <T> answerNow(block: suspend FindingActions.() -> T): T? = viewModelScope.async(work) {
        val result = quietly { actions.block() }
        load()
        result
    }.await()

    /** A save in first-run setup or My settings; the block gets the state as it is when it runs. */
    fun edit(block: suspend SetupCommands.(MainState) -> Unit) = change { setup.block(mutableState.value) }

    /** A save that hands something back to build an invitation from (a new guardian, her private topic). */
    suspend fun <T> editNow(block: suspend SetupCommands.() -> T): T = withContext(work) {
        val result = setup.block()
        load()
        result
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch(work) {
            block()
            load()
        }
    }
}
