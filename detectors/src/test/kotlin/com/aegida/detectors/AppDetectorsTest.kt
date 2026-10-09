package com.aegida.detectors

import com.aegida.core.model.Baseline
import com.aegida.core.model.InstalledApp
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDetectorsTest {
    private fun app(pkg: String, installer: String? = InstalledApp.PLAY_STORE, system: Boolean = false) =
        InstalledApp(pkg, pkg.substringAfterLast('.'), installer, system)

    private val atSetup = listOf(app("org.keep.one"), app("com.android.settings", installer = null, system = true))
    private val baseline = Baseline.of(snapshot().copy(apps = atSetup))

    private fun newApps(apps: List<InstalledApp>, base: Baseline? = baseline) =
        NewAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = apps)).copy(baseline = base)).map { it.key.problem to it.key.subject }

    private fun remote(apps: List<InstalledApp>, base: Baseline? = baseline) =
        RemoteControlDetector().evaluate(inputs(snapshot = snapshot().copy(apps = apps)).copy(baseline = base)).map { it.key.problem to it.key.subject }

    @Test fun nothingIsRaisedBeforeThereIsABaseline() {
        val sideloaded = atSetup + app("org.new.sideloaded", installer = null)
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(sideloaded, base = null))
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(sideloaded, base = baseline.copy(apps = null)))
        assertEquals(emptyList<Pair<Problem, String>>(), remote(atSetup + app("com.anydesk.anydeskandroid"), base = null))
    }

    @Test fun anAppInstalledFromOutsideThePlayStoreAfterSetupIsRaised() {
        assertEquals(
            listOf(Problem.NewAppOutsidePlay to "org.new.by.hand", Problem.NewAppOutsidePlay to "org.new.other.store"),
            newApps(atSetup + app("org.new.other.store", installer = "org.some.store") + app("org.new.by.hand", installer = null)),
        )
    }

    @Test fun newAppsFromPlayAppsThatWereThereAndAppsThatShippedWithThePhoneAreLeftAlone() {
        val phone = atSetup + app("org.new.fromplay") + app("com.android.newsystem", installer = null, system = true) + app("org.keep.one", installer = null)
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(phone))
    }

    @Test fun aRemoteControlAppIsRaisedFromPlayOrNotUntilSheSaysItIsHers() {
        val phone = atSetup + app("com.anydesk.anydeskandroid") + app("com.teamviewer.quicksupport.market", installer = null)
        assertEquals(
            listOf(Problem.RemoteControlApp to "com.anydesk.anydeskandroid", Problem.RemoteControlApp to "com.teamviewer.quicksupport.market"),
            remote(phone),
        )
        val accepted = baseline.copy(acceptedRemoteApps = setOf("com.anydesk.anydeskandroid"))
        assertEquals(listOf(Problem.RemoteControlApp to "com.teamviewer.quicksupport.market"), remote(phone, accepted))
    }

    @Test fun aRemoteControlAppIsOnlyRaisedOnceNotAlsoAsANewApp() {
        val phone = atSetup + app("com.anydesk.anydeskandroid", installer = null)
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(phone))
        assertEquals(1, remote(phone).size)
    }

    @Test fun sayingAnAppWasHersMovesTheRightPart() {
        val phone = snapshot().copy(apps = atSetup + app("org.new.by.hand", installer = null) + app("com.anydesk.anydeskandroid"))
        assertEquals(setOf("org.keep.one", "org.new.by.hand", "com.anydesk.anydeskandroid"), baseline.accepting(Problem.NewAppOutsidePlay, phone).apps)
        assertEquals(setOf("org.keep.one"), baseline.accepting(Problem.RemoteControlApp, phone).apps)
        assertEquals(setOf("com.anydesk.anydeskandroid"), baseline.accepting(Problem.RemoteControlApp, phone).acceptedRemoteApps)
    }

    @Test fun theBaselineKeepsUserAppsOnly() = assertEquals(setOf("org.keep.one"), baseline.apps)
}
