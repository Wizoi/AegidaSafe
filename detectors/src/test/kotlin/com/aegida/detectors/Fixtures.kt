package com.aegida.detectors

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.CheckLog
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import java.time.Duration
import java.time.Instant

/** Shared test data. Dates are relative to now. */
object Fixtures {
    val now: Instant = Instant.now()

    fun snapshot(
        privateDns: PrivateDns = PrivateDns.Automatic,
        listenerEnabled: Boolean = true,
        canPostNotifications: Boolean = true,
        browser: String? = "com.android.chrome",
        smsApp: String? = "com.google.android.apps.messaging",
        dialerApp: String? = "com.google.android.dialer",
        notificationListeners: Set<String> = emptySet(),
        accessibilityApps: Set<String> = emptySet(),
        deviceAdmins: Set<String> = emptySet(),
        assistant: String? = "com.google.android.googlequicksearchbox",
    ) = Snapshot(
        now, privateDns, browser, listenerEnabled, canPostNotifications, Reading.NotPresent, Reading.NotPresent,
        smsApp, dialerApp, notificationListeners, accessibilityApps, deviceAdmins, assistant = assistant,
    )

    fun feed(age: Duration = Duration.ofHours(3), hosts: List<String> = listOf("evil.example")) =
        ThreatFeed.of(now.minus(age), listOf(FeedSource("test", "test", hosts.size)), hosts, emptyList())

    fun inputs(
        snapshot: Snapshot = snapshot(),
        setup: Setup = Setup.NotDone,
        feed: ThreatFeed? = feed(),
        event: NotificationEvent? = null,
        lastDailyCheck: CheckRun? = CheckRun(Trigger.Daily, now.minus(Duration.ofHours(20)), now.minus(Duration.ofHours(20))),
        baseline: com.aegida.core.model.Baseline? = null,
    ) = Inputs(snapshot, now, setup, feed, event, lastDailyCheck, baseline = baseline)

    /** Posted with `cmd notification post` on the Aegida36 emulator (channel shell_cmd, package com.android.shell). */
    fun message(text: String, title: String = "Bank alert") =
        NotificationEvent("0|com.android.shell|0|spike1|2000", "com.android.shell", "shell_cmd", title, text, now)

    /** The fixture site's notification exactly as Chrome posted it on the emulator (findings #18). */
    val chromeSiteNotification = NotificationEvent(
        key = "0|com.android.chrome|-1|p#http://localhost:8765/#1aegida-fixture|10150",
        packageName = "com.android.chrome",
        channelId = "web:http://localhost:8765;13435952006713133",
        title = "VIRUS DETECTED! Your phone is infected",
        text = "Tap now to clean your device (Aegida test fixture)",
        postedAt = now,
    )
}

class FakeSetupStore(var value: Setup = Setup.NotDone) : SetupStore {
    override suspend fun setup() = value
    override suspend fun save(setup: Setup) { value = setup }
}

/** Like the file store: keeps what it was given, decoded the same way. */
class FakeFeedStore(var value: ThreatFeed? = null) : FeedStore {
    var saves = 0
    override suspend fun current() = value
    override suspend fun save(verifiedFeed: ByteArray) { value = FeedCodec.decode(verifiedFeed); saves++ }
}

/** Behaves like the Room log: appends, and latest means most recently started. */
class FakeCheckLog : CheckLog {
    val runs = mutableListOf<CheckRun>()
    override suspend fun record(run: CheckRun) { runs += run }
    override suspend fun latest() = runs.maxByOrNull { it.startedAt }
    override suspend fun latest(trigger: Trigger) = runs.filter { it.trigger == trigger }.maxByOrNull { it.startedAt }
    override suspend fun prune(before: Instant): Int {
        val keep = latestDaily()
        val old = runs.filter { it.startedAt.isBefore(before) && it !== keep }
        runs.removeAll(old)
        return old.size
    }
    private fun latestDaily() = runs.filter { it.trigger == Trigger.Daily }.maxByOrNull { it.startedAt }
}

class FakeOutbox : com.aegida.core.alert.Outbox {
    val rows = mutableListOf<com.aegida.core.alert.QueuedMessage>()
    private var next = 1L
    override suspend fun add(message: com.aegida.core.alert.QueuedMessage) { rows += message.copy(id = next++) }
    override suspend fun all() = rows.sortedBy { it.queuedAt }
    override suspend fun remove(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun failedAgain(id: Long, at: java.time.Instant) {
        val i = rows.indexOfFirst { it.id == id }
        rows[i] = rows[i].copy(attempts = rows[i].attempts + 1, lastTriedAt = at)
    }
}

class FakeBaselines(var value: com.aegida.core.model.Baseline? = null) : com.aegida.core.store.BaselineStore {
    override suspend fun baseline() = value
    override suspend fun save(baseline: com.aegida.core.model.Baseline) { value = baseline }
}

/** Behaves like the real log: every call is a notice (the ten-minute update rule is the store's, tested there). */
class FakeNoticeLog : com.aegida.core.notice.NoticeLog {
    val rows = mutableListOf<Triple<String, java.time.Instant, Int>>()
    override suspend fun record(packageName: String, key: String, at: java.time.Instant, signals: Int) { rows += Triple(packageName, at, signals) }
    override suspend fun since(packageName: String, from: java.time.Instant) =
        rows.filter { it.first == packageName && !it.second.isBefore(from) }.map { com.aegida.core.notice.NoticeRow(it.second, it.third) }
    override suspend fun prune(before: java.time.Instant): Int { val n = rows.size; rows.removeAll { it.second.isBefore(before) }; return n - rows.size }
}

class FakeAllowList : com.aegida.core.notice.AllowList {
    val items = mutableListOf<com.aegida.core.notice.AllowedItem>()
    override suspend fun add(item: com.aegida.core.notice.AllowedItem) { items.removeAll { it.problem == item.problem && it.subject == item.subject }; items += item }
    override suspend fun remove(problem: com.aegida.core.text.Problem, subject: String) { items.removeAll { it.problem == problem && it.subject == subject } }
    override suspend fun all() = items.toList()
}
