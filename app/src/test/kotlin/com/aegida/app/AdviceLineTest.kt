package com.aegida.app

import com.aegida.core.text.Fix
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Test

/** The advice page for a scam link says which app the message came from; an app picks its own label, so it is cleaned like the other labels (R17). */
class AdviceLineTest {
    private fun appLine(label: String?) = Texts.Home.whatItIs(
        com.aegida.core.text.Problem.ScamLink, label?.let { mapOf(com.aegida.core.model.Evidence.APP_LABEL to it) } ?: emptyMap(), now, zone, locale,
    )

    @Test fun namesTheAppInQuotesAndNothingWhenThereIsNoLabel() {
        assertEquals(listOf("It came from the “Messages” app."), appLine("Messages"))
        assertEquals(emptyList<String>(), appLine(null))
        assertEquals(emptyList<String>(), appLine("  "))
    }

    @Test fun anAppLabelCannotSmuggleAnInstruction() {
        val line = appLine("Call 555\nnow " + "x".repeat(100)).single()
        assertEquals(false, line.contains("\n"))
        assertEquals(true, line.length < 90)
    }

    private val zone = java.time.ZoneId.of("America/New_York")
    private val locale = java.util.Locale.US
    private val now = java.time.Instant.parse("2026-10-10T16:00:00Z")

    private fun finding(vararg evidence: Pair<String, String>) = com.aegida.core.model.Finding(
        com.aegida.core.model.FindingKey(com.aegida.core.model.DetectorId.Link, com.aegida.core.text.Problem.LookalikeLink, "paypa1-help.example"),
        evidence.toMap(), now, now,
    )

    /** Kevin, Oct 10: "if the owner has to do something or avoid it, they need to know what it was". */
    @Test fun theAdvicePageSaysTheAppTheTimeWhoItIsFromAndWhatItSaysWithoutTheLink() {
        val e = com.aegida.core.model.Evidence
        val lines = com.aegida.app.ui.AdviceLines.of(
            finding(e.APP_LABEL to "Messages", e.MESSAGE_TITLE to "Chase Bank", e.MESSAGE_TEXT to "Refund: paypa1-help.example/claim now",
                e.MESSAGE_AT to now.minusSeconds(3600).toEpochMilli().toString(), e.HOST to "paypa1-help.example"),
            now, zone, locale,
        )
        assertEquals(
            listOf("It came from the “Messages” app.", "It came in today at 11:00 AM.", "From: “Chase Bank”", "It says: “Refund: (link) now”",
                Texts.Home.SCAM_ADVICE),
            lines.map { it.replace(' ', ' ') }, // newer Java puts a narrow no-break space before AM/PM
        )
    }

    @Test fun theAdvicePageStillWorksWhenNothingAboutTheMessageIsKnown() {
        assertEquals(listOf(Texts.Home.SCAM_ADVICE), com.aegida.app.ui.AdviceLines.of(finding(), now, zone, locale))
    }

    @Test fun aLongMessageIsCutShortAndALinkNeverShowsAsText() {
        val line = Texts.Home.saysLine("Hello https://evil.example/x?token=1 and " + "word ".repeat(60))!!
        assertEquals(false, line.contains("evil.example") || line.contains("http"))
        assertEquals(true, line.length <= 120)
    }

    @Test fun theHomeButtonOnlySaysFixMyPhoneWhenSomethingIsFixed() {
        assertEquals(Texts.Home.SHOW_ME, Texts.Home.fixButton(Fix.Acknowledge))
        assertEquals(Texts.Home.FIX_MY_PHONE, Texts.Home.fixButton(Fix.Settings))
        assertEquals(Texts.Home.FIX_MY_PHONE, Texts.Home.fixButton(Fix.WebsiteFilterGuide))
    }
}
