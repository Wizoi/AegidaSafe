package com.aegida.core

import com.aegida.core.url.HostMask
import org.junit.Assert.assertEquals
import org.junit.Test

/** Kevin, Oct 10: a guardian gets enough of a web address to tell sites apart (first two letters and the ending), never the whole address. */
class HostMaskTest {
    @Test fun keepsTheFirstTwoLettersAFixedRunOfStarsAndTheEnding() {
        assertEquals("pa*****.example", HostMask.of("paypa1-help.example"))
        assertEquals("lo*****.uk", HostMask.of("login.bank.co.uk"))
        assertEquals("ch*****.com", HostMask.of("chase-secure-login.com"))
    }

    @Test fun theLengthOfTheNameIsNotRevealed() {
        assertEquals(HostMask.of("ab-xx.com").length, HostMask.of("ab-a-very-long-scam-domain-name.com").length)
    }

    @Test fun aSchemeAndPortAndPathAreDroppedFirst() {
        assertEquals("ex*****.com",HostMask.of("https://example.com:8443/pay?x=1"))
        assertEquals("lo*****", HostMask.of("http://localhost:8765"))
    }

    @Test fun veryShortAndNumericNamesShowNothingOfTheName() {
        assertEquals("*****.co", HostMask.of("x.co"))
        assertEquals("*****", HostMask.of("192.168.1.5"))
        assertEquals("*****", HostMask.of(""))
    }
}
