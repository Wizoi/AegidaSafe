package com.aegida.testkit

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.CheckLog
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.Setup
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import java.time.Instant

// One fake per port, shared by every module's tests (docs/code-health.md). Each behaves like the real store (CLAUDE.md):
// Room REPLACE is one row per key, prune removes by age.

class FakeSetupStore(var value: Setup = Setup.NotDone) : SetupStore {
    override suspend fun setup() = value
    override suspend fun save(setup: Setup) { value = setup }
}

/** Like the file store: keeps what it was given, decoded the same way. */
class FakeFeedStore(var value: ThreatFeed? = null) : FeedStore {
    var saves = 0
    var savedBytes: ByteArray? = null
    override suspend fun current() = value
    override suspend fun save(verifiedFeed: ByteArray) { value = FeedCodec.decode(verifiedFeed); savedBytes = verifiedFeed; saves++ }
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

/** Behaves like the real log (RoomNoticeLog): the same notice coming back is not new, an update within NoticeRules.SAME_NOTICE neither. */
class FakeNoticeLog : com.aegida.core.notice.NoticeLog {
    data class Row(val packageName: String, val key: String, val whenMillis: Long, val at: java.time.Instant, val signals: Int)
    val rows = mutableListOf<Row>()
    override suspend fun record(packageName: String, key: String, whenMillis: Long, at: java.time.Instant, signals: Int) {
        if (rows.any { it.key == key && it.whenMillis == whenMillis }) return
        val last = rows.filter { it.key == key }.maxOfOrNull { it.at }
        val gap = last?.let { java.time.Duration.between(it, at) }
        if (gap != null && !gap.isNegative && gap < com.aegida.core.notice.NoticeRules.SAME_NOTICE) return
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
