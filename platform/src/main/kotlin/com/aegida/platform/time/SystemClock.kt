package com.aegida.platform.time

import com.aegida.core.time.Clock
import java.time.Instant
import javax.inject.Inject

/** The one real clock (ClockSingleOwnerTest). */
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = Instant.now()
}
