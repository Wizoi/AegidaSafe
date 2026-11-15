package com.aegida.core

import com.aegida.core.alert.GuardianPhone
import com.aegida.core.alert.PhoneChange
import com.aegida.core.alert.Reach
import com.aegida.core.text.GuardianPhoneWords
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release B T4/T2: the one rule for "the guardian has a number to ring" and the words that depend on it. */
class GuardianPhoneTest {
    @Test fun theGuardiansNameIsWrittenAsTypedInThePhoneRowOnlyTheFallbackIsCapitalized() { // T4.24
        assertEquals("iPhone Dad's phone", GuardianPhoneWords.phoneTitle("iPhone Dad"))
        assertEquals("Your guardian's phone", GuardianPhoneWords.phoneTitle(""))
    }

    @Test fun t4_20_theNumberIsCutAtTheFirstCommaOrSemicolon() { // finding #185
        assertEquals("5550100", GuardianPhone.dialable("555 0100,123"))
        assertEquals("5550100", GuardianPhone.dialable("(555) 0100;123"))
        assertEquals("+15550100", GuardianPhone.dialable("+1 555-0100 , 9 ; 8"))
    }

    @Test fun t4_13_lettersAreDroppedAndNoDigitsMeansNoNumber() {
        assertEquals("5550100", GuardianPhone.dialable("Sam 555 0100"))
        for (none in listOf("", "  ", "n/a", "+", ",123", ";", "call Sam")) assertFalse(none, GuardianPhone.callable(none))
    }

    @Test fun t4_10_aNumberWithNoDialerIsShownAsWordsNeverAButton() {
        assertEquals(Reach.Button, GuardianPhone.reach("555 0100", dialerExists = true))
        assertEquals(Reach.NumberOnly, GuardianPhone.reach("555 0100", dialerExists = false))
        assertEquals(Reach.None, GuardianPhone.reach("", dialerExists = true))
    }

    @Test fun k7_6_onlyADifferentDialedNumberIsAChange() {
        assertNull(PhoneChange.of("555 0100", "(555) 0100"))
        assertNull(PhoneChange.of("555 0100", "555 0100,123"))
        assertNull(PhoneChange.of("", "n/a"))
        assertEquals(PhoneChange.Added, PhoneChange.of("", "555 0100"))
        assertEquals(PhoneChange.Added, PhoneChange.of("n/a", "555 0100"))
        assertEquals(PhoneChange.Changed, PhoneChange.of("555 0100", "555 0199"))
        assertEquals(PhoneChange.Removed, PhoneChange.of("555 0100", ""))
    }

    @Test fun t2_10_andT4_7_theUnsureLineSaysTapCallWithANumberAndAskSamWithout() {
        val withNumber = Texts.Setup.seeLines(true, true, "Sam", callButton = true)
        val without = Texts.Setup.seeLines(true, true, "Sam", callButton = false)
        assertEquals("If you are ever unsure, tap Call a guardian.", withNumber.last())
        assertEquals("If you are ever unsure, ask Sam.", without.last())
        assertEquals("If you are ever unsure, ask your guardian.", Texts.Setup.seeLines(true, true, "  ", callButton = false).last())
        assertEquals(withNumber.dropLast(1), without.dropLast(1))
    }

    @Test fun t4_11_aGuardianNameShapedLikeAWebAddressIsNeverShownAsOne() {
        val line = GuardianPhoneWords.unsureAsk("pay-now.example.com/win")
        assertFalse(line, line.contains("pay-now.example.com"))
        assertEquals("If you are ever unsure, ask your guardian.", GuardianPhoneWords.unsureAsk(""))
    }

    @Test fun t4_17_everySentenceThatSaidCallSaysAskWhenThereIsNoNumber() {
        assertEquals("Aegida will try again soon. If this keeps happening, call a guardian.", GuardianPhoneWords.couldntCheckWhy("Sam", true))
        assertEquals("Aegida will try again soon. If this keeps happening, ask Sam.", GuardianPhoneWords.couldntCheckWhy("Sam", false))
        assertEquals("If you did not do this, change it back, or call your guardian.", GuardianPhoneWords.confirmNoHelp("Sam", true))
        assertEquals("If you did not do this, change it back, or ask Sam.", GuardianPhoneWords.confirmNoHelp("Sam", false))
        assertEquals("If you did not do this, change it back, or ask your guardian.", GuardianPhoneWords.confirmNoHelp("", false))
    }

    @Test fun k10_anUnreachableGuardianWithNoNumberIsToldToTellThemNotToCall() {
        assertEquals(Problem.GuardianUnreachable.forMom, GuardianPhoneWords.forMom(Problem.GuardianUnreachable, "Sam", callable = true))
        val unreachable = Problem.GuardianUnreachable
        assertEquals("Aegida couldn't reach Sam for a day. When you can, please tell Sam.", GuardianPhoneWords.forMom(unreachable, "Sam", false))
        val noName = GuardianPhoneWords.forMom(unreachable, "", false)
        assertEquals("Aegida couldn't reach your guardian for a day. When you can, please tell them.", noName)
        assertEquals(Problem.NoisyApp.forMom, GuardianPhoneWords.forMom(Problem.NoisyApp, "Sam", false))
        assertTrue(GuardianPhoneWords.forMom(Problem.GuardianUnreachable, "Sam", false).none { it.isDigit() })
    }
}
