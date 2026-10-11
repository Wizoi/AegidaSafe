package com.aegida.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.notice.AllowedItem
import com.aegida.core.notice.NoticeRow
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
import org.junit.Assert.assertTrue
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

    @Test fun resolvedFindingsPruneByAgeButOpenAndRecentOnesStay() = runTest { // findings #81 (c)
        val store = RoomFindingStore(db.findings())
        fun at(subject: String, daysAgo: Long, state: FindingState) =
            finding.copy(key = finding.key.copy(subject = subject), lastSeen = now.minus(Duration.ofDays(daysAgo)), state = state)
        val oldResolved = at("old-resolved", 91, FindingState.Resolved)
        val recentResolved = at("recent-resolved", 89, FindingState.Resolved)
        val oldOpen = at("old-open", 200, FindingState.Open)
        val oldSeen = at("old-acknowledged", 200, FindingState.Acknowledged)
        listOf(oldResolved, recentResolved, oldOpen, oldSeen).forEach { store.put(it) }
        assertEquals(1, store.prune(now.minus(Duration.ofDays(90))))
        assertNull(store.get(oldResolved.key))
        listOf(recentResolved, oldOpen, oldSeen).forEach { assertEquals(it, store.get(it.key)) }
    }

    @Test fun aHistoryRowKeepsItsDetailAndALegacyRowHasNone() = runTest { // Kevin, Oct 10
        val log = RoomHistoryLog(db.history())
        val with = HistoryEntry(now, Problem.ScamLink, Outcome.OwnerWarned, "It came from the “Messages” app.")
        val without = HistoryEntry(now.minus(Duration.ofDays(1)), Problem.ListenerOff, Outcome.OwnerWarned)
        log.add(with); log.add(without)
        assertEquals(listOf(with, without), log.since(now.minus(Duration.ofDays(2))))
    }

    /** Findings #92, #93: the notices an app posted, as counts and signals only; the same notice is counted once. */
    @Test fun aNoticeIsCountedOnceWhetherUpdatedSoonAfterOrComingBackLater() = runTest {
        val log = RoomNoticeLog(db.notices())
        val t = now.minus(Duration.ofHours(3))
        log.record("com.example.pdf", "k1", 100, t, 2)
        log.record("com.example.pdf", "k1", 101, t.plusSeconds(300), 2)           // updated five minutes later: the same notice
        log.record("com.example.pdf", "k1", 100, t.plus(Duration.ofHours(2)), 2)   // the same one back from a snooze: not new
        log.record("com.example.pdf", "k1", 200, t.plus(Duration.ofHours(1)), 3)   // the same id posted again an hour later: new
        log.record("com.example.pdf", "k2", 5, now.minus(Duration.ofMinutes(5)), 0)
        log.record("org.other", "k3", 1, now, 1)
        assertEquals(
            listOf(NoticeRow(t, 2), NoticeRow(t.plus(Duration.ofHours(1)), 3), NoticeRow(now.minus(Duration.ofMinutes(5)), 0)),
            log.since("com.example.pdf", now.minus(Duration.ofDays(1))),
        )
    }

    @Test fun theNotificationKeyIsNeverStoredAsItIs() = runTest { // it can carry an app's own tag (finding #93)
        RoomNoticeLog(db.notices()).record("com.example.pdf", "0|com.example.pdf|7|user-4471|10123", 1, now, 1)
        db.query("SELECT * FROM notice", null).use { c ->
            assertTrue(c.moveToFirst())
            for (i in 0 until c.columnCount) assertTrue(c.columnNames[i], c.getString(i)?.contains("user-4471") != true)
        }
    }

    @Test fun oldNoticesAndOnesFromAClockThatWasAheadArePruned() = runTest {
        val log = RoomNoticeLog(db.notices())
        log.record("a", "k1", 1, now.minus(Duration.ofDays(8)), 1)
        log.record("a", "k2", 2, now.minus(Duration.ofDays(1)), 1)
        log.record("a", "k3", 3, now.plus(Duration.ofDays(2)), 1)
        assertEquals(2, log.prune(now.minus(Duration.ofDays(7)), now.plus(Duration.ofMinutes(5))))
        assertEquals(1, log.since("a", now.minus(Duration.ofDays(30))).size)
    }

    @Test fun theAllowListKeepsOneRowPerAppAndAnItemCanBeRemoved() = runTest {
        val list = RoomAllowList(db.allowed())
        val pdf = AllowedItem(Problem.NoisyApp, "com.example.pdf", "Image to PDF", now.minus(Duration.ofDays(1)))
        list.add(pdf)
        list.add(pdf.copy(label = "Image to PDF 2", at = now)) // REPLACE, not append (CLAUDE.md)
        list.add(AllowedItem(Problem.NoisyApp, "org.other", "Other", now.minus(Duration.ofDays(2))))
        assertEquals(listOf("Image to PDF 2", "Other"), list.all().map { it.label }) // newest first
        list.remove(Problem.NoisyApp, "com.example.pdf")
        assertEquals(listOf("Other"), list.all().map { it.label })
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
            websites = setOf("http://localhost:8765", "https://news.example"),
        )
        settings.save(baseline)
        assertEquals(baseline, settings.baseline())
        settings.save(baseline.copy(websites = null))
        assertNull("not recorded stays not recorded", settings.baseline()!!.websites)
        settings.save(baseline.copy(websites = emptySet()))
        assertEquals("recorded and none stays none", emptySet<String>(), settings.baseline()!!.websites)
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
