package com.aegida.alerts

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.feed.FeedVerifier
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.Trigger
import com.aegida.core.model.HomeState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.SetupStep
import com.aegida.core.model.Snapshot
import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.oem.Reading
import com.aegida.core.text.Texts
import com.aegida.detectors.DnsDetector
import com.aegida.detectors.Evaluator
import com.aegida.detectors.EvaluatorMemory
import com.aegida.detectors.HealthDetector
import com.aegida.detectors.LinkDetector
import com.aegida.detectors.PopupSpamDetector
import com.aegida.testkit.FakeBaselines
import com.aegida.testkit.FakeCheckLog
import com.aegida.testkit.FakeFeedStore
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.FakeSetupStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A phone from first launch on, wired as the app wires it (AppModule) but with the shared fakes for the stores and the network.
 * Each first-run step is done the way MainViewModel does it (that class needs Hilt and Android, so it is not run here).
 */
internal class WholePhone(network: (Http) -> com.aegida.core.net.HttpClient = { it }) {
    var now: Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val setupStore = FakeSetupStore()
    val feedStore = FakeFeedStore(null)
    val checkLog = FakeCheckLog()
    val findings = Findings()
    val owner = Owner()
    val history = History()
    val http = Http()
    val outbox = FakeOutbox()
    val guardians = FakeGuardians()
    val baselines = FakeBaselines()
    var listenerEnabled = false
    var canPost = false
    var dns: PrivateDns = PrivateDns.Automatic

    private val net = network(http)
    private val sender = NtfySender(net)
    private val gate = AlertGate()
    private val evaluator = Evaluator(
        setOf(LinkDetector(), PopupSpamDetector(), DnsDetector(), HealthDetector()), { now },
        EvaluatorMemory(setupStore, feedStore, checkLog, outbox, baselines),
    )
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, sender, outbox)) { now }
    private val switch = DeadMansSwitch(guardians, sender) { now }
    val runner = CheckRunner(
        { Snapshot(now, dns, "com.android.chrome", listenerEnabled, canPost, Reading.NotPresent, Reading.NotPresent) },
        evaluator, pipeline, BaselineKeeper(setupStore, baselines), OutboxFlusher(outbox, sender, history, guardians, { now }), CheckGate(gate),
    )
    val guardianAlerts = GuardianAlerts(guardians, sender, switch, gate)
    val daily = DailyCheck(
        DailyFeed(FeedUpdater(net, FeedVerifier(keys.public.encoded), feedStore) { now }, feedStore) { now },
        runner,
        DailyPruning(history, findings, checkLog) { now },
        DailyHeartbeat(switch, guardianAlerts, gate),
        checkLog,
    )

    /** The scam list as the feed tool signs it, served by the fake network. */
    fun publishFeed() {
        val feed = ThreatFeed.of(now.minusSeconds(3600), listOf(FeedSource("test", "test", 1)), List(2_000) { "h$it.example" } + "evil.example", emptyList())
        val bytes = FeedCodec.encode(feed)
        val sig = Signature.getInstance(FeedVerifier.ALGORITHM).run { initSign(keys.private); update(bytes); sign() }
        http.feedResult = { if (it === Endpoint.Feed.SIGNATURE) BytesResult.Ok(sig) else BytesResult.Ok(bytes) }
    }

    /** MainViewModel.refresh: a check, then the first full check if none has run. */
    suspend fun openTheApp() {
        runner.run(Trigger.AppStart)
        daily.runIfNeverRun()
    }

    /** The About step (saveAbout), then the invite step (prepareInvite makes her private name). */
    suspend fun aboutAndInvite(topic: NtfyTopic, owner: String = "Ruth", guardian: String = "Kevin", email: String = "kevin@example.com") {
        guardians.save(guardians.current().copy(ownerName = owner, firstGuardianName = guardian, firstGuardianEmail = email))
        guardians.save(guardians.current().copy(firstTopic = topic))
    }

    /** The invitation's text, as InviteEmail fills it in. */
    suspend fun invitation(): String {
        val g = guardians.current()
        return Texts.Invite.BODY.format(g.firstGuardianName, g.firstTopic!!.value, g.firstTopic!!.value, g.firstTopic!!.value, g.ownerName)
    }

    /** finishStep for every step. */
    suspend fun finishSetup() {
        setupStore.save(setupStore.setup().copy(stepsDone = SetupStep.entries.size, websiteFilterHost = (dns as? PrivateDns.Hostname)?.host))
    }

    suspend fun home() = HomeState.of(findings.open(), checkLog.latest())
}

