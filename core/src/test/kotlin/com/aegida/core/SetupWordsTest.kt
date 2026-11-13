package com.aegida.core

import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/tester-feedback-design.md T3: setup words that confused, one test per changed string; the app speaks as "Aegida", never "I". */
class SetupWordsTest {
    @Test fun theNameBoxAsksForHerFirstNameNotHowToAddressHer() { // T3.4
        assertEquals("Your first name", Texts.Setup.YOUR_NAME)
    }

    @Test fun theAlertsExampleUsesHerOwnFirstNameWithThePossessiveGuardianNotesUse() { // T3.1
        assertTrue(Texts.Setup.alertsBody("Helen").contains("fake warnings to Helen's phone. Aegida hid them."))
        assertFalse(Texts.Setup.alertsBody("Helen").contains("Ruth"))
    }

    @Test fun theAlertsExampleHoldsUpForHostileNames() { // T3.2: the same "'s" form guardian notes use, whatever she typed
        for (name in listOf("Chris", "Ruth 😀", "Ελένη", "Рут", "A".repeat(200))) {
            val body = Texts.Setup.alertsBody(name)
            assertTrue(name, body.contains("fake warnings to $name's phone."))
            assertTrue(name, body.endsWith("It never sends your messages, calls or where you are."))
        }
        assertTrue(Texts.Setup.alertsBody("  Ruth  ").contains("to Ruth's phone"))
    }

    @Test fun anOldDatabaseWithABlankNameSaysYourPhone() { // T3.3
        for (blank in listOf("", "   ")) assertTrue(Texts.Setup.alertsBody(blank).contains("fake warnings to your phone. Aegida hid them."))
    }

    @Test fun thePictureLineSaysAegidaShowsItOnWarningsAndDoesNotPromiseEveryNotice() { // T3.4
        assertEquals("Aegida shows this picture on its warnings. If a warning does not show it, it did not come from Aegida.", Texts.Setup.PICTURE_BODY)
    }

    @Test fun theCouldntCheckLineSpeaksAsAegida() { // T3.4
        assertEquals("Aegida couldn't check today.", Texts.Home.COULDNT_CHECK)
    }

    @Test fun theInviteStepSaysAegidaWillWriteTheEmail() { // T3.4
        assertEquals("Aegida will write an email to Kevin in your own email app. You press Send.", Texts.Setup.INVITE_BODY.format("Kevin"))
    }

    @Test fun theWelcomeSaysGuardiansAreToldOnlyIfSheSaysSo() { // T3.4
        assertTrue(Texts.Setup.WELCOME_BODY.endsWith("It quietly looks out for scams and, if you say so, lets a guardian you trust know."))
    }
}
