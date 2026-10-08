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

    override suspend fun latest(): CheckRun? = dao.latest()?.let { row ->
        CheckRun(
            trigger = Trigger.valueOf(row.trigger),
            startedAt = Instant.ofEpochMilli(row.startedAtMillis),
            finishedAt = row.finishedAtMillis?.let(Instant::ofEpochMilli),
        )
    }
}
