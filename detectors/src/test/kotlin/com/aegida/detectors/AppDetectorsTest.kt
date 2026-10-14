package com.aegida.detectors

import com.aegida.core.model.Baseline
import com.aegida.core.model.InstalledApp
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.snapshot
import com.aegida.testkit.GALAXY_STORE
import com.aegida.testkit.aGalaxyStoreApp
import com.aegida.testkit.aPlayApp
import com.aegida.testkit.aSystemApp
import com.aegida.testkit.anApp
import com.aegida.testkit.anAppOpenedFromAFile
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDetectorsTest {
    private val atSetup = listOf(aPlayApp("org.keep.one"), aSystemApp("com.android.settings"))
    private val baseline = Baseline.of(snapshot().copy(apps = atSetup))

    private fun newApps(apps: List<InstalledApp>, base: Baseline? = baseline) =
        NewAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = apps)).copy(baseline = base)).map { it.key.problem to it.key.subject }

    private fun remote(apps: List<InstalledApp>, base: Baseline? = baseline) =
        RemoteControlDetector().evaluate(inputs(snapshot = snapshot().copy(apps = apps)).copy(baseline = base)).map { it.key.problem to it.key.subject }

    @Test fun nothingIsRaisedBeforeThereIsABaseline() {
        val sideloaded = atSetup + anAppOpenedFromAFile("org.new.sideloaded")
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(sideloaded, base = null))
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(sideloaded, base = baseline.copy(apps = null)))
        assertEquals(emptyList<Pair<Problem, String>>(), remote(atSetup + aPlayApp("com.anydesk.anydeskandroid"), base = null))
    }

    @Test fun anAppInstalledFromOutsideThePlayStoreAfterSetupIsRaised() {
        assertEquals(
            listOf(Problem.NewAppOutsidePlay to "org.new.by.hand", Problem.NewAppOutsidePlay to "org.new.other.store"),
            newApps(atSetup + anApp("org.new.other.store", installer = "org.some.store") + anAppOpenedFromAFile("org.new.by.hand")),
        )
    }

    @Test fun newAppsFromPlayAppsThatWereThereAndAppsThatShippedWithThePhoneAreLeftAlone() {
        val phone = atSetup + aPlayApp("org.new.fromplay") + aSystemApp("com.android.newsystem") + anAppOpenedFromAFile("org.keep.one")
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(phone))
    }

    @Test fun aRemoteControlAppIsRaisedFromPlayOrNotUntilSheSaysItIsHers() {
        val phone = atSetup + aPlayApp("com.anydesk.anydeskandroid") + anAppOpenedFromAFile("com.teamviewer.quicksupport.market")
        assertEquals(
            listOf(Problem.RemoteControlApp to "com.anydesk.anydeskandroid", Problem.RemoteControlApp to "com.teamviewer.quicksupport.market"),
            remote(phone),
        )
        val accepted = baseline.copy(acceptedRemoteApps = setOf("com.anydesk.anydeskandroid"))
        assertEquals(listOf(Problem.RemoteControlApp to "com.teamviewer.quicksupport.market"), remote(phone, accepted))
    }

    @Test fun aRemoteControlAppIsOnlyRaisedOnceNotAlsoAsANewApp() {
        val phone = atSetup + anAppOpenedFromAFile("com.anydesk.anydeskandroid")
        assertEquals(emptyList<Pair<Problem, String>>(), newApps(phone))
        assertEquals(1, remote(phone).size)
    }

    @Test fun sayingAnAppWasHersMovesOnlyThatApp() { // findings #50
        val phone = snapshot().copy(
            apps = atSetup + anAppOpenedFromAFile("org.new.by.hand") + anAppOpenedFromAFile("org.new.spy") +
                aPlayApp("com.anydesk.anydeskandroid") + aPlayApp("com.carriez.flutter_hbb"),
        )
        assertEquals(setOf("org.keep.one", "org.new.by.hand"), baseline.accepting(Problem.NewAppOutsidePlay, "org.new.by.hand", phone).apps)
        assertEquals(setOf("org.keep.one"), baseline.accepting(Problem.RemoteControlApp, "com.anydesk.anydeskandroid", phone).apps)
        assertEquals(setOf("com.anydesk.anydeskandroid"), baseline.accepting(Problem.RemoteControlApp, "com.anydesk.anydeskandroid", phone).acceptedRemoteApps)
        assertEquals(baseline, baseline.accepting(Problem.NewAppOutsidePlay, "org.not.installed", phone))
        // the second new app is still a question after the first was accepted
        val afterFirst = baseline.accepting(Problem.NewAppOutsidePlay, "org.new.by.hand", phone)
        assertEquals(listOf(Problem.NewAppOutsidePlay to "org.new.spy"), newApps(phone.apps, afterFirst))
    }

    @Test fun theBaselineKeepsUserAppsOnly() = assertEquals(setOf("org.keep.one"), baseline.apps)

    @Test fun aKnownSpyingAppIsOneWarningNotAlsoANewAppQuestion() {
        val feed = com.aegida.core.feed.ThreatFeed.of(
            Fixtures.now, emptyList(), emptyList(), emptyList(),
            apps = com.aegida.core.feed.AppKeys(
                packageNames = listOf("com.thetruth"), certificateSha1s = listOf("31a6ececd97cf39bc4126b8745cd94a7c30bf81c"),
            ),
        )
        val renamed = anAppOpenedFromAFile("org.renamed").copy(certSha1s = setOf("31a6ececd97cf39bc4126b8745cd94a7c30bf81c"))
        val phone = atSetup + anAppOpenedFromAFile("com.thetruth") + renamed + anAppOpenedFromAFile("org.plain.sideload")
        val found = NewAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = phone), feed = feed).copy(baseline = baseline)).map { it.key.subject }
        assertEquals(listOf("org.plain.sideload"), found)
    }

    // Maker's own store (findings #106, scenarios GS*): the Galaxy Store, as the Samsung phone read it.
    private val store = GALAXY_STORE

    private fun newAppsOn(apps: List<InstalledApp>, stores: Set<String>, base: Baseline? = baseline) =
        NewAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = apps, makerStores = stores)).copy(baseline = base))
            .map { it.key.problem to it.key.subject }

    @Test fun anAppInstalledFromTheMakersStoreIsNotAQuestion() { // GS1
        val phone = atSetup + aGalaxyStoreApp("org.new.temu") + aGalaxyStoreApp("org.new.pdf")
        assertEquals(emptyList<Pair<Problem, String>>(), newAppsOn(phone, setOf(store)))
    }

    @Test fun anAppFromTheMakersStoreThatSheAlreadyAnsweredStaysQuiet() { // GS3
        val phone = snapshot().copy(apps = atSetup + anAppOpenedFromAFile("org.new.temu"), makerStores = setOf(store))
        val answered = baseline.accepting(Problem.NewAppOutsidePlay, "org.new.temu", phone)
        assertEquals(emptyList<Pair<Problem, String>>(), newAppsOn(atSetup + aGalaxyStoreApp("org.new.temu"), setOf(store), answered))
    }

    @Test fun aRemoteControlAppFromTheMakersStoreIsStillRaised() = // GS4
        assertEquals(
            listOf(Problem.RemoteControlApp to "com.anydesk.anydeskandroid", Problem.RemoteControlApp to "com.teamviewer.quicksupport.market"),
            RemoteControlDetector().evaluate(
                inputs(snapshot = snapshot().copy(
                        apps = atSetup + aGalaxyStoreApp("com.anydesk.anydeskandroid") + aGalaxyStoreApp("com.teamviewer.quicksupport.market"),
                        makerStores = setOf(store),
                    )).copy(baseline = baseline),
            ).map { it.key.problem to it.key.subject },
        )

    @Test fun anAppThatSaysItCameFromTheMakersStoreButWasAskedForByAnotherInstallerIsAQuestion() { // GS6
        val phone = atSetup + aGalaxyStoreApp("org.preloaded.pusher", initiating = "org.preloaded.installer") +
            aGalaxyStoreApp("org.no.initiator", initiating = null)
        assertEquals(
            listOf(Problem.NewAppOutsidePlay to "org.no.initiator", Problem.NewAppOutsidePlay to "org.preloaded.pusher"),
            newAppsOn(phone, setOf(store)),
        )
    }

    @Test fun aFileOpenedByHandOnAPhoneWithAMakerStoreIsStillAQuestion() { // GS7
        val phone = atSetup + anAppOpenedFromAFile("org.new.file") +
            anApp("org.new.pkg", installer = "com.google.android.packageinstaller", initiating = "com.google.android.apps.messaging")
        assertEquals(
            listOf(Problem.NewAppOutsidePlay to "org.new.file", Problem.NewAppOutsidePlay to "org.new.pkg"),
            newAppsOn(phone, setOf(store)),
        )
    }

    @Test fun anAppFromAnyOtherStoreIsStillAQuestion() { // GS8
        val phone = atSetup + anApp("org.new.amazon", installer = "com.amazon.venezia", initiating = "com.amazon.venezia")
        assertEquals(listOf(Problem.NewAppOutsidePlay to "org.new.amazon"), newAppsOn(phone, setOf(store)))
    }

    @Test fun onAPhoneWithNoMakerStoreOnlyPlayIsTrusted() { // GS10, GS11, GS20 (store missing from the list)
        val phone = atSetup + aGalaxyStoreApp("org.new.temu")
        assertEquals(listOf(Problem.NewAppOutsidePlay to "org.new.temu"), newAppsOn(phone, emptySet()))
    }

    @Test fun anAppFromTheMakersStoreThatLaterGetsAccessIsStillAskedAbout() { // GS17
        val now = snapshot().copy(apps = atSetup + aGalaxyStoreApp("org.new.temu"), makerStores = setOf(store), notificationListeners = setOf("org.new.temu"))
        val found = DriftDetector().evaluate(inputs(snapshot = now).copy(baseline = baseline)).map { it.key.problem to it.key.subject }
        assertEquals(listOf(Problem.ListenerAdded to "org.new.temu"), found)
    }
}
