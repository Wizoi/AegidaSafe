package com.aegida.core.time

import java.time.Instant

/** The only source of "now". The real one lives in :platform; tests pass a fake (ClockSingleOwnerTest). */
fun interface Clock {
    fun now(): Instant

    /** When the phone last started, or null if it is not known (fakes). */
    fun bootedAt(): Instant? = null

    /** When this app process started, or null if it is not known (fakes). Android binds the listener seconds after (finding #136). */
    fun processStartedAt(): Instant? = null
}
