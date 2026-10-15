package com.aegida.platform.time

import com.aegida.core.time.Clock
import java.time.Instant
import javax.inject.Inject

/** The one real clock (ClockSingleOwnerTest). */
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()

    override fun bootedAt(): Instant = Instant.now().minusMillis(android.os.SystemClock.elapsedRealtime())

    override fun processStartedAt(): Instant =
        Instant.now().minusMillis(android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime())
}
