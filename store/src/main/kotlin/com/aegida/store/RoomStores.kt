package com.aegida.store

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.model.Baseline
import com.aegida.core.store.BaselineStore
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Covered
import com.aegida.core.alert.Outbox
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.model.AutoBlockerAnswer
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Setup
import com.aegida.core.net.NtfyTopic
import com.aegida.core.notice.AllowList
import com.aegida.core.notice.AllowedItem
import com.aegida.core.notice.NoticeLog
import com.aegida.core.notice.NoticeRow
import com.aegida.core.notice.NoticeRules
import com.aegida.core.store.SetupStore
import com.aegida.core.text.Problem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

internal class RoomFindingStore(private val dao: FindingDao) : FindingStore {
    override suspend fun open(): List<Finding> = dao.open().map { it.toFinding() }

    override suspend fun get(key: FindingKey): Finding? =
        dao.get(key.detectorId.name, key.problem.name, key.subject)?.toFinding()

    override suspend fun put(finding: Finding) = dao.put(
        FindingEntity(
            detectorId = finding.key.detectorId.name,
            problem = finding.key.problem.name,
            subject = finding.key.subject,
            evidenceJson = Json.encodeToString(finding.evidence),
            firstSeenMillis = finding.firstSeen.toEpochMilli(),
            lastSeenMillis = finding.lastSeen.toEpochMilli(),
            state = finding.state.name,
        ),
    )

    override suspend fun prune(before: Instant, notAfter: Instant): Int = dao.prune(before.toEpochMilli(), notAfter.toEpochMilli())

    private fun FindingEntity.toFinding() = Finding(
        key = FindingKey(DetectorId.valueOf(detectorId), Problem.valueOf(problem), subject),
        evidence = Json.decodeFromString<Map<String, String>>(evidenceJson),
        firstSeen = Instant.ofEpochMilli(firstSeenMillis),
        lastSeen = Instant.ofEpochMilli(lastSeenMillis),
        state = FindingState.valueOf(state),
    )
}

internal class RoomHistoryLog(private val dao: HistoryDao) : HistoryLog {
    override suspend fun add(entry: HistoryEntry) =
        dao.add(HistoryEntity(atMillis = entry.at.toEpochMilli(), historyProblem = entry.problem.name, outcome = entry.outcome.name, detail = entry.detail))

    override suspend fun since(from: Instant): List<HistoryEntry> = dao.since(from.toEpochMilli()).map { row ->
        HistoryEntry(Instant.ofEpochMilli(row.atMillis), Problem.valueOf(row.historyProblem), Outcome.valueOf(row.outcome), row.detail)
    }

    override suspend fun prune(before: Instant): Int = dao.prune(before.toEpochMilli())
}

/** Setup and guardian choices, stored as named values. */
internal class RoomSettings(private val dao: SettingDao) : SetupStore, GuardianSettings, BaselineStore {
    private suspend fun values(): Map<String, String> = dao.all().associate { it.name to it.value }

    override suspend fun setup(): Setup {
        val v = values()
        return Setup(
            websiteFilterHost = v[FILTER_HOST]?.takeIf { it.isNotEmpty() },
            allowedSites = v[ALLOWED_SITES].orEmpty().split('\n').filter { it.isNotEmpty() }.toSet(),
            pictureId = v[PICTURE]?.takeIf { it.isNotEmpty() },
            autoBlocker = v[AUTO_BLOCKER]?.let { name -> AutoBlockerAnswer.entries.firstOrNull { it.name == name } } ?: AutoBlockerAnswer.NotAsked,
            stepsDone = v[STEPS_DONE]?.toIntOrNull() ?: 0,
        )
    }

    override suspend fun save(setup: Setup) = dao.put(
        listOf(
            SettingEntity(FILTER_HOST, setup.websiteFilterHost.orEmpty()),
            SettingEntity(ALLOWED_SITES, setup.allowedSites.sorted().joinToString("\n")),
            SettingEntity(PICTURE, setup.pictureId.orEmpty()),
            SettingEntity(AUTO_BLOCKER, setup.autoBlocker.name),
            SettingEntity(STEPS_DONE, setup.stepsDone.toString()),
        ),
    )

