package com.aegida.app

import android.content.ActivityNotFoundException
import com.aegida.app.ui.launchQuietly
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T4.5: the contact list cannot be opened when she taps: no crash, and nothing happens. */
class ContactPickerLaunchTest {
    @Test fun t4_5_aListThatWentAwayOrACallerNotAllowedOrALauncherThatIsGoneIsNotACrash() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException("denied"), IllegalStateException("launcher gone"))) {
            assertFalse(failure.toString(), launchQuietly { throw failure })
        }
    }

    @Test fun t4_5_aListThatOpensIsReportedAsOpened() {
        var opened = 0
        assertTrue(launchQuietly { opened++ })
        assertTrue(opened == 1)
    }
}
