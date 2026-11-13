package com.aegida.app

import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T2: the What you will see page promises a notice with her picture only when Aegida can both see notifications and show its own. */
class MainStateTest {
    private fun state(listener: Boolean, posting: Boolean) = MainState(
        snapshot = Snapshot(Instant.now(), PrivateDns.Automatic, null, listener, posting, Reading.NotPresent, Reading.NotPresent),
    )

    @Test fun noticesAreOnWhenAegidaCanSeeNotificationsAndCanPostItsOwn() = assertTrue(state(listener = true, posting = true).noticesOn)

    @Test fun noticesAreOffWhenNotificationAccessIsOff() = assertFalse(state(listener = false, posting = true).noticesOn)

    @Test fun noticesAreOffWhenAegidaMayNotPostNotifications() = assertFalse(state(listener = true, posting = false).noticesOn)

    @Test fun noticesAreOffWhenBothAreOff() = assertFalse(state(listener = false, posting = false).noticesOn)

    @Test fun noticesAreOffBeforeAnyCheckHasTakenASnapshot() = assertFalse(MainState().noticesOn)
}
