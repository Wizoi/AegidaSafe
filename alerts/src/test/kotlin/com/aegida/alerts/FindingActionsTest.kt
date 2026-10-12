package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Baseline
import com.aegida.core.model.DetectorId
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.HomeState
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Fix
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FindingActionsTest {
    private val now = Instant.now()
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val topic = NtfyTopic.generate(java.util.Random(3))
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, guardians, NtfySender(http), outbox) { now }
    private val baselines = FakeBaselines()
    private var phone = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val actions = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate()) { now }

    private val scam = Finding(FindingKey(DetectorId.Link, Problem.ScamLink, "evil.example"), emptyMap(), now.minus(Duration.ofMinutes(1)), now)

    /** Kevin, Oct 10: the sender, words and time of a scam message are kept on the phone only until the owner has seen the warning. */
    @Test fun gotItClearsTheMessageDetailsButKeepsTheRest() = runTest {
        val e = com.aegida.core.model.Evidence
        val withMessage = scam.copy(evidence = mapOf(e.HOST to "evil.example", e.APP_LABEL to "Messages", e.MESSAGE_TITLE to "Bank", e.MESSAGE_TEXT to "Verify now", e.MESSAGE_AT to "123"))
        pipeline.handle(listOf(withMessage), setOf(DetectorId.Link))
        assertTrue(actions.acknowledge(withMessage.key))
        assertEquals(mapOf(e.HOST to "evil.example", e.APP_LABEL to "Messages"), findings.get(withMessage.key)!!.evidence)
    }

    private val allowList = FakeAllowList()
    private val noisy = Finding(
        FindingKey(DetectorId.NoisyApp, Problem.NoisyApp, "com.example.pdf"),
        mapOf(com.aegida.core.model.Evidence.APP_LABEL to "Image to PDF", com.aegida.core.model.Evidence.PACKAGE to "com.example.pdf"), now.minus(Duration.ofMinutes(1)), now,
    )

    /** Kevin, Oct 11: the owner may say an app is fine; it is written to the list in My settings and the guardian is always told. */
    @Test fun allowingANoisyAppAddsItToTheListMarksItSeenAndTellsTheGuardian() = runTest {
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        http.posts.clear(); http.bodies.clear()

        assertTrue(withAllow.allow(noisy.key))

        assertEquals(listOf(com.aegida.core.notice.AllowedItem(Problem.NoisyApp, "com.example.pdf", "Image to PDF", now)), allowList.items)
        assertEquals(FindingState.Acknowledged, findings.get(noisy.key)!!.state)
        assertEquals(listOf("Ruth told Aegida that an app (“Image to PDF”) is fine, so Aegida will stop warning about it. If that surprises you, it may be a good time to ask Ruth."), http.bodies)
    }

    @Test fun allowingAnAppNamedLikeAWebAddressTellsTheGuardianOnlyAMaskedAddress() = runTest { // finding #98
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        val sly = noisy.copy(evidence = mapOf(com.aegida.core.model.Evidence.APP_LABEL to "Get evil-site.com"))
        pipeline.handle(listOf(sly), setOf(DetectorId.NoisyApp))
        http.posts.clear(); http.bodies.clear()
        withAllow.allow(sly.key)
        val note = http.bodies.single()
        assertFalse(note, note.contains("evil-site.com"))
        assertTrue(note, note.contains("ev*****.com"))
    }

    @Test fun allowingAnAppWithNoNameNeverSendsItsPackageName() = runTest {
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        val unnamed = noisy.copy(evidence = emptyMap())
        pipeline.handle(listOf(unnamed), setOf(DetectorId.NoisyApp))
        http.posts.clear(); http.bodies.clear()
        withAllow.allow(unnamed.key)
        assertEquals(listOf("Ruth told Aegida that an app is fine, so Aegida will stop warning about it. If that surprises you, it may be a good time to ask Ruth."), http.bodies)
    }

    @Test fun onlyAnAppWarningCanBeAllowedNeverASpyingOrRemoteControlOne() = runTest {
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        val spy = Finding(FindingKey(DetectorId.Stalkerware, Problem.StalkerwareApp, "org.spy"), emptyMap(), now, now)
        pipeline.handle(listOf(spy), setOf(DetectorId.Stalkerware))
        assertFalse(withAllow.allow(spy.key))
        assertEquals(emptyList<Any>(), allowList.items)
    }

    @Test fun warnMeAgainEndsTheQuietWeekSoTheNextAdsWarnAtOnce() = runTest { // finding #93
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        withAllow.allow(noisy.key)
        withAllow.stopAllowing(Problem.NoisyApp, "com.example.pdf")
        val shownBefore = owner.shown.size
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        assertEquals(shownBefore + 1, owner.shown.size)
    }

    @Test fun theAllowNoteIsQueuedWhenOfflineAsItsOwnKindAndHistorySaysWhatSheDid() = runTest { // finding #93
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        assertTrue(withAllow.allow(noisy.key))
        assertEquals(listOf(Problem.AppAllowed), outbox.all().map { it.problem })
        val lines = history.entries.filter { it.problem == Problem.AppAllowed }
        assertEquals(setOf(Outcome.Acknowledged, Outcome.GuardianNotReached), lines.map { it.outcome }.toSet())
        assertEquals("The “Image to PDF” app.", lines.first().detail)
    }

    @Test fun takingAnAppOffTheListLetsItBeWarnedAboutAgain() = runTest {
        val withAllow = FindingActions(findings, history, owner, baselines, { phone }, pipeline, AlertGate(), allowList = allowList) { now }
        pipeline.handle(listOf(noisy), setOf(DetectorId.NoisyApp))
        withAllow.allow(noisy.key)
        withAllow.stopAllowing(Problem.NoisyApp, "com.example.pdf")
        assertEquals(emptyList<Any>(), allowList.items)
    }

    @Test fun aSeenWarningLeavesHomeClearsItsBannerAndIsWrittenToHistory() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        assertTrue(HomeState.of(findings.open(), null) is HomeState.NeedsTaps)

        assertTrue(actions.acknowledge(scam.key))

        assertEquals(FindingState.Acknowledged, findings.get(scam.key)!!.state)
        assertFalse(HomeState.of(findings.open(), null) is HomeState.NeedsTaps)
        assertEquals(listOf(scam.key), owner.cleared)
        val row = HistoryRows.of(history.entries).single { Outcome.Acknowledged.forMom in it.detail }
        assertEquals("A message had a scam link", row.title)
        assertTrue(row.handled)
    }

    @Test fun markingSomethingSeenTwiceOrSomethingUnknownDoesNothing() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        assertTrue(actions.acknowledge(scam.key))
        assertFalse(actions.acknowledge(scam.key))
        assertFalse(actions.acknowledge(FindingKey(DetectorId.Link, Problem.ScamLink, "nothing.example")))
        assertEquals(1, history.entries.count { it.outcome == Outcome.Acknowledged })
    }

    @Test fun theSameScamComingBackAfterBeingSeenWarnsAgain() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        actions.acknowledge(scam.key)
        assertEquals(1, pipeline.handle(listOf(scam), setOf(DetectorId.Link)).alerted)
        assertEquals(2, owner.shown.size)
    }

    @Test fun eachProblemHasTheFixThatMatchesHowItClears() {
        // Event findings never clear by themselves, so they must be acknowledgeable; setting findings clear when fixed.
        val eventProblems = Problem.entries.filter { it.fix == Fix.Acknowledge || it.fix == Fix.SettingsThenAcknowledge || it.fix == Fix.ConfirmEvent }
        assertEquals(setOf(Problem.ScamLink, Problem.LookalikeLink, Problem.SiteNotifications, Problem.NewWebsiteNotifications), eventProblems.toSet())
        assertTrue(Problem.entries.filter { it.severity >= com.aegida.core.model.Severity.NeedsTap }.none { it.fix == Fix.None })
        assertTrue(Problem.entries.filter { it.severity == com.aegida.core.model.Severity.Info }.all { it.fix == Fix.None })
    }

    @Test fun sayingAChangeWasHersMovesTheBaselineAndClearsTheQuestion() = runTest {
        baselines.value = Baseline.of(phone)
        phone = phone.copy(browser = "org.other.browser")
        val changed = Finding(FindingKey(DetectorId.Drift, Problem.BrowserChanged, "org.other.browser"), emptyMap(), now, now)
        pipeline.handle(listOf(changed), setOf(DetectorId.Drift))

        assertTrue(actions.acceptChange(changed.key))

        assertEquals("org.other.browser", baselines.value!!.browser)
        assertEquals(FindingState.Acknowledged, findings.get(changed.key)!!.state)
        assertFalse(actions.acceptChange(changed.key)) // nothing open any more
    }

    private val newSite = "http://localhost:8765"
    private suspend fun aNewWebsite(): Finding {
        baselines.value = Baseline.of(phone)
        val f = Finding(FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, newSite), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.PopupSpam))
        return f
    }

    @Test fun aNewWebsiteIsToldToTheGuardianAsAQuestionAndHidden() = runTest { // Kevin, Oct 9
        val f = aNewWebsite()
        assertEquals(listOf("A website that was not there before started sending notifications on Ruth's phone. Aegida asked Ruth: “Was that you?”"), http.bodies)
        assertEquals(listOf(f.key), owner.shown)
    }

    @Test fun sayingANewWebsiteWasHersLetsItThroughNextTimeAndTheGuardianHearsIt() = runTest {
        val f = aNewWebsite()
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(setOf(newSite), baselines.value!!.websites)
        assertEquals(FindingState.Acknowledged, findings.get(f.key)!!.state)
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
    }

    @Test fun onlyAWasThatYouQuestionCanBeAcceptedIntoTheBaseline() = runTest {
        baselines.value = Baseline.of(phone)
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        assertFalse(actions.acceptChange(scam.key))
        assertEquals(FindingState.Open, findings.get(scam.key)!!.state)
    }

    private suspend fun listenerAdded(): Finding {
        baselines.value = Baseline.of(phone)
        phone = phone.copy(notificationListeners = setOf("org.spy.app"))
        val f = Finding(FindingKey(DetectorId.Drift, Problem.ListenerAdded, "org.spy.app"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift))
        http.posts.clear(); http.bodies.clear()
        return f
    }

    @Test fun whenSheSaysItWasHersTheGuardianWhoWasToldHearsThat() = runTest {
        val f = listenerAdded()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. If you were not expecting it, please call Ruth."), http.bodies)
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.Acknowledged, Outcome.GuardianTold), history.entries.map { it.outcome })
    }

    @Test fun aGuardianWhoWasNeverToldAboutItHearsNothingAboutHerAnswer() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false) // alerts were off when it happened
        baselines.value = Baseline.of(phone)
        phone = phone.copy(browser = "org.other.browser")
        val f = Finding(FindingKey(DetectorId.Drift, Problem.BrowserChanged, "org.other.browser"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift))
        assertEquals(0, http.bodies.size)
        guardians.value = guardians.value.copy(alertsOn = true) // switched on before she answered
        assertTrue(actions.acceptChange(f.key))
        assertEquals(0, http.bodies.size)
    }

    @Test fun aChangedDefaultAppIsToldToTheGuardianAndHerAnswerIsFollowedUp() = runTest { // findings #72
        baselines.value = Baseline.of(phone)
        phone = phone.copy(browser = "org.other.browser")
        val f = Finding(FindingKey(DetectorId.Drift, Problem.BrowserChanged, "org.other.browser"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift))
        assertEquals(listOf("The app that opens websites on Ruth's phone was changed. Aegida asked Ruth: “Was that you?”"), http.bodies)
        http.bodies.clear()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
    }

    @Test fun withGuardianAlertsOffNothingIsSent() = runTest {
        val f = listenerAdded()
        guardians.value = guardians.value.copy(alertsOn = false)
        assertTrue(actions.acceptChange(f.key))
        assertEquals(0, http.bodies.size)
    }

    @Test fun aFollowUpThatCannotBeSentIsKeptForRetryAndShownInHistory() = runTest {
        val f = listenerAdded()
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        assertTrue(actions.acceptChange(f.key))
        assertEquals(1, outbox.rows.size)
        assertEquals(Outcome.GuardianNotReached, history.entries.last().outcome)
    }

    @Test fun justSeeingAScamWarningSendsNoFollowUp() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        http.posts.clear(); http.bodies.clear()
        actions.acknowledge(scam.key)
        assertEquals(0, http.bodies.size)
    }

    private suspend fun newAppSideloaded(): Finding {
        baselines.value = Baseline.of(phone)
        phone = phone.copy(apps = listOf(com.aegida.core.model.InstalledApp("org.new.app", "New", null, false)))
        val f = Finding(FindingKey(DetectorId.NewApp, Problem.NewAppOutsidePlay, "org.new.app"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.NewApp))
        http.posts.clear(); http.bodies.clear()
        return f
    }

    @Test fun aNeedsTapChangeGetsTheCalmAllClearButAnUrgentOneNever() = runTest {
        val calm = newAppSideloaded()
        assertTrue(actions.acceptChange(calm.key))
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
    }

    @Test fun noAllClearIsSentAboutSomethingTheGuardianWasNeverToldAbout() = runTest { // findings #50
        newAppSideloaded()
        // a second finding of the same kind a moment later is held back by the repeat back-off ...
        val second = Finding(FindingKey(DetectorId.NewApp, Problem.NewAppOutsidePlay, "org.second.app"), emptyMap(), now.plusSeconds(60), now.plusSeconds(60))
        phone = phone.copy(apps = phone.apps + com.aegida.core.model.InstalledApp("org.second.app", "Second", null, false))
        pipeline.handle(listOf(second), setOf(DetectorId.NewApp))
        assertEquals(0, http.bodies.size)
        // ... so when she says it was hers the guardian, who never heard of it, hears nothing
        assertTrue(actions.acceptChange(second.key))
        assertEquals(0, http.bodies.size)
    }

    @Test fun noAllClearWhileTheOriginalAlarmIsStillWaitingInTheOutbox() = runTest { // findings #50
        baselines.value = Baseline.of(phone)
        phone = phone.copy(notificationListeners = setOf("org.spy.app"))
        val f = Finding(FindingKey(DetectorId.Drift, Problem.ListenerAdded, "org.spy.app"), emptyMap(), now, now)
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift))
        assertEquals(1, outbox.rows.size)
        http.result = com.aegida.core.net.HttpResult.Ok(200, "{}")
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(0, http.bodies.size)
        assertEquals(1, outbox.rows.size) // the alarm is still queued and will go out as it was
    }

    @Test fun noAllClearWhileALaterMessageAboutItIsStillQueued() = runTest { // findings #50: the reminder must not land after the all-clear
        baselines.value = Baseline.of(phone)
        phone = phone.copy(notificationListeners = setOf("org.spy.app"))
        val f = Finding(FindingKey(DetectorId.Drift, Problem.ListenerAdded, "org.spy.app"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift)) // delivered
        findings.rows[f.key] = findings.rows.getValue(f.key).copy(firstSeen = now.minus(Duration.ofHours(25)))
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift)) // the day-old reminder cannot be sent: queued
        assertEquals(1, outbox.rows.size)
        http.result = com.aegida.core.net.HttpResult.Ok(200, "{}")
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(0, http.bodies.size)
    }
}
