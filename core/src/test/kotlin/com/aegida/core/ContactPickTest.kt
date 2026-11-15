package com.aegida.core

import com.aegida.core.alert.ContactPick
import com.aegida.core.alert.PickedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Release B, T4.2 and T4.18-T4.23: what a picked contact row becomes. */
class ContactPickTest {
    @Test fun t4_2_aRowWithANumberAndANameIsPicked() {
        assertEquals(ContactPick.Picked(PickedContact("+1 (555) 010-0199", "Sam")), ContactPick.fromRow("+1 (555) 010-0199", "Sam"))
    }

    @Test fun t4_18_aBlankNumberOrOneWithNoDigitsOrACodeLikeStar86IsNoNumber() {
        for (bad in listOf(null, "", "   ", "n/a", "call me", "*86", "#31#", " *86 ", "+")) {
            assertEquals(bad.toString(), ContactPick.NoNumber, ContactPick.fromRow(bad, "Sam"))
        }
    }

    @Test fun t4_18_aNumberWithLettersIsNoNumberBecauseTheCallButtonWouldDialTheDigitsOnly() {
        for (vanity in listOf("1-800-FLOWERS", "555 0100 ext", "x123")) {
            assertEquals(vanity, ContactPick.NoNumber, ContactPick.fromRow(vanity, "Shop"))
        }
    }

    @Test fun t4_12_aNameShapedLikeAWebAddressIsKeptAsPlainTextAndABlankNameIsEmpty() {
        assertEquals("pay-now.example.com", (ContactPick.fromRow("555 0100", " pay-now.example.com ") as ContactPick.Picked).contact.name)
        assertEquals("", (ContactPick.fromRow("555 0100", null) as ContactPick.Picked).contact.name)
    }

    @Test fun t4_2_theNameFillsTheGuardianNameInSetupOnlyWhenItIsStillEmpty() {
        val sam = PickedContact("555 0100", "Sam")
        assertEquals("Sam", sam.nameToFill(currentGuardianName = "", inSetup = true))
        assertEquals("Sam", sam.nameToFill(currentGuardianName = "  ", inSetup = true))
        assertNull(sam.nameToFill(currentGuardianName = "Samuel", inSetup = true))
        assertNull(PickedContact("555 0100", "").nameToFill("", inSetup = true))
    }

    @Test fun t4_23_pickingInMySettingsNeverGivesAName() {
        assertNull(PickedContact("555 0100", "Sam").nameToFill(currentGuardianName = "", inSetup = false))
    }
}
