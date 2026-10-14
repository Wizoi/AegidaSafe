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
    override suspend fun prune(before: Instant, notAfter: Instant): Int =
        rows.values.filter { (it.state == FindingState.Resolved || it.state == FindingState.Acknowledged) && (it.lastSeen.isBefore(before) || it.lastSeen.isAfter(notAfter)) }
            .map { rows.remove(it.key) }.size
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

