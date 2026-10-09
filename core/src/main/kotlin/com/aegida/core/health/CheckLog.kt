package com.aegida.core.health

import java.time.Instant

/** One run of the Evaluator. HealthDetector reads the latest daily one to notice a phone that went quiet. */
data class CheckRun(
    val trigger: Trigger,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

enum class Trigger { AppStart, Daily, PackageChanged, NotificationPosted, SettingChanged }

/** Stored by :store (the only SQL). */
interface CheckLog {
    suspend fun record(run: CheckRun)
    suspend fun latest(): CheckRun?
    suspend fun latest(trigger: Trigger): CheckRun?

    /** Deletes runs started before [before], except the newest Daily one (HealthDetector needs it). Returns how many went. */
    suspend fun prune(before: Instant): Int
}
