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

data class HistoryEntry(val at: Instant, val problem: Problem, val outcome: Outcome)

enum class Outcome(val forMom: String) {
    OwnerWarned("You were warned."),
    GuardianTold("Your guardian was told."),
    GuardianNotReached("Aegida could not reach your guardian."),
    GuardianNeverReached("Aegida could not reach your guardian, and gave up."),
    GuardianReminded("Your guardian was reminded."),
    Fixed("Fixed."),
    Acknowledged("You saw it."),
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
) {
    companion object {
        val None = Guardians(alertsOn = false, ownerName = "", firstTopic = null)
    }
}
