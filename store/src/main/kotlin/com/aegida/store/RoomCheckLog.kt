package com.aegida.store

import com.aegida.core.health.CheckLog
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import java.time.Instant

internal class RoomCheckLog(private val dao: CheckRunDao) : CheckLog {

    override suspend fun record(run: CheckRun) {
        dao.insert(
            CheckRunEntity(
                trigger = run.trigger.name,
                startedAtMillis = run.startedAt.toEpochMilli(),
                finishedAtMillis = run.finishedAt?.toEpochMilli(),
            ),
        )
    }

    override suspend fun latest(): CheckRun? = dao.latest()?.toCheckRun()

    override suspend fun latest(trigger: Trigger): CheckRun? = dao.latest(trigger.name)?.toCheckRun()

    override suspend fun prune(before: Instant): Int = dao.deleteOlderThan(before.toEpochMilli(), Trigger.Daily.name)

    private fun CheckRunEntity.toCheckRun() = CheckRun(
        trigger = Trigger.valueOf(trigger),
        startedAt = Instant.ofEpochMilli(startedAtMillis),
        finishedAt = finishedAtMillis?.let(Instant::ofEpochMilli),
    )
}
