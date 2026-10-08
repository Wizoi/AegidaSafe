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
    ) = Snapshot(now, privateDns, "com.android.chrome", listenerEnabled, canPostNotifications, Reading.NotPresent, Reading.NotPresent)

    fun feed(age: Duration = Duration.ofHours(3), hosts: List<String> = listOf("evil.example")) =
        ThreatFeed.of(now.minus(age), listOf(FeedSource("test", "test", hosts.size)), hosts, emptyList())

    fun inputs(
        snapshot: Snapshot = snapshot(),
        setup: Setup = Setup.NotDone,
        feed: ThreatFeed? = feed(),
        event: NotificationEvent? = null,
        lastDailyCheck: CheckRun? = CheckRun(Trigger.Daily, now.minus(Duration.ofHours(20)), now.minus(Duration.ofHours(20))),
    ) = Inputs(snapshot, now, setup, feed, event, lastDailyCheck)

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
}
