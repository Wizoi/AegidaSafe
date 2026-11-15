package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.PhoneChange
import com.aegida.core.text.Problem
import com.aegida.core.text.GuardianPhoneWords
import com.aegida.core.time.Clock
import javax.inject.Inject

/** What saving the About details did ([GuardianDetails.saveAbout]). */
enum class AboutSaved {
    /** Names and phone are saved; nothing else was needed. */
    Saved,

    /** The email is a new one for a guardian who already has a channel: the caller makes a new guardian ([GuardianChange]), K9. */
    NewGuardian,
}

/**
 * The one place a name or phone of the guardian is corrected, under [AlertGate] (T5). It never changes the channel (the channel is the
 * password, so only a new email makes a new one: [GuardianChange]) and never tells anyone the owner picked someone else.
 * [fixNames] sends nothing at all. [setPhone] owns K7: the current guardian gets one short note that the number changed, never the number.
 */
class GuardianDetails @Inject constructor(
    private val settings: GuardianSettings,
    private val pipeline: AlertPipeline,
    private val line: GuardianLine,
    private val history: HistoryLog,
    private val gate: AlertGate,
    private val alerts: GuardianAlerts,
    private val clock: Clock,
) {
    /** Her first name and the guardian's name. False (nothing saved) when either is blank. Sends nothing; the channel keeps its spelling (T5.1, T5.3). */
    suspend fun fixNames(owner: String, guardian: String): Boolean = gate.run { fixNamesLocked(owner, guardian) }

    /** Adds, changes or removes the guardian's phone, and tells the current guardian (K7); while alerts are off the note is held (K8b). */
    suspend fun setPhone(phone: String) = gate.run { setPhoneLocked(phone) }

    /**
     * The About step (setup, and Back to it): with no channel yet everything is saved as typed, nobody can be told. With a channel, the same
     * email (any letter case) is the same person: names and phone are fixed and nothing but a phone note is sent. A new email is
     * [AboutSaved.NewGuardian] after the names are saved (K9, T5.18).
     */
    suspend fun saveAbout(owner: String, guardian: String, email: String, phone: String): AboutSaved = gate.run {
        val before = settings.current()
        if (before.firstTopic == null) {
            settings.save(
                before.copy(
                    ownerName = owner.trim(), firstGuardianName = guardian.trim(),
                    firstGuardianEmail = email.trim(), firstGuardianPhone = phone.trim(),
                ),
            )
            return@run AboutSaved.Saved
        }
        if (sameEmail(before.firstGuardianEmail, email)) {
            fixNamesLocked(owner, guardian)
            setPhoneLocked(phone)
            AboutSaved.Saved
        } else {
            // A new person: only her own name is fixed now. The guardian's name, email and phone are saved together with the new channel by
            // [GuardianChange], never the new name onto the old channel, so a failure there leaves the old guardian complete (T5.11, findings #190).
            fixOwnerNameLocked(owner)
            AboutSaved.NewGuardian
        }
    }

    /** The caller holds the [AlertGate] ([GuardianChange]): the same email is the same guardian, so only names and phone are fixed. */
    internal suspend fun sameGuardianLocked(guardian: String, phone: String) {
        val owner = settings.current().ownerName
        fixNamesLocked(owner, guardian)
        setPhoneLocked(phone)
    }

    /**
     * Turns alerts on or off in one [AlertGate] hold. Going on also sends the phone note held while they were off (K8b, K7.8), in the same
     * hold, so no check can run between, and the note is forgotten only after it was sent or queued in the outbox: a kill in between leaves it
     * held, and [sendHeldPhoneNote] (the daily check) sends it (findings #187).
     */
    suspend fun setAlertsOn(on: Boolean): AlertsSwitched = gate.run {
        val switched = alerts.switchAlerts(on)
        if (on) sendHeldPhoneNoteLocked()
        switched
    }

    /** The phone note held while alerts were off, if alerts are on now and one still waits; the daily check calls this too. */
    suspend fun sendHeldPhoneNote() = gate.run { sendHeldPhoneNoteLocked() }

    internal suspend fun sendHeldPhoneNoteLocked() {
        val held = settings.current()
        val heldKind = held.phoneNotePending ?: return
        if (!held.alertsOn || held.firstTopic == null) return
        // setPhoneLocked keeps the held kind true against the number it was first held from: changed and changed back is already no note (K7.6).
        pipeline.tellAllowed(Problem.GuardianPhoneChanged, null, clock.now()) { GuardianPhoneWords.phoneNote(heldKind, it) }
        settings.save(settings.current().copy(phoneNotePending = null, phoneNoteFrom = null))
    }

    private suspend fun fixOwnerNameLocked(owner: String) {
        val name = owner.trim()
        val current = settings.current()
        if (name.isNotEmpty() && current.ownerName != name) settings.save(current.copy(ownerName = name))
    }

    private suspend fun fixNamesLocked(owner: String, guardian: String): Boolean {
        val name = owner.trim()
        val guardianName = guardian.trim()
        if (name.isEmpty() || guardianName.isEmpty()) return false
        val current = settings.current()
        if (current.ownerName != name || current.firstGuardianName != guardianName) {
            settings.save(current.copy(ownerName = name, firstGuardianName = guardianName))
        }
        return true
    }

    private suspend fun setPhoneLocked(phone: String) {
        val current = settings.current()
        val typed = phone.trim()
        if (typed == current.firstGuardianPhone) return
        val change = PhoneChange.of(current.firstGuardianPhone, typed)
        if (change == null) { // only spaces, brackets or an extension changed: nobody needs to hear (K7.6)
            settings.save(current.copy(firstGuardianPhone = typed))
            return
        }
        val now = clock.now()
        history.add(HistoryEntry(now, Problem.GuardianPhoneChanged, Outcome.Acknowledged))
        var pending = current.phoneNotePending
        var from = current.phoneNoteFrom
        if (current.firstTopic != null) {
            if (current.alertsOn) {
                // Told first, saved after: a note is never lost to a kill in between (K7.8). One still waiting is enough (K7.4).
                if (!line.isWaiting(Problem.GuardianPhoneChanged)) {
                    pipeline.tellAllowed(Problem.GuardianPhoneChanged, null, now) { GuardianPhoneWords.phoneNote(change, it) }
                }
                pending = null
                from = null
            } else {
                // One held note, however many changes; true for the number as it is when alerts go on, against the number from when it was
                // first held: changed and changed back is no note (K8b, K7.6).
                from = from ?: current.firstGuardianPhone
                pending = PhoneChange.of(from, typed)
                if (pending == null) from = null
            }
        }
        settings.save(settings.current().copy(firstGuardianPhone = typed, phoneNotePending = pending, phoneNoteFrom = from))
    }

    companion object {
        /** The same address: letters in any case, spaces around it do not count. */
        fun sameEmail(a: String, b: String): Boolean = a.trim().isNotEmpty() && a.trim().equals(b.trim(), ignoreCase = true)
    }
}
