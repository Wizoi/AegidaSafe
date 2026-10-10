package com.aegida.app

import com.aegida.core.text.Fix
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The advice page for a scam link says which app the message came from; an app picks its own label, so it is cleaned like the other labels (R17). */
class AdviceLineTest {
    @Test fun namesTheAppInQuotesAndNothingWhenThereIsNoLabel() {
        assertEquals("It came from the “Messages” app.", Texts.Home.adviceFrom("Messages"))
        assertNull(Texts.Home.adviceFrom(null))
        assertNull(Texts.Home.adviceFrom("  "))
    }

    @Test fun anAppLabelCannotSmuggleAnInstruction() {
        val line = Texts.Home.adviceFrom("Call 555\nnow " + "x".repeat(100))!!
        assertEquals(false, line.contains("\n"))
        assertEquals(true, line.length < 90)
    }

    @Test fun theHomeButtonOnlySaysFixMyPhoneWhenSomethingIsFixed() {
        assertEquals(Texts.Home.SHOW_ME, Texts.Home.fixButton(Fix.Acknowledge))
        assertEquals(Texts.Home.FIX_MY_PHONE, Texts.Home.fixButton(Fix.Settings))
        assertEquals(Texts.Home.FIX_MY_PHONE, Texts.Home.fixButton(Fix.WebsiteFilterGuide))
    }
}
