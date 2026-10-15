package com.aegida.alerts

import com.aegida.testkit.FakeOutbox
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlertPipelineTest {
    private val now = Instant.now()
    private val topic = NtfyTopic("aegida_" + "p0O9i8U7".repeat(4))

    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val settings = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(settings, NtfySender(http), outbox)) { clockAt }

    /** The pipeline's clock; tests that need a later check move it on. */
    private var clockAt = now

    private fun finding(problem: Problem, subject: String, detector: DetectorId) =
        Finding(FindingKey(detector, problem, subject), emptyMap(), now.minus(Duration.ofSeconds(1)), now)

    private val scamLink = finding(Problem.ScamLink, "evil.example", DetectorId.Link)
    private val dnsOff = finding(Problem.WebsiteFilterOff, "security.cloudflare-dns.com", DetectorId.Dns)

    @Test fun aNewFindingWarnsOwnerAndTellsTheFirstGuardianInHerName() = runTest {
        val result = pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(PipelineResult(alerted = 1, resolved = 0, guardiansNotReached = 0), result)
        assertEquals(listOf(scamLink.key), owner.shown)
        assertEquals(listOf("A message with a scam link arrived on Ruth's phone. Aegida warned Ruth."), http.bodies)
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianTold), history.entries.map { it.outcome })
    }

    @Test fun theGuardianIsToldWhichAppAScamMessageCameFromWhenItIsKnown() = runTest { // Kevin, Oct 10
        val e = com.aegida.core.model.Evidence
        val named = scamLink.copy(evidence = mapOf(e.APP_LABEL to "Messages", e.HOST to "evil.example"))
        pipeline.handle(listOf(named), setOf(DetectorId.Link))
        val told = "A message with a scam link arrived on Ruth's phone, from the “Messages” app, with a link to ev*****.example. Aegida warned Ruth."
        assertEquals(listOf(told), http.bodies)
    }

    @Test fun historyKeepsWhatItWasForTheWarningAndTheGuardianNote() = runTest { // Kevin, Oct 10
        val e = com.aegida.core.model.Evidence
        val named = scamLink.copy(evidence = mapOf(e.APP_LABEL to "Messages", e.HOST to "evil.example", e.MESSAGE_TITLE to "Bank"))
        pipeline.handle(listOf(named), setOf(DetectorId.Link))
        val expected = "It came from the “Messages” app. From: “Bank”. The link went to evil.example."
        assertEquals(listOf(expected, expected), history.entries.map { it.detail })
    }

    private val noisy = Finding(
        FindingKey(DetectorId.NoisyApp, Problem.NoisyApp, "com.example.pdf"),
        mapOf(com.aegida.core.model.Evidence.APP_LABEL to "Image to PDF", com.aegida.core.model.Evidence.NOTICES to "5"), now.minus(Duration.ofSeconds(1)), now,
    )

    /** Finding #92: an app that keeps spamming would warn on every notice, so once the owner has seen it, it is quiet for a week. */
    @Test fun aNoisyAppTheOwnerHasSeenIsNotRaisedAgainForAWeekButIsAfterwards() = runTest {
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        assertEquals(1, owner.shown.size)
        findings.put(findings.get(noisy.key)!!.copy(state = FindingState.Acknowledged, lastSeen = now)) // she tapped Got it
        http.posts.clear(); http.bodies.clear()

        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp)) // the same hour: quiet
        assertEquals(1, owner.shown.size)
        assertEquals(emptyList<String>(), http.bodies)

        val later = AlertPipeline(findings, owner, history, GuardianLine(settings, NtfySender(http), outbox)) { now.plus(Duration.ofDays(8)) }
        later.handle(listOf(noisy), setOf(DetectorId.NoisyApp)) // eight days on: raised again
        assertEquals(2, owner.shown.size)
    }

    @Test fun thePipelineSaysWhichWarningsItKeptQuietAndWhichHaveWaitedADay() = runTest { // finding #94
        val key = FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://new.example")
        val site = Finding(key, emptyMap(), now.minus(Duration.ofHours(30)), now)
        pipeline.handle(listOf(site), setOf(DetectorId.PopupSpam))
        assertEquals("open for 30 hours", setOf(site.key), pipeline.handle(listOf(site), setOf(DetectorId.PopupSpam)).openLong)
        findings.put(findings.get(site.key)!!.copy(state = FindingState.Acknowledged, lastSeen = now)) // she said No
        assertEquals(setOf(site.key), pipeline.handle(listOf(site), setOf(DetectorId.PopupSpam)).quieted)
    }

    @Test fun aQuestionFirstSeenUnderAClockThatWasAheadIsTreatedAsSeenNowAndDeletedADayLater() = runTest { // findings #98, #99
        settings.value = settings.value.copy(alertsOn = false)
        val key = FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://new.example")
        val site = Finding(key, emptyMap(), now.plus(Duration.ofDays(3)), now)
        pipeline.handle(listOf(site), setOf(DetectorId.PopupSpam))
        pipeline.handle(listOf(site), setOf(DetectorId.PopupSpam))
        assertEquals("one owner puts the time right", now, findings.get(site.key)!!.firstSeen)
        val dayLater = AlertPipeline(findings, owner, history, GuardianLine(settings, NtfySender(http), outbox)) { now.plus(Duration.ofHours(25)) }
        assertEquals(setOf(site.key), dayLater.handle(listOf(site), setOf(DetectorId.PopupSpam)).openLong)
    }

    /**
     * Scenario WP17 A15 (findings #149 (6)): a site she allowed keeps sending alarming words while she does not look; she taps the
     * warning a long time after the last one; the quiet week runs from her tap.
     */
    @Test fun theQuietWeekRunsFromHerTapNotFromTheLastNotice() = runTest {
        val site = "https://news.example"
        fun scary(n: Int, at: Instant) = com.aegida.detectors.PopupSpamDetector().evaluate(
            com.aegida.detectors.Inputs(
                Snapshot(at, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent), at,
                Setup(null, setOf(site)), null, com.aegida.testkit.aChromePopup(at, site = "$site/", n = n), null,
            ),
        ).single()
        pipeline.handle(listOf(scary(1, now)), setOf(DetectorId.PopupSpam))
        for (day in 1L..3L) {
            clockAt = now.plus(Duration.ofDays(day))
            pipeline.handle(listOf(scary(day.toInt() + 1, clockAt)), setOf(DetectorId.PopupSpam))
        }
        val key = scary(1, now).key
        assertEquals(Problem.SiteScaryNews, key.problem)
        val tap = now.plus(Duration.ofDays(20))
        clockAt = tap
        assertTrue(SeenMarker(findings, history, owner).markSeen(key, tap))
        clockAt = tap.plus(Duration.ofDays(2))
        pipeline.handle(listOf(scary(9, clockAt)), setOf(DetectorId.PopupSpam))
        assertEquals("told once; two days after her tap is still quiet", listOf(key), owner.shown)
        clockAt = tap.plus(Duration.ofDays(8))
        pipeline.handle(listOf(scary(10, clockAt)), setOf(DetectorId.PopupSpam))
        assertEquals("a week after her tap she is told again", listOf(key, key), owner.shown)
    }

    @Test fun aSeenTimeFromAClockThatWasAheadDoesNotKeepItQuiet() = runTest { // finding #93
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        findings.put(findings.get(noisy.key)!!.copy(state = FindingState.Acknowledged, lastSeen = now.plus(Duration.ofDays(30))))
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        assertEquals(2, owner.shown.size)
    }

    @Test fun theGuardianIsToldWhichAppAndTheOwnerIsToldNothingElse() = runTest {
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        assertEquals(listOf("An app (“Image to PDF”) keeps showing Ruth ads as notices. Aegida told Ruth how to turn them off."), http.bodies)
    }

    @Test fun aCheckKilledAfterWarningButBeforeFinishingStillTellsTheGuardianOnTheNextRun() = runTest {
        val dying = object : HttpClient by http {
            var die = true
            override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
                if (die) { die = false; error("process killed") }
                return http.post(endpoint, body, headers)
            }
        }
        val killable = AlertPipeline(findings, owner, history, GuardianLine(settings, NtfySender(dying), outbox)) { now }
        try { killable.handle(listOf(scamLink), setOf(DetectorId.Link)) } catch (expected: IllegalStateException) { /* the process died here */ }
        val result = killable.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(1, result.alerted)
        assertEquals(1, http.bodies.size)
    }

    @Test fun aDefaultAppChangedBackIsResolvedAndTheQuestionDisappears() = runTest {
        val changed = finding(Problem.BrowserChanged, "org.other.browser", DetectorId.Drift)
        pipeline.handle(listOf(changed), setOf(DetectorId.Drift))
        val result = pipeline.handle(emptyList(), setOf(DetectorId.Drift))
        assertEquals(1, result.resolved)
        assertEquals(FindingState.Resolved, findings.rows[changed.key]!!.state)
        assertEquals(listOf(changed.key), owner.cleared)
    }

    /** GS2: a question raised before the phone's own store was trusted goes away at the next check, through the real detector. */
    @Test fun aNewAppQuestionFromTheMakersStoreIsResolvedAtTheNextCheck() = runTest {
        val store = "org.maker.store"
        val app = com.aegida.core.model.InstalledApp("org.new.temu", "Temu", store, false, initiatingInstaller = store)
        val atSetup = com.aegida.core.model.Snapshot(
            now, com.aegida.core.model.PrivateDns.Automatic, null, true, true,
            com.aegida.core.oem.Reading.NotPresent, com.aegida.core.oem.Reading.NotPresent,
        )
        val baseline = com.aegida.core.model.Baseline.of(atSetup)
        fun check(phone: com.aegida.core.model.Snapshot) =
            com.aegida.detectors.NewAppDetector()
                .evaluate(com.aegida.detectors.Inputs(phone, now, com.aegida.core.model.Setup.NotDone, null, null, null, baseline = baseline))

        val before = check(atSetup.copy(apps = listOf(app))) // the old build: no trusted store
        assertEquals(1, before.size)
        pipeline.handle(before, setOf(DetectorId.NewApp))

        val after = check(atSetup.copy(apps = listOf(app), makerStores = setOf(store)))
        val result = pipeline.handle(after, setOf(DetectorId.NewApp), phone = atSetup.copy(apps = listOf(app), makerStores = setOf(store)))
        assertEquals(1, result.resolved)
        assertEquals(FindingState.Resolved, findings.rows[before.single().key]!!.state)
        // Kevin, Oct 14: she did nothing, so History does not say "Fixed."
        assertEquals(Outcome.NoLongerAConcern, history.entries.last().outcome)
        assertEquals("No longer a concern.", history.entries.last().outcome.forMom)
    }

    /**
     * GS2 variant (findings #118): the spying-app feed now lists the app she was asked about. The new-app question closes because the urgent
     * warning took over, not because the app became fine, so History never says "No longer a concern." about it; it says nothing (the urgent
     * warning has its own lines).
     */
    @Test fun aNewAppQuestionClosedBecauseTheFeedNowListsTheAppIsNeverCalledNoLongerAConcern() = runTest {
        val app = com.aegida.testkit.anAppOpenedFromAFile("org.new.helper")
        val atSetup = com.aegida.core.model.Snapshot(
            now, com.aegida.core.model.PrivateDns.Automatic, null, true, true,
            com.aegida.core.oem.Reading.NotPresent, com.aegida.core.oem.Reading.NotPresent,
        )
        val baseline = com.aegida.core.model.Baseline.of(atSetup)
        val phone = atSetup.copy(apps = listOf(app))
        fun inputs(feed: com.aegida.core.feed.ThreatFeed?) =
            com.aegida.detectors.Inputs(phone, now, com.aegida.core.model.Setup.NotDone, feed, null, null, baseline = baseline)
        val ran = setOf(DetectorId.NewApp, DetectorId.Stalkerware)
        fun check(feed: com.aegida.core.feed.ThreatFeed?) =
            com.aegida.detectors.NewAppDetector().evaluate(inputs(feed)) + com.aegida.detectors.StalkerwareDetector().evaluate(inputs(feed))
        val question = check(null).single()
        pipeline.handle(listOf(question), ran, phone = phone)

        val feed = com.aegida.core.feed.ThreatFeed.of(
            now, emptyList(), emptyList(), emptyList(), apps = com.aegida.core.feed.AppKeys(packageNames = listOf(app.packageName)),
        )
        val after = check(feed)
        assertEquals(listOf(com.aegida.core.text.Problem.StalkerwareApp), after.map { it.key.problem }) // the urgent warning opens instead
        pipeline.handle(after, ran, phone = phone)

        assertEquals(FindingState.Resolved, findings.rows[question.key]!!.state)
        val linesForTheQuestion = history.entries.filter { it.problem == question.key.problem }.map { it.outcome }
        assertTrue(linesForTheQuestion.toString(), Outcome.NoLongerAConcern !in linesForTheQuestion && Outcome.Fixed !in linesForTheQuestion)
    }

    /** GS2's other side: the app she was asked about is gone, so the question closed because she removed it. */
    @Test fun aNewAppQuestionClosedBecauseTheAppWasRemovedSaysFixed() = runTest {
        val app = com.aegida.core.model.InstalledApp("org.new.byhand", "By hand", null, false)
        val atSetup = com.aegida.core.model.Snapshot(
            now, com.aegida.core.model.PrivateDns.Automatic, null, true, true,
            com.aegida.core.oem.Reading.NotPresent, com.aegida.core.oem.Reading.NotPresent,
        )
        val baseline = com.aegida.core.model.Baseline.of(atSetup)
        fun check(phone: com.aegida.core.model.Snapshot) =
            com.aegida.detectors.NewAppDetector()
                .evaluate(com.aegida.detectors.Inputs(phone, now, com.aegida.core.model.Setup.NotDone, null, null, null, baseline = baseline))
        pipeline.handle(check(atSetup.copy(apps = listOf(app))), setOf(DetectorId.NewApp), phone = atSetup.copy(apps = listOf(app)))
        pipeline.handle(check(atSetup), setOf(DetectorId.NewApp), phone = atSetup)
        assertEquals(Outcome.Fixed, history.entries.last().outcome)
    }

    /** HC1 (findings #129): the closing line says which app it was, so History can turn the question green. */
    @Test fun aRemovedAppsQuestionTurnsGreenInHistoryWhenItsFixedLineIsWritten() = runTest {
        val app = com.aegida.core.model.InstalledApp("org.new.byhand", "By hand", null, false)
        val atSetup = com.aegida.core.model.Snapshot(
            now, com.aegida.core.model.PrivateDns.Automatic, null, true, true,
            com.aegida.core.oem.Reading.NotPresent, com.aegida.core.oem.Reading.NotPresent,
        )
        val baseline = com.aegida.core.model.Baseline.of(atSetup)
        fun check(phone: com.aegida.core.model.Snapshot) =
            com.aegida.detectors.NewAppDetector()
                .evaluate(com.aegida.detectors.Inputs(phone, clockAt, com.aegida.core.model.Setup.NotDone, null, null, null, baseline = baseline))
        pipeline.handle(check(atSetup.copy(apps = listOf(app))), setOf(DetectorId.NewApp), phone = atSetup.copy(apps = listOf(app)))
        clockAt = now.plus(Duration.ofHours(6))
        pipeline.handle(check(atSetup), setOf(DetectorId.NewApp), phone = atSetup)

        val rows = com.aegida.core.alert.HistoryRows.of(history.entries)
        val question = rows.single { it.at == now }
        assertTrue(question.detail, Outcome.OwnerWarned.forMom in question.detail)
        assertTrue(question.handled)
        assertEquals(question.facts, rows.single { it.at == clockAt }.facts) // the Fixed line names the same app
    }

    /** HC4 (findings #118, #129): the spying-app list now names the app; the question closes with "See the newer warning.". */
    @Test fun aNewAppQuestionTakenOverByTheSpyingAppWarningSaysSeeTheNewerWarning() = runTest {
        val app = com.aegida.testkit.anAppOpenedFromAFile("org.new.helper")
        val atSetup = com.aegida.core.model.Snapshot(
            now, com.aegida.core.model.PrivateDns.Automatic, null, true, true,
            com.aegida.core.oem.Reading.NotPresent, com.aegida.core.oem.Reading.NotPresent,
        )
        val baseline = com.aegida.core.model.Baseline.of(atSetup)
        val phone = atSetup.copy(apps = listOf(app))
        val ran = setOf(DetectorId.NewApp, DetectorId.Stalkerware)
        fun check(feed: com.aegida.core.feed.ThreatFeed?): List<Finding> {
            val inputs = com.aegida.detectors.Inputs(phone, clockAt, com.aegida.core.model.Setup.NotDone, feed, null, null, baseline = baseline)
            return com.aegida.detectors.NewAppDetector().evaluate(inputs) + com.aegida.detectors.StalkerwareDetector().evaluate(inputs)
        }
        pipeline.handle(check(null), ran, phone = phone)
        clockAt = now.plus(Duration.ofHours(6))
        val feed = com.aegida.core.feed.ThreatFeed.of(
            now, emptyList(), emptyList(), emptyList(), apps = com.aegida.core.feed.AppKeys(packageNames = listOf(app.packageName)),
        )
        pipeline.handle(check(feed), ran, phone = phone)

        val closing = history.entries.single { it.problem == Problem.NewAppOutsidePlay && it.at == clockAt }
        assertEquals(Outcome.SeeNewerWarning, closing.outcome)
        val rows = com.aegida.core.alert.HistoryRows.of(history.entries)
        assertTrue(rows.single { it.at == now }.handled) // the question
        assertTrue(rows.single { it.title == Problem.StalkerwareApp.whatHappened }.handled.not()) // the newer warning still needs her
    }

    /** HC14: the baseline was retaken while the question was open; there is no newer warning, so no line says there is one. */
    @Test fun aQuestionClosedWithNoNewerWarningNeverSaysSeeTheNewerWarning() = runTest {
        val app = com.aegida.testkit.anAppOpenedFromAFile("org.new.helper")
        val atSetup = com.aegida.core.model.Snapshot(
            now, com.aegida.core.model.PrivateDns.Automatic, null, true, true,
            com.aegida.core.oem.Reading.NotPresent, com.aegida.core.oem.Reading.NotPresent,
        )
        val phone = atSetup.copy(apps = listOf(app))
        fun check(baseline: com.aegida.core.model.Baseline) = com.aegida.detectors.NewAppDetector()
            .evaluate(com.aegida.detectors.Inputs(phone, clockAt, com.aegida.core.model.Setup.NotDone, null, null, null, baseline = baseline))
        pipeline.handle(check(com.aegida.core.model.Baseline.of(atSetup)), setOf(DetectorId.NewApp), phone = phone)
        clockAt = now.plus(Duration.ofHours(6))
        val after = check(com.aegida.core.model.Baseline.of(phone))
        assertEquals(emptyList<Finding>(), after)
        pipeline.handle(after, setOf(DetectorId.NewApp), phone = phone)

        assertTrue(history.entries.none { it.outcome == Outcome.SeeNewerWarning })
    }

    @Test fun theGuardianMessageNeverContainsTheLinkOrEvidence() = runTest {
        pipeline.handle(listOf(scamLink.copy(evidence = mapOf("host" to "evil.example"))), setOf(DetectorId.Link))
        assertTrue(http.bodies.none { "evil.example" in it || "http" in it })
    }

    @Test fun aRepeatDoesNotAlertAgain() = runTest {
        pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        val result = pipeline.handle(listOf(scamLink.copy(lastSeen = now.plusSeconds(60))), setOf(DetectorId.Link))
        assertEquals(0, result.alerted)
        assertEquals(1, owner.shown.size)
        assertEquals(1, http.bodies.size)
        assertEquals(now.plusSeconds(60), findings.rows[scamLink.key]!!.lastSeen)
    }

    @Test fun guardiansHearNothingWhenOwnerHasNotTurnedAlertsOn() = runTest {
        settings.value = settings.value.copy(alertsOn = false)
        pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(listOf(scamLink.key), owner.shown)
    }

    @Test fun anUnreachedGuardianIsRecordedNotDropped() = runTest {
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        val result = pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(1, result.guardiansNotReached)
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianNotReached), history.entries.map { it.outcome })
    }

    @Test fun aSettingThatIsPutRightIsResolvedAndItsBannerCleared() = runTest {
        pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns))
        val result = pipeline.handle(emptyList(), setOf(DetectorId.Dns))
        assertEquals(1, result.resolved)
        assertEquals(FindingState.Resolved, findings.rows[dnsOff.key]!!.state)
        assertEquals(listOf(dnsOff.key), owner.cleared)
        assertEquals(Outcome.Fixed, history.entries.last().outcome)
    }

    @Test fun eventFindingsAreNotResolvedJustBecauseNoMessageArrived() = runTest {
        pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(0, pipeline.handle(emptyList(), setOf(DetectorId.Link, DetectorId.Dns)).resolved)
    }

    @Test fun aSettingNotCheckedThisRunIsNotResolved() = runTest {
        pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns))
        assertEquals(0, pipeline.handle(emptyList(), setOf(DetectorId.Link)).resolved)
    }

    @Test fun infoFindingsAreStoredButDoNotBannerOrTellGuardians() = runTest {
        val listsOld = finding(Problem.ListsOld, "aegida", DetectorId.Health)
        pipeline.handle(listOf(listsOld), setOf(DetectorId.Health))
        assertEquals(emptyList<FindingKey>(), owner.shown)
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(FindingState.Open, findings.rows[listsOld.key]!!.state)
    }

    @Test fun aResolvedProblemThatComesBackAlertsAgain() = runTest {
        pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns))
        pipeline.handle(emptyList(), setOf(DetectorId.Dns))
        assertEquals(1, pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns)).alerted)
        assertEquals(2, owner.shown.size)
    }
}
