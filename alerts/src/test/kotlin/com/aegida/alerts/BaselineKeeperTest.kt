package com.aegida.alerts

import com.aegida.core.model.Baseline
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.SetupStep
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BaselineKeeperTest {
    private val phone = Snapshot(Instant.now(), PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent, accessibilityApps = setOf("app.screen"))
    private val setup = FakeSetupStore()
    private val baselines = FakeBaselines()
    private val keeper = BaselineKeeper(setup, baselines)

    @Test fun nothingIsKeptWhileSetupIsStillBeingDone() = runTest {
        keeper.keep(phone)
        assertNull(baselines.value)
    }

    @Test fun theFirstCheckAfterSetupKeepsWhatThePhoneLooksLikeAndLaterChecksNeverReplaceIt() = runTest {
        setup.value = Setup.NotDone.copy(stepsDone = SetupStep.entries.size)
        keeper.keep(phone)
        assertEquals(Baseline.of(phone), baselines.value)
        keeper.keep(phone.copy(browser = "org.other.browser", accessibilityApps = emptySet()))
        assertEquals(Baseline.of(phone), baselines.value)
    }

    @Test fun aBaselineFromBeforeAppsWereRecordedGetsThemOnceAndNothingElseChanges() = runTest {
        setup.value = Setup.NotDone.copy(stepsDone = SetupStep.entries.size)
        val old = Baseline.of(phone).copy(apps = null)
        baselines.value = old
        keeper.keep(phone) // no apps readable yet: leave it alone
        assertNull(baselines.value!!.apps)
        val withApps = phone.copy(
            browser = "org.other.browser",
            apps = listOf(
                com.aegida.core.model.InstalledApp("org.one", "One", "com.android.vending", false),
                com.aegida.core.model.InstalledApp("com.android.phone", "Phone", null, true),
            ),
        )
        keeper.keep(withApps)
        assertEquals(old.copy(apps = setOf("org.one")), baselines.value)
        keeper.keep(withApps.copy(apps = withApps.apps + com.aegida.core.model.InstalledApp("org.two", "Two", null, false)))
        assertEquals(setOf("org.one"), baselines.value!!.apps)
    }

    @Test fun theFirstBaselineStartsWithNoWebsitesSoEachIsAskedAboutOnce() = runTest { // findings #84
        setup.value = Setup.NotDone.copy(stepsDone = SetupStep.entries.size)
        keeper.keep(phone)
        assertEquals(emptySet<String>(), baselines.value!!.websites)
    }

    @Test fun aBaselineFromBeforeWebsitesWereRecordedGetsAnEmptyListOnce() = runTest { // Kevin, Oct 9
        setup.value = Setup.NotDone.copy(stepsDone = SetupStep.entries.size)
        val old = Baseline.of(phone).copy(websites = null)
        baselines.value = old
        keeper.keep(phone)
        assertEquals(old.copy(websites = emptySet()), baselines.value)
    }

    @Test fun aDefaultTheBaselineCouldNotReadAtSetupIsAdoptedOnceWhenItBecomesReadable() = runTest { // findings #70
        setup.value = Setup.NotDone.copy(stepsDone = SetupStep.entries.size)
        val old = Baseline.of(phone).copy(smsApp = null, assistant = null)
        baselines.value = old
        keeper.keep(phone.copy(smsApp = "org.texts", assistant = "org.assistant"))
        assertEquals(old.copy(smsApp = "org.texts", assistant = "org.assistant"), baselines.value)
        keeper.keep(phone.copy(smsApp = "org.other.texts", assistant = "org.other.assistant")) // a later change is drift, not adopted
        assertEquals(old.copy(smsApp = "org.texts", assistant = "org.assistant"), baselines.value)
    }
}
