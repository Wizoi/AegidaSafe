package com.aegida.detectors

import com.aegida.core.feed.AppKeys
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.model.Baseline
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.Snapshot
import com.aegida.core.notice.NoticeRow
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import com.aegida.detectors.Fixtures.now
import com.aegida.detectors.Fixtures.snapshot
import com.aegida.testkit.GALAXY_STORE
import com.aegida.testkit.aGalaxyStoreApp
import com.aegida.testkit.aPlayApp
import com.aegida.testkit.aSystemApp
import com.aegida.testkit.anAppOpenedFromAFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration

/**
 * #164 (scenarios WN28-WN33): every app warning carries, next to the name the app gives itself, facts it cannot fake: where it came
 * from ([Evidence.APP_SOURCE]) and when it was first installed ([Evidence.APP_ADDED]), plus its package. App shapes are the testkit's,
 * which come from the Pixel and Galaxy dumps.
 */
class AppFactsEvidenceTest {
    private val added = now.minus(Duration.ofHours(2))
    private val baseline = Baseline.of(snapshot().copy(apps = listOf(aSystemApp("com.android.settings"))))

    private fun Finding.facts() = Triple(evidence[Evidence.APP_SOURCE], evidence[Evidence.APP_ADDED], evidence[Evidence.PACKAGE])

    private fun drift(phone: Snapshot) = DriftDetector().evaluate(inputs(snapshot = phone).copy(baseline = baseline))

    @Test fun aNewAppFromOutsideThePlayStoreSaysItCameFromOutsideAndWhenItWasAdded() { // WN28, WN29
        val app = anAppOpenedFromAFile("org.new.sideloaded").copy(firstInstalled = added)
        val finding = NewAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(app))).copy(baseline = baseline)).single()
        assertEquals(Triple("outside", added.toEpochMilli().toString(), "org.new.sideloaded"), finding.facts())
        assertEquals("sideloaded", finding.evidence[Evidence.APP_LABEL])
    }

    @Test fun aRemoteControlAppFromPlaySaysItCameFromThePlayStore() { // WN28, WN7 variant "installed from Play"
        val app = aPlayApp("com.anydesk.anydeskandroid").copy(firstInstalled = added)
        val finding = RemoteControlDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(app))).copy(baseline = baseline)).single()
        assertEquals(Triple("play", added.toEpochMilli().toString(), "com.anydesk.anydeskandroid"), finding.facts())
    }

    @Test fun aSpyingAppFromTheMakersStoreSaysItCameFromTheMakersStore() { // WN28, WN8
        val feed = ThreatFeed.of(
            now, listOf(FeedSource("Echap", "CC BY 4.0", 1)), emptyList(), emptyList(), apps = AppKeys(packageNames = listOf("com.thetruth")),
        )
        val spy = aGalaxyStoreApp("com.thetruth").copy(firstInstalled = added)
        val phone = snapshot().copy(apps = listOf(aSystemApp(GALAXY_STORE), spy), makerStores = setOf(GALAXY_STORE))
        val finding = StalkerwareDetector().evaluate(inputs(snapshot = phone, feed = feed)).single()
        assertEquals(Triple("maker_store", added.toEpochMilli().toString(), "com.thetruth"), finding.facts())
    }

    @Test fun eachChangedDefaultAppAndEachNewAccessSaysWhereTheAppCameFromAndWhen() { // WN28: WN4, WN5, WN6, WN9
        val pkgs = listOf("org.b", "org.s", "org.d", "org.a", "org.l", "org.x", "org.m")
        val phone = snapshot().copy(
            browser = "org.b", smsApp = "org.s", dialerApp = "org.d", assistant = "org.a",
            notificationListeners = setOf("org.l"), accessibilityApps = setOf("org.x"), deviceAdmins = setOf("org.m"),
            apps = pkgs.map { anAppOpenedFromAFile(it).copy(firstInstalled = added) },
        )
        val findings = drift(phone)
        assertEquals(
            setOf(
                Problem.BrowserChanged, Problem.SmsAppChanged, Problem.DialerChanged, Problem.AssistantChanged,
                Problem.ListenerAdded, Problem.AccessibilityAdded, Problem.AdminAdded,
            ),
            findings.map { it.key.problem }.toSet(),
        )
        findings.forEach { assertEquals(it.key.problem.name, Triple("outside", added.toEpochMilli().toString(), it.key.subject), it.facts()) }
    }

    @Test fun anAppThatCameWithThePhoneSaysSoAndHasNoAddedTime() { // WN29: the snapshotter reads no install time for system apps
        val watch = "com.google.android.apps.wearables"
        val phone = snapshot().copy(notificationListeners = setOf(watch), apps = listOf(aSystemApp(watch)))
        assertEquals(Triple("system", null, "com.google.android.apps.wearables"), drift(phone).single().facts())
    }

    @Test fun anAppNotInTheListGetsNoNameAndNoFactsRatherThanAGuess() { // WN31: uninstalled, or in the work profile
        val finding = drift(snapshot().copy(notificationListeners = setOf("org.gone"))).single()
        assertEquals(Triple(null, null, null), finding.facts())
        assertFalse(Evidence.APP_LABEL in finding.evidence)
    }

    @Test fun aNoisyAppSaysWhereItCameFromAndWhen() { // WN28, WN11
        val app = aPlayApp("com.android.shell").copy(label = "Image to PDF", category = 3, firstInstalled = added)
        val notices = listOf(3, 2, 0).mapIndexed { i, s -> NoticeRow(now.minus(Duration.ofMinutes(10L * (i + 1))), s) }
        val event = message("File converter is ready! Convert", title = "Image to PDF")
        val finding = NoisyAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(app)), event = event).copy(notices = notices)).single()
        assertEquals(Triple("play", added.toEpochMilli().toString(), "com.android.shell"), finding.facts())
    }

    @Test fun aScamLinkSaysWhereTheAppItCameInCameFrom() { // WN12: the carrier app, when the phone lists it
        val phone = snapshot().copy(apps = listOf(aSystemApp("com.android.shell")))
        val finding = LinkDetector().evaluate(inputs(snapshot = phone, event = message("Refund: https://evil.example/claim"))).single()
        assertEquals(Triple("system", null, "com.android.shell"), finding.facts())
    }

    @Test fun aPreloadedInstallerThatOnlyWritesTheMakersStoreNameIsOutsideNotTheStore() { // WN33, as NewAppDetector decides (GS6)
        val pushed = aGalaxyStoreApp("org.pushed", initiating = "com.preloaded.installer").copy(firstInstalled = added)
        val phone = snapshot().copy(apps = listOf(aSystemApp(GALAXY_STORE), pushed, aGalaxyStoreApp("org.real")), makerStores = setOf(GALAXY_STORE))
        val findings = NewAppDetector().evaluate(inputs(snapshot = phone).copy(baseline = baseline))
        assertEquals(listOf("org.pushed"), findings.map { it.key.subject })
        assertEquals("outside", findings.single().evidence[Evidence.APP_SOURCE])
        val asListener = drift(phone.copy(notificationListeners = setOf("org.real"))).single()
        assertEquals("maker_store", asListener.evidence[Evidence.APP_SOURCE])
    }
}
