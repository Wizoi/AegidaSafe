package com.aegida.app.ui

import android.content.Context
import com.aegida.core.alert.GuardianPhone
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Reach
import com.aegida.platform.notify.FixActions

/**
 * The guardian as Home and the question pages need them: the name and saved phone, whether the phone has a dialer ([GuardianPhone.reach]
 * decides what is offered) and what "Call a guardian" does. The default is "no number": nothing is offered that cannot work (T4).
 */
data class GuardianReach(
    val name: String = "",
    val phone: String = "",
    val dialerExists: Boolean = false,
    val onCall: () -> Unit = {},
) {
    val reach: Reach get() = GuardianPhone.reach(phone, dialerExists)
    val callable: Boolean get() = GuardianPhone.callable(phone)

    companion object {
        /** The one place a call is started: the dialer with the number, and nothing at all when there is none (never an empty keypad). */
        fun of(context: Context, guardians: Guardians) = GuardianReach(
            guardians.firstGuardianName, guardians.firstGuardianPhone, FixActions.dialerExists(context),
            onCall = { FixActions.callGuardian(guardians.firstGuardianPhone)?.let { context.startSafely(it) } },
        )
    }
}
