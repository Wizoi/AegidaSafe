package com.aegida.core.oem

/**
 * Readings that exist only on some phone makers' builds. The one implementation lives in :platform
 * and is the only place allowed to know any maker's names (OemSingleOwnerTest). Detectors never assume
 * one of these exists.
 */
interface OemSource {
    /** The maker's app-install blocker. Unvalidated, so the real source never reports [Reading.Present]. */
    fun installBlocker(): Reading<Boolean>

    /** Whether Aegida is on the maker's "never put to sleep" list. */
    fun neverSleeping(): Reading<Boolean>
}

sealed interface Reading<out T> {
    data class Present<T>(val value: T) : Reading<T>

    /** This phone does not have the feature (for example a Pixel or the emulator). */
    data object NotPresent : Reading<Nothing>

    /** The feature may exist but Aegida cannot read it. Shown as "can't check", never as on or off. */
    data object Unreadable : Reading<Nothing>
}
