package com.aegida.core.text

import com.aegida.core.alert.PhoneChange

/**
 * The words that depend on whether the guardian has a number to ring (T4, K1, K10) and the note to the guardian when it changes (K7).
 * The sentences themselves are constants in [Texts]; this picks between them. The name is shown as [Texts.Home.guardianLabel] shows an app
 * name: never a web address a screen could mistake for a link (T4.11).
 */
object GuardianPhoneWords {
    /** The guardian's name as she reads it on her own phone; null when blank. */
    fun guardianWho(guardianName: String): String? = Texts.Home.guardianLabel(guardianName).ifEmpty { null }

    /** The row in My settings: "Sam's phone". */
    fun phoneTitle(guardianName: String): String = guardianWho(guardianName)?.let { Texts.Settings.PHONE_TITLE.format(it) } ?: Texts.Settings.PHONE_TITLE_NO_NAME

    /** Shown only while alerts are on: only then does the guardian hear of the change (K7). Never says the number is sent. */
    fun phoneTold(guardianName: String): String = guardianWho(guardianName)?.let { Texts.Settings.PHONE_TOLD.format(it) } ?: Texts.Settings.PHONE_TOLD_NO_NAME

    /** Home's "unsure" line where "Call a guardian" cannot be offered. */
    fun unsureAsk(guardianName: String): String = Texts.Home.UNSURE_ASK.format(guardianWho(guardianName) ?: Texts.Home.YOUR_GUARDIAN)

    /** No dialer app on the phone: the number as plain words, never a button that does nothing (T4.10). */
    fun guardianNumber(phone: String): String = Texts.Home.GUARDIAN_NUMBER.format(phone.trim())

    /** "If this keeps happening, call a guardian" or, with no number to ring, "ask Sam". */
    fun couldntCheckWhy(guardianName: String, callable: Boolean): String =
        if (callable) Texts.Home.COULDNT_CHECK_WHY else Texts.Home.COULDNT_CHECK_WHY_ASK.format(guardianWho(guardianName) ?: Texts.Home.YOUR_GUARDIAN)

    fun confirmNoHelp(guardianName: String, callable: Boolean): String =
        if (callable) Texts.Home.CONFIRM_NO_HELP else Texts.Home.CONFIRM_NO_HELP_ASK.format(guardianWho(guardianName) ?: Texts.Home.YOUR_GUARDIAN)

    /** What Home says under the headline for [problem]: the same sentence, except "Please call them." when there is no number to ring (K10). */
    fun forMom(problem: Problem, guardianName: String, callable: Boolean): String =
        if (problem != Problem.GuardianUnreachable || callable) {
            problem.forMom
        } else {
            guardianWho(guardianName)?.let { Texts.Home.UNREACHABLE_TELL.format(it) } ?: Texts.Home.UNREACHABLE_TELL_NO_NAME
        }

    /** The note for [change], from the owner's name alone: it has no parameter for the number, so the number cannot be in it (K7.9). */
    fun phoneNote(change: PhoneChange, owner: String): String = when (change) {
        PhoneChange.Added -> Texts.Guardian.PHONE_ADDED
        PhoneChange.Changed -> Texts.Guardian.PHONE_CHANGED
        PhoneChange.Removed -> Texts.Guardian.PHONE_REMOVED
    }.format(owner)
}
