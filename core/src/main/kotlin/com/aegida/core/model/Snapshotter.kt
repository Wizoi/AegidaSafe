package com.aegida.core.model

/**
 * The one way to read the phone into a [Snapshot]. The baseline and the current reading both come from
 * here, so there is no second reading path (design section 1). Implemented in :platform.
 */
fun interface Snapshotter {
    /**
     * [alsoApps]: packages this check is about (the app that just posted a notice) that must be in [Snapshot.apps] even when the
     * kept app list was read before they were installed, so a warning can name the app (#164). Roles and access lists are always
     * filled this way.
     */
    fun take(alsoApps: Set<String>): Snapshot

    fun take(): Snapshot = take(emptySet())
}
