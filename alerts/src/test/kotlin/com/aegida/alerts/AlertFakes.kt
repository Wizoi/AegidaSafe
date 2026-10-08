package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.MomNotifier
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
    override suspend fun open() = rows.values.filter { it.state == FindingState.Open }
    override suspend fun get(key: FindingKey) = rows[key]
    override suspend fun put(finding: Finding) { rows[finding.key] = finding }
}

class Mom : MomNotifier {
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
    var feedResult: (Endpoint) -> BytesResult = { BytesResult.Failed(null, com.aegida.core.net.FailureReason.Offline) }
    override suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult = feedResult(endpoint)
    override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
        bodies += body
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
}