    override suspend fun current(): Guardians {
        val v = values()
        return Guardians(
            alertsOn = v[ALERTS_ON] == "true",
            ownerName = v[MOM_NAME].orEmpty(),
            firstTopic = v[FIRST_TOPIC]?.takeIf { it.isNotEmpty() }?.let(::NtfyTopic),
            firstGuardianName = v[GUARDIAN_NAME].orEmpty(),
            firstGuardianEmail = v[GUARDIAN_EMAIL].orEmpty(),
            firstGuardianPhone = v[GUARDIAN_PHONE].orEmpty(),
            pairedAt = v[GUARDIAN_PAIRED_AT]?.toLongOrNull()?.let(java.time.Instant::ofEpochMilli),
            welcomePendingFor = v[WELCOME_PENDING]?.takeIf { it.isNotEmpty() },
            heartbeatCancelled = v[HEARTBEAT_CANCELLED] == "true",
        )
    }

    override suspend fun save(guardians: Guardians) = dao.put(
        listOf(
            SettingEntity(ALERTS_ON, guardians.alertsOn.toString()),
            SettingEntity(MOM_NAME, guardians.ownerName),
            SettingEntity(FIRST_TOPIC, guardians.firstTopic?.value.orEmpty()),
            SettingEntity(GUARDIAN_NAME, guardians.firstGuardianName),
            SettingEntity(GUARDIAN_EMAIL, guardians.firstGuardianEmail),
            SettingEntity(GUARDIAN_PHONE, guardians.firstGuardianPhone),
            SettingEntity(GUARDIAN_PAIRED_AT, guardians.pairedAt?.toEpochMilli()?.toString().orEmpty()),
            SettingEntity(WELCOME_PENDING, guardians.welcomePendingFor.orEmpty()),
            SettingEntity(HEARTBEAT_CANCELLED, guardians.heartbeatCancelled.toString()),
        ),
    )

    override suspend fun baseline(): Baseline? {
        val v = values()
        if (v[BASELINE_TAKEN] != "true") return null
        fun set(name: String) = v[name].orEmpty().split('\n').filter { it.isNotEmpty() }.toSet()
        return Baseline(
            browser = v[BASELINE_BROWSER]?.takeIf { it.isNotEmpty() },
            smsApp = v[BASELINE_SMS]?.takeIf { it.isNotEmpty() },
            dialerApp = v[BASELINE_DIALER]?.takeIf { it.isNotEmpty() },
            notificationListeners = set(BASELINE_LISTENERS),
            accessibilityApps = set(BASELINE_ACCESSIBILITY),
            deviceAdmins = set(BASELINE_ADMINS),
            assistant = v[BASELINE_ASSISTANT]?.takeIf { it.isNotEmpty() },
            apps = if (v[BASELINE_APPS_TAKEN] == "true") set(BASELINE_APPS) else null,
            acceptedRemoteApps = set(BASELINE_REMOTE_OK),
            websites = if (v[BASELINE_WEBSITES_TAKEN] == "true") set(BASELINE_WEBSITES) else null,
        )
    }

