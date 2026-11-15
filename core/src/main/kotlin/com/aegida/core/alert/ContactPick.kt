package com.aegida.core.alert

/** The one row Android lets Aegida read when the owner picks a contact (T4): a number and a name, nothing else. */
data class PickedContact(val phone: String, val name: String) {
    /** The name to put in the guardian's name box: only in setup, only while that box is still empty (T4.2); never in My settings (T4.23). */
    fun nameToFill(currentGuardianName: String, inSetup: Boolean): String? =
        name.takeIf { inSetup && currentGuardianName.isBlank() && it.isNotEmpty() }
}

/** What came back from the contact list. */
sealed interface ContactPick {
    data class Picked(val contact: PickedContact) : ContactPick

    /** A contact with no number Aegida could ring, or a row that could not be read (T4.18, T4.19): she is told, and can type it. */
    data object NoNumber : ContactPick

    /** She backed out: nothing changes (T4.3). */
    data object Cancelled : ContactPick

    companion object {
        /** A row's number and name; no number when it is blank, has no digit, or starts with * or # (a phone code such as "*86", not a number). */
        fun fromRow(number: String?, name: String?): ContactPick {
            val phone = number?.trim().orEmpty()
            if (phone.none { it.isDigit() } || phone.first() == '*' || phone.first() == '#') return NoNumber
            return Picked(PickedContact(phone, name?.trim().orEmpty()))
        }
    }
}
