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
}