    override suspend fun save(baseline: Baseline) = dao.put(
        listOf(
            SettingEntity(BASELINE_TAKEN, "true"),
            SettingEntity(BASELINE_BROWSER, baseline.browser.orEmpty()),
            SettingEntity(BASELINE_SMS, baseline.smsApp.orEmpty()),
            SettingEntity(BASELINE_DIALER, baseline.dialerApp.orEmpty()),
            SettingEntity(BASELINE_ASSISTANT, baseline.assistant.orEmpty()),
            SettingEntity(BASELINE_LISTENERS, baseline.notificationListeners.sorted().joinToString("\n")),
            SettingEntity(BASELINE_ACCESSIBILITY, baseline.accessibilityApps.sorted().joinToString("\n")),
            SettingEntity(BASELINE_ADMINS, baseline.deviceAdmins.sorted().joinToString("\n")),
            SettingEntity(BASELINE_APPS_TAKEN, (baseline.apps != null).toString()),
            SettingEntity(BASELINE_APPS, baseline.apps.orEmpty().sorted().joinToString("\n")),
            SettingEntity(BASELINE_REMOTE_OK, baseline.acceptedRemoteApps.sorted().joinToString("\n")),
            SettingEntity(BASELINE_WEBSITES_TAKEN, (baseline.websites != null).toString()),
            SettingEntity(BASELINE_WEBSITES, baseline.websites.orEmpty().sorted().joinToString("\n")),
        ),
    )

    private companion object {
        const val BASELINE_TAKEN = "baseline.taken"
        const val BASELINE_BROWSER = "baseline.browser"
        const val BASELINE_SMS = "baseline.smsApp"
        const val BASELINE_DIALER = "baseline.dialerApp"
        const val BASELINE_ASSISTANT = "baseline.assistant"
        const val BASELINE_LISTENERS = "baseline.notificationListeners"
        const val BASELINE_ACCESSIBILITY = "baseline.accessibilityApps"
        const val BASELINE_ADMINS = "baseline.deviceAdmins"
        const val BASELINE_APPS_TAKEN = "baseline.appsTaken"
        const val BASELINE_APPS = "baseline.apps"
        const val BASELINE_REMOTE_OK = "baseline.acceptedRemoteApps"
        const val BASELINE_WEBSITES_TAKEN = "baseline.websitesTaken"
        const val BASELINE_WEBSITES = "baseline.websites"
        const val FILTER_HOST = "setup.websiteFilterHost"
        const val ALLOWED_SITES = "setup.allowedSites"
        const val ALERTS_ON = "guardians.alertsOn"
        const val MOM_NAME = "guardians.ownerName"
        const val FIRST_TOPIC = "guardians.firstTopic"
        const val GUARDIAN_NAME = "guardians.firstName"
        const val GUARDIAN_EMAIL = "guardians.firstEmail"
        const val GUARDIAN_PHONE = "guardians.firstPhone"
        const val GUARDIAN_PAIRED_AT = "guardians.pairedAt"
        const val WELCOME_PENDING = "guardians.welcomePending"
        const val HEARTBEAT_CANCELLED = "guardians.heartbeatCancelled"
        const val PICTURE = "setup.pictureId"
        const val AUTO_BLOCKER = "setup.autoBlocker"
        const val STEPS_DONE = "setup.stepsDone"
    }
}

internal class RoomOutbox(private val dao: OutboxDao) : Outbox {
    override suspend fun add(message: QueuedMessage) = dao.add(
        OutboxEntity(
            topic = message.topic, title = message.title, body = message.body, urgent = message.urgent,
            outboxProblem = message.problem.name, queuedAtMillis = message.queuedAt.toEpochMilli(),
            attempts = message.attempts, lastTriedMillis = message.lastTriedAt.toEpochMilli(),
            outboxDetail = OutboxCodec.encode(message.detail, message.alsoCovers, message.reminder),
        ),
    )

    override suspend fun all(): List<QueuedMessage> = dao.all().map {
        val decoded = OutboxCodec.decode(it.outboxDetail)
        QueuedMessage(
            it.rowId, it.topic, it.title, it.body, it.urgent, Problem.valueOf(it.outboxProblem),
            Instant.ofEpochMilli(it.queuedAtMillis), it.attempts, Instant.ofEpochMilli(it.lastTriedMillis), decoded.detail, decoded.also, decoded.reminder,
        )
    }

    override suspend fun remove(id: Long) = dao.remove(id)
    override suspend fun failedAgain(id: Long, at: Instant) = dao.failedAgain(id, at.toEpochMilli())
}

