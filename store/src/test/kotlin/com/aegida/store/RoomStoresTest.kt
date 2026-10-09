package com.aegida.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.Outcome
import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.model.AutoBlockerAnswer
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Setup
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class RoomStoresTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @get:Rule val temp = TemporaryFolder()

    @After fun close() = db.close()

    private val finding = Finding(
        FindingKey(DetectorId.PopupSpam, Problem.SiteNotifications, "http://localhost:8765"),
        mapOf(Evidence.CHANNEL_ID to "web:http://localhost:8765;13435952006713133", Evidence.PACKAGE to "com.android.chrome"),
        now.minus(Duration.ofMinutes(3)),
        now,
    )

    @Test fun findingsRoundTripWithEvidenceAndReplaceByKey() = runTest {
        val store = RoomFindingStore(db.findings())
        store.put(finding)
        assertEquals(finding, store.get(finding.key))
        store.put(finding.copy(state = FindingState.Resolved))
        assertEquals(FindingState.Resolved, store.get(finding.key)!!.state)
        assertEquals(emptyList<Finding>(), store.open())
        assertNull(store.get(finding.key.copy(subject = "http://other.example")))
    }

    @Test fun aWarningTheGuardianWasRemindedAboutIsStillOpenButASeenOneIsNot() = runTest {
        val store = RoomFindingStore(db.findings())
        store.put(finding.copy(state = FindingState.Escalated))
        assertEquals(listOf(FindingState.Escalated), store.open().map { it.state })
        store.put(finding.copy(state = FindingState.Acknowledged))
        assertEquals(emptyList<Finding>(), store.open())
    }

    @Test fun historyIsNewestFirstAndPrunesByAge() = runTest {
        val log = RoomHistoryLog(db.history())
        val old = HistoryEntry(now.minus(Duration.ofDays(91)), Problem.ScamLink, Outcome.OwnerWarned)
        val recent = HistoryEntry(now.minus(Duration.ofDays(1)), Problem.ScamLink, Outcome.GuardianTold)
        val newest = HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.Fixed)
        listOf(old, recent, newest).forEach { log.add(it) }
        assertEquals(listOf(newest, recent, old), log.since(now.minus(Duration.ofDays(365))))
        assertEquals(1, log.prune(now.minus(Duration.ofDays(90))))
        assertEquals(listOf(newest, recent), log.since(now.minus(Duration.ofDays(365))))
    }

    @Test fun setupAndGuardiansRoundTripAndStartEmpty() = runTest {
        val settings = RoomSettings(db.settings())
        assertEquals(Setup.NotDone, settings.setup())
        assertEquals(Guardians.None, settings.current())
        val setup = Setup(
            "security.cloudflare-dns.com", setOf("https://news.example", "https://recipes.example"),
            pictureId = "Owl", autoBlocker = AutoBlockerAnswer.NotNow, stepsDone = 8,
        )
        val guardians = Guardians(
            alertsOn = true, ownerName = "Ruth", firstTopic = NtfyTopic("aegida_" + "r5T6y7U8".repeat(4)),
            firstGuardianName = "Kevin", firstGuardianEmail = "kevin@example.org", firstGuardianPhone = "+1 555 0100",
            pairedAt = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
        )
        settings.save(setup)
        settings.save(guardians)
        assertEquals(setup, settings.setup())
        assertEquals(guardians, settings.current())
    }

    @Test fun baselineStartsAsNoneAndRoundTripsIncludingUnsetDefaults() = runTest {
        val settings = RoomSettings(db.settings())
        assertNull(settings.baseline())
        val baseline = com.aegida.core.model.Baseline(
            browser = "com.android.chrome", smsApp = null, dialerApp = "com.google.android.dialer",
            notificationListeners = setOf("app.b", "app.a"), accessibilityApps = emptySet(), deviceAdmins = setOf("app.admin"),
            assistant = "org.assistant", apps = setOf("org.one", "org.two"), acceptedRemoteApps = setOf("com.anydesk.anydeskandroid"),
        )
        settings.save(baseline)
        assertEquals(baseline, settings.baseline())
        settings.save(baseline.copy(browser = null, accessibilityApps = setOf("app.screen")))
        assertEquals(baseline.copy(browser = null, accessibilityApps = setOf("app.screen")), settings.baseline())
        settings.save(baseline.copy(apps = null))
        assertNull("not recorded is not the same as no apps", settings.baseline()!!.apps)
        settings.save(baseline.copy(apps = emptySet()))
        assertEquals(emptySet<String>(), settings.baseline()!!.apps)
    }

    private fun feedBytes(at: Instant) =
        FeedCodec.encode(ThreatFeed.of(at, listOf(FeedSource("test", "test", 1)), listOf("evil.example"), emptyList()))

    @Test fun feedSurvivesARestartAndADamagedFileReadsAsNoFeed() = runTest {
        val dir = temp.newFolder("feed")
        FileFeedStore(dir).save(feedBytes(now))
        assertEquals(now.truncatedTo(ChronoUnit.SECONDS), FileFeedStore(dir).current()!!.generatedAt) // new instance = restart
        File(dir, "feed.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(FileFeedStore(dir).current())
    }

    @Test fun anEmptyFeedStoreHasNoFeed() = runTest {
        assertNull(FileFeedStore(temp.newFolder("empty")).current())
    }

    @Test fun anAnswerFromAnOlderOrNewerAppThatIsNotKnownReadsAsNotAsked() = runTest {
        db.settings().put(listOf(SettingEntity("setup.autoBlocker", "SomethingNew")))
        assertEquals(AutoBlockerAnswer.NotAsked, RoomSettings(db.settings()).setup().autoBlocker)
    }
}
