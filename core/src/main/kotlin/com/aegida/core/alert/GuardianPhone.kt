package com.aegida.core.alert

/**
 * The one rule for "the guardian has a number Aegida can dial" (T4, finding #185). A phone can be saved as typed ("555 0100,123" has a
 * pause and an extension); only what comes before the first "," or ";" is dialed, and only if it has digits. Every screen and the
 * call intent (FixActions.callGuardian) ask here, so a button never shows without a number it can ring.
 */
object GuardianPhone {
    /** What is dialed: digits and "+" up to the first "," or ";" (an extension is never glued on). Empty when there is nothing to dial. */
    fun dialable(phone: String): String =
        phone.substringBefore(',').substringBefore(';').filter { it.isDigit() || it == '+' }.takeIf { number -> number.any { it.isDigit() } }.orEmpty()

    fun callable(phone: String): Boolean = dialable(phone).isNotEmpty()

    /** How Home and the question pages offer to reach the guardian: a button, the number as words (no dialer on the phone), or neither. */
    fun reach(phone: String, dialerExists: Boolean): Reach = when {
        !callable(phone) -> Reach.None
        dialerExists -> Reach.Button
        else -> Reach.NumberOnly
    }
}

enum class Reach { Button, NumberOnly, None }

/** What a change of the guardian's phone is called in the note to the guardian (K7). */
enum class PhoneChange {
    Added, Changed, Removed;

    companion object {
        /** Null when the number that would be dialed is the same (spaces, brackets or an extension changed), or neither has one (K7.6). */
        fun of(before: String, after: String): PhoneChange? {
            val was = GuardianPhone.dialable(before)
            val now = GuardianPhone.dialable(after)
            return when {
                was == now -> null
                was.isEmpty() -> Added
                now.isEmpty() -> Removed
                else -> Changed
            }
        }
    }
}
