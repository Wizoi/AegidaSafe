package com.aegida.core.time

import java.time.Instant

/** The only source of "now". The real one lives in :platform; tests pass a fake (ClockSingleOwnerTest). */
fun interface Clock {
    fun now(): Instant
}
