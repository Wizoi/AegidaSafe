package com.aegida.core.alert

import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import java.time.Instant

/** Findings with their state, one row per FindingKey. Implemented by :store. */
interface FindingStore {
    suspend fun open(): List<Finding>
    suspend fun get(key: FindingKey): Finding?
    suspend fun put(finding: Finding)

    /**
     * Deletes resolved and seen findings last seen before [before] or after [notAfter] (a clock that was ahead); open ones stay.
     * Returns how many. Seen ones go too, or a scam host or a site she answered would be kept forever (findings #94).
     */
    suspend fun prune(before: java.time.Instant, notAfter: java.time.Instant): Int
}

/** Heads-up banners on the owner's phone. Implemented by NotificationPoster (the only notification owner). */
interface OwnerNotifier {
    fun show(finding: Finding)
    fun clear(key: FindingKey)
}

/** What happened, newest first, kept about 90 days. Implemented by :store. */
interface HistoryLog {
    suspend fun add(entry: HistoryEntry)

    /** Newest first. */
    suspend fun since(from: Instant): List<HistoryEntry>

    /** Removes entries older than [before]; returns how many were removed. */
    suspend fun prune(before: Instant): Int
}

data class HistoryEntry(val at: Instant, val problem: Problem, val outcome: Outcome, val detail: String? = null)

enum class Outcome(val forMom: String) {
    OwnerWarned("You were warned."),
    GuardianTold("Your guardian was told."),
    GuardianNotReached("Aegida could not reach your guardian."),
    GuardianNeverReached("Aegida could not reach your guardian, and gave up."),
    GuardianReminded("Your guardian was reminded."),
    Fixed("Fixed."),
    /** The question closed though nothing on the phone changed: Aegida learned the app was fine (Kevin, Oct 14). */
    NoLongerAConcern("No longer a concern."),
    Acknowledged("You saw it."),
    /** A new-app question closed because a newer, urgent warning about the same app took it over (findings #118, #129). */
    SeeNewerWarning("See the newer warning."),
}

/** Guardian alerting as the owner set it up. Implemented by :store. */
interface GuardianSettings {
    suspend fun current(): Guardians
    suspend fun save(guardians: Guardians)
}

data class Guardians(
    /** the owner turned on "Let Aegida tell my guardians about serious problems". */
    val alertsOn: Boolean,
    val ownerName: String,
    /** The first guardian's private topic, or null before pairing. */
    val firstTopic: NtfyTopic?,
    val firstGuardianName: String = "",
    val firstGuardianEmail: String = "",
    /** Optional; "Call a guardian" opens the dialer with it (no call permission needed). */
    val firstGuardianPhone: String = "",
    /**
     * When the current guardian was picked, if it was changed after setup (null: the guardian since setup). A message sent
     * before this went to someone else, so the repeat back-off does not count it (findings #75).
     */
    val pairedAt: java.time.Instant? = null,
    /** The topic whose "set up" message has not gone out yet (offline), kept so a restart does not lose it (findings #95); never sent to another guardian. */
    val welcomePendingFor: String? = null,
    /** While alerts are off: the waiting "hasn't checked in" message was cancelled, so nothing more is sent (findings #95). */
    val heartbeatCancelled: Boolean = false,
) {
    companion object {
        val None = Guardians(alertsOn = false, ownerName = "", firstTopic = null)
    }
}
