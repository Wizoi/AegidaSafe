package com.aegida.store

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
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
        dao.add(HistoryEntity(atMillis = entry.at.toEpochMilli(), historyProblem = entry.problem.name, outcome = entry.outcome.name))

    override suspend fun since(from: Instant): List<HistoryEntry> = dao.since(from.toEpochMilli()).map { row ->
        HistoryEntry(Instant.ofEpochMilli(row.atMillis), Problem.valueOf(row.historyProblem), Outcome.valueOf(row.outcome))
    }

    override suspend fun prune(before: Instant): Int = dao.prune(before.toEpochMilli())
}

/** Setup and guardian choices, stored as named values. */
internal class RoomSettings(private val dao: SettingDao) : SetupStore, GuardianSettings {
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
            momName = v[MOM_NAME].orEmpty(),
            firstTopic = v[FIRST_TOPIC]?.takeIf { it.isNotEmpty() }?.let(::NtfyTopic),
            firstGuardianName = v[GUARDIAN_NAME].orEmpty(),
            firstGuardianEmail = v[GUARDIAN_EMAIL].orEmpty(),
            firstGuardianPhone = v[GUARDIAN_PHONE].orEmpty(),
        )
    }

    override suspend fun save(guardians: Guardians) = dao.put(
        listOf(
            SettingEntity(ALERTS_ON, guardians.alertsOn.toString()),
            SettingEntity(MOM_NAME, guardians.momName),
            SettingEntity(FIRST_TOPIC, guardians.firstTopic?.value.orEmpty()),
            SettingEntity(GUARDIAN_NAME, guardians.firstGuardianName),
            SettingEntity(GUARDIAN_EMAIL, guardians.firstGuardianEmail),
            SettingEntity(GUARDIAN_PHONE, guardians.firstGuardianPhone),
        ),
    )

    private companion object {
        const val FILTER_HOST = "setup.websiteFilterHost"
        const val ALLOWED_SITES = "setup.allowedSites"
        const val ALERTS_ON = "guardians.alertsOn"
        const val MOM_NAME = "guardians.momName"
        const val FIRST_TOPIC = "guardians.firstTopic"
        const val GUARDIAN_NAME = "guardians.firstName"
        const val GUARDIAN_EMAIL = "guardians.firstEmail"
        const val GUARDIAN_PHONE = "guardians.firstPhone"
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
        ),
    )

    override suspend fun all(): List<QueuedMessage> = dao.all().map {
        QueuedMessage(
            it.rowId, it.topic, it.title, it.body, it.urgent, Problem.valueOf(it.outboxProblem),
            Instant.ofEpochMilli(it.queuedAtMillis), it.attempts, Instant.ofEpochMilli(it.lastTriedMillis),
        )
    }

    override suspend fun remove(id: Long) = dao.remove(id)
    override suspend fun failedAgain(id: Long, at: Instant) = dao.failedAgain(id, at.toEpochMilli())
}
