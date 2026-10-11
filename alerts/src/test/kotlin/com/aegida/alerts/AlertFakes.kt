package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.CheckLog
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Setup
import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import java.time.Instant

// Fakes that behave like the real thing (CLAUDE.md): Room REPLACE is one row per key, prune removes by age.

class Findings : FindingStore {
    val rows = linkedMapOf<FindingKey, Finding>()
    override suspend fun open() = rows.values.filter { it.state.needsHer }
    override suspend fun get(key: FindingKey) = rows[key]
    override suspend fun put(finding: Finding) { rows[finding.key] = finding }
    override suspend fun prune(before: Instant): Int =
        rows.values.filter { it.state == FindingState.Resolved && it.lastSeen.isBefore(before) }.map { rows.remove(it.key) }.size
}

class Owner : OwnerNotifier {
    val shown = mutableListOf<FindingKey>()
    val cleared = mutableListOf<FindingKey>()
    override fun show(finding: Finding) { shown += finding.key }
    override fun clear(key: FindingKey) { cleared += key }
}

class History : HistoryLog {
    val entries = mutableListOf<HistoryEntry>()
    override suspend fun add(entry: HistoryEntry) { entries += entry }
    override suspend fun since(from: Instant) = entries.filter { !it.at.isBefore(from) }.sortedByDescending { it.at }
    override suspend fun prune(before: Instant): Int {
        val old = entries.filter { it.at.isBefore(before) }
        entries.removeAll(old)
        return old.size
    }
}

class Http(var result: HttpResult = HttpResult.Ok(200, "{}")) : HttpClient {
    val bodies = mutableListOf<String>()
    val posts = mutableListOf<Triple<Endpoint, String, Map<String, String>>>()
    val deleted = mutableListOf<Endpoint>()
    /** Runs inside every post, so a test can make a send take long enough for another run to start. */
    var onPost: suspend () -> Unit = {}
    var feedResult: (Endpoint) -> BytesResult = { BytesResult.Failed(null, com.aegida.core.net.FailureReason.Offline) }
    override suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult = feedResult(endpoint)
    override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
        // Like OkHttp: a header value with a control or non-ASCII character is refused, with an exception, not an HTTP error.
        headers.forEach { (name, value) -> require(value.all { it == '\t' || it.code in 0x20..0x7e }) { "Unexpected char in header $name" } }
        onPost()
        bodies += body
        posts += Triple(endpoint, body, headers)
        return result
    }
    override suspend fun delete(endpoint: Endpoint): HttpResult {
        deleted += endpoint
        return result
    }
}

class FakeGuardians(var value: Guardians = Guardians.None) : GuardianSettings {
    override suspend fun current() = value
    override suspend fun save(guardians: Guardians) { value = guardians }
}

class FakeSetupStore(var value: Setup = Setup.NotDone) : SetupStore {
    override suspend fun setup() = value
    override suspend fun save(setup: Setup) { value = setup }
}

class FakeFeedStore(var value: ThreatFeed? = null) : FeedStore {
    override suspend fun current() = value
    override suspend fun save(verifiedFeed: ByteArray) { value = com.aegida.core.feed.FeedCodec.decode(verifiedFeed) }
}

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

/** Behaves like the real log (RoomNoticeLog): the same notice coming back is not new, an update within NoticeRules.SAME_NOTICE neither. */
class FakeNoticeLog : com.aegida.core.notice.NoticeLog {
    data class Row(val packageName: String, val key: String, val whenMillis: Long, val at: java.time.Instant, val signals: Int)
    val rows = mutableListOf<Row>()
    override suspend fun record(packageName: String, key: String, whenMillis: Long, at: java.time.Instant, signals: Int) {
        if (rows.any { it.key == key && it.whenMillis == whenMillis }) return
        val last = rows.filter { it.key == key }.maxOfOrNull { it.at }
        if (last != null && java.time.Duration.between(last, at) < com.aegida.core.notice.NoticeRules.SAME_NOTICE) return
        rows += Row(packageName, key, whenMillis, at, signals)
    }
    override suspend fun since(packageName: String, from: java.time.Instant) =
        rows.filter { it.packageName == packageName && !it.at.isBefore(from) }.sortedBy { it.at }.map { com.aegida.core.notice.NoticeRow(it.at, it.signals) }
    override suspend fun prune(before: java.time.Instant, notAfter: java.time.Instant): Int {
        val n = rows.size
        rows.removeAll { it.at.isBefore(before) || it.at.isAfter(notAfter) }
        return n - rows.size
    }
}

class FakeAllowList : com.aegida.core.notice.AllowList {
    val items = mutableListOf<com.aegida.core.notice.AllowedItem>()
    override suspend fun add(item: com.aegida.core.notice.AllowedItem) { items.removeAll { it.problem == item.problem && it.subject == item.subject }; items += item }
    override suspend fun remove(problem: com.aegida.core.text.Problem, subject: String) { items.removeAll { it.problem == problem && it.subject == subject } }
    override suspend fun all() = items.toList()
}