internal class RoomNoticeLog(private val dao: NoticeDao) : NoticeLog {
    override suspend fun record(packageName: String, key: String, whenMillis: Long, at: Instant, signals: Int) {
        val keyHash = hash(key)
        val noticeHash = hash(key + "|" + whenMillis)
        if (dao.seen(noticeHash) > 0) return // the same notice coming back (a snooze, a repost): not new
        val last = dao.lastAt(keyHash)
        val gap = if (last == null) null else at.toEpochMilli() - last
        // An update a moment later is not a new notice; a negative gap is a clock that went back (findings #96).
        if (gap != null && gap >= 0 && gap < NoticeRules.SAME_NOTICE.toMillis()) return
        dao.add(NoticeEntity(packageName = packageName, keyHash = keyHash, noticeHash = noticeHash, atMillis = at.toEpochMilli(), signals = signals))
    }

    override suspend fun since(packageName: String, from: Instant): List<NoticeRow> =
        dao.since(packageName, from.toEpochMilli()).map { NoticeRow(Instant.ofEpochMilli(it.atMillis), it.signals) }

    override suspend fun prune(before: Instant, notAfter: Instant): Int = dao.prune(before.toEpochMilli(), notAfter.toEpochMilli())

    /** The key is never stored as it is: it can carry an app's own tag (finding #93). */
    private fun hash(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(HASH_BYTES).joinToString("") { "%02x".format(it) }

    private companion object {
        /** The first 12 bytes of the SHA-256: enough to tell notices apart, too little to read the key back. */
        const val HASH_BYTES = 12
    }
}

internal class RoomAllowList(private val dao: AllowedDao) : AllowList {
    override suspend fun add(item: AllowedItem) =
        dao.put(AllowedEntity(item.problem.name, item.subject, item.label, item.at.toEpochMilli()))

    override suspend fun remove(problem: Problem, subject: String) = dao.remove(problem.name, subject)

    override suspend fun all(): List<AllowedItem> =
        dao.all().map { AllowedItem(Problem.valueOf(it.problem), it.subject, it.label, Instant.ofEpochMilli(it.atMillis)) }
}

/**
 * The outbox's one detail column holds the History detail of the held note and, for a reminder that covers several warnings, the
 * others as "kind, detail" pairs (findings #116, #131). Details are plain sentences; the separators are control characters they never contain.
 */
internal object OutboxCodec {
    private const val RECORD = ''
    private const val FIELD = ''
    private const val REMINDER = ''

    class Decoded(val detail: String?, val also: List<Covered>, val reminder: Boolean)

    fun encode(detail: String?, also: List<Covered>, reminder: Boolean = false): String? {
        val body = if (also.isEmpty()) detail
        else (listOf(detail.orEmpty()) + also.map { it.problem.name + FIELD + it.detail.orEmpty() }).joinToString(RECORD.toString())
        return if (reminder) REMINDER + body.orEmpty() else body
    }

    /**
     * Never throws: a row that does not decode (a kind this version does not know, a record cut short) keeps what can be read and loses
     * the rest, so the message is still delivered and one bad row cannot break every check (findings #144).
     */
    fun decode(stored: String?): Decoded {
        val reminder = stored != null && stored.startsWith(REMINDER)
        val text = if (reminder) stored!!.substring(1) else stored
        if (text == null || RECORD !in text) return Decoded(text?.ifEmpty { null }, emptyList(), reminder)
        val parts = text.split(RECORD)
        val also = parts.drop(1).mapNotNull { part ->
            val pieces = part.split(FIELD, limit = 2)
            val problem = pieces.firstOrNull()?.let { name -> Problem.entries.firstOrNull { it.name == name } }
            if (pieces.size != 2 || problem == null) null else Covered(problem, pieces[1].ifEmpty { null })
        }
        return Decoded(parts.first().ifEmpty { null }, also, reminder)
    }
}
