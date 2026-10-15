package com.aegida.app

import com.aegida.alerts.FindingActions
import com.aegida.app.ui.PictureBanner
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Problem
import com.aegida.testkit.FakeSetupStore
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * R5 as behaviour (findings #144): reading every installed app and the database is slow, so the ViewModel's work must never run on
 * the thread it was called from (the main thread on a phone), and the app list is read through one shared snapshotter and one shared
 * banner. Main is replaced by a dispatcher that is never advanced, so work that was launched on Main would never run and the test
 * would time out instead of passing. A Hilt graph test would need hilt-android-testing (a new dependency, Kevin's OK); the scope
 * of the two providers is read from the compiled module instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OffMainThreadTest {
    private val caller: Thread = Thread.currentThread()
    private val ranOn = ConcurrentHashMap<String, Thread>()

    @Before fun mainNeverRuns() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun restoreMain() = Dispatchers.resetMain()

    private fun note(what: String) {
        ranOn[what] = Thread.currentThread()
    }

    private fun viewModel(): MainViewModel {
        val loader = mockk<MainStateLoader>()
        coEvery { loader.load() } coAnswers { note("load"); MainState() }
        val actions = mockk<FindingActions>(relaxed = true)
        coEvery { actions.acknowledge(any()) } coAnswers { note("acknowledge"); true }
        return MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), actions, mockk(relaxed = true))
    }

    @Test fun everyActionTheViewModelStartsRunsOnAWorkerThreadNotTheCaller() {
        val vm = viewModel()
        val finding = Finding(FindingKey(DetectorId.Link, Problem.ScamLink, "m"), emptyMap(), Instant.now(), Instant.now())
        vm.refresh(runCheck = false)
        vm.acknowledge(finding)
        vm.answer { note("answer") }
        vm.edit { note("edit") }
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (ranOn.size < 4 && System.nanoTime() < until) Thread.sleep(10)
        assertEquals("work launched on Main never ran", setOf("load", "acknowledge", "answer", "edit"), ranOn.keys)
        ranOn.forEach { (what, thread) -> assertNotSame("$what ran on the calling thread", caller, thread) }
    }

    @Test fun anActionThatHandsBackAnAnswerAlsoRunsOffTheCallingThread() {
        val vm = viewModel()
        val thread = runBlocking { vm.editNow { Thread.currentThread() } }
        assertNotSame(caller, thread)
    }

    @Test fun thereIsOneSnapshotterAndOneBannerSoOneAppListCacheAndOnePicture() {
        val provided = com.aegida.app.di.AppModule::class.java.declaredClasses.single { it.simpleName == "Companion" }.declaredMethods
        for (name in listOf("androidSnapshotter", "pictureBanner")) {
            val provider = provided.single { it.name == name }
            assertTrue("$name must be a singleton", provider.isAnnotationPresent(Singleton::class.java))
        }
    }

    @Test fun savingThePictureTellsTheSharedBannerSoPostersNeedNoDatabaseRead() = runBlocking {
        val banner = mockk<PictureBanner>(relaxed = true)
        val commands = SetupCommands(FakeSetupStore(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), banner)
        commands.savePicture(SafetyPicture.Owl)
        verify { banner.set(SafetyPicture.Owl.id) }
    }
}