/** docs/scenarios/first-run.md. */
class FirstRunScenariosTest {
    private val phone = WholePhone()
    private val topic = NtfyTopic("aegida_" + "rT7uY8iO".repeat(4) + "Xw2z")

    @Test fun fromFirstLaunchToTheGuardianReceivingTheFirstMessageAndHomeSayingNoKnownProblems() = runTest {
        phone.publishFeed()
        phone.openTheApp()
        assertEquals("nothing is sent before she has a guardian", emptyList<Any>(), phone.http.posts)
        assertEquals("before setup ends, missing access is not a problem yet, and the lists were downloaded", emptyList<Any>(), phone.findings.open())
        assertTrue("the first full check ran at first launch", phone.feedStore.current() != null)

        phone.aboutAndInvite(topic)
        assertTrue("the invitation carries her private name", phone.invitation().contains(topic.value))
        phone.guardianAlerts.setAlertsOn(true)
        val first = phone.http.posts.first()
        assertEquals("https://ntfy.sh/${topic.value}", first.first.url)
        assertEquals(Texts.Guardian.SET_UP.format("Ruth"), first.second)
        assertEquals("Ruth", first.third["Title"])
        assertEquals("and the hasn't-checked-in message is waiting", "https://ntfy.sh/${topic.value}/heartbeat", phone.http.posts[1].first.url)

        phone.listenerEnabled = true
        phone.canPost = true
        phone.dns = PrivateDns.Hostname("security.cloudflare-dns.com")
        phone.finishSetup()
        phone.runner.run(Trigger.AppStart)
        assertTrue("Home: no known problems found", phone.home() is HomeState.NoKnownProblems)
        assertEquals("the guardian got the set-up message and the waiting heartbeat, nothing else", 2, phone.http.posts.size)
    }

    /**
     * A name given out before finding #110 may hold letters that look alike (I, l, 1, O, 0). It is never changed, so it must
     * still be sent, and spelled in the invitation, letter for letter. New names no longer use those letters.
     */
    @Test fun aPrivateNameWithLookAlikeLettersReachesTheGuardianExactlyAsTheInvitationSpellsIt() = runTest {
        val lookAlike = NtfyTopic("aegida_" + "Il1IO0lO".repeat(5))
        phone.aboutAndInvite(lookAlike)
        phone.guardianAlerts.setAlertsOn(true)
        val path = phone.http.posts.first().first.url.removePrefix("https://ntfy.sh/")
        assertEquals("sent to the very name, letter for letter", lookAlike.value, path)
        assertTrue("the invitation spells the same name, as a name and as both links", phone.invitation().let {
            it.contains("\n   ${lookAlike.value}\n") && it.contains("ntfy://ntfy.sh/${lookAlike.value}") && it.contains("https://ntfy.sh/${lookAlike.value}")
        })
    }

    @Test fun whenSheIsOfflineAtTheAlertsStepTheFirstMessageIsSentByTheNextDailyCheck() = runTest {
        phone.aboutAndInvite(topic)
        phone.http.result = HttpResult.Failed(null, FailureReason.Offline)
        phone.guardianAlerts.setAlertsOn(true)
        phone.http.result = HttpResult.Ok(200, "{}")
        phone.http.posts.clear()
        phone.listenerEnabled = true
        phone.canPost = true
        phone.finishSetup()
        phone.daily.run()
        assertEquals("https://ntfy.sh/${topic.value}", phone.http.posts.first().first.url)
        assertEquals(Texts.Guardian.SET_UP.format("Ruth"), phone.http.posts.first().second)
    }

    @Test fun whenSheSaysNoToTellingHerGuardianNothingIsEverSentToThem() = runTest {
        phone.publishFeed()
        phone.aboutAndInvite(topic)
        phone.guardianAlerts.setAlertsOn(false)
        phone.finishSetup()
        phone.listenerEnabled = true
        phone.canPost = true
        phone.openTheApp()
        phone.daily.run()
        assertEquals(emptyList<Any>(), phone.http.posts)
    }
}
