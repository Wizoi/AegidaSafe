package com.aegida.core

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.core.url.HostMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Findings #94 and #96: what the Opus reviews of the released code found in the core rules and texts. */
class ReviewFixesTest {
    @Test fun aLinkIsTakenOutOfTheWordsByTheSameCodeThatFindsLinksNotByTheStoredHost() {
        val lookalike = Texts.Home.saysLine("Refund at \u0440\u0430ypal.com/claim now", "xn--ypal-43d9g.com")!!
        assertFalse(lookalike, lookalike.contains("ypal"))
        val two = Texts.Home.saysLine("Pay at evil.example/x or other-site.example/y today", "evil.example")!!
        assertFalse(two, two.contains("other-site") || two.contains("evil"))
        assertEquals("From: “Visit (link) now”", Texts.Home.senderLine("Visit paypa1-help.example now"))
    }

    @Test fun aWebsitePopUpTitleNeverShowsALinkAsText() { // finding #98
        val said = Texts.Home.saidLine("Infected! Go to cleaner-app.top now")!!
        assertFalse(said, said.contains("cleaner-app"))
    }

    @Test fun ordinaryWordsWithADotAreNotTakenForLinks() { // finding #98
        assertEquals("Mr.Smith says your parcel is held.Please call St.Louis", Texts.Home.withoutLinks("Mr.Smith says your parcel is held.Please call St.Louis"))
    }

    @Test fun aLongLabelEndingInAnAddressIsMaskedBeforeItIsCut() { // finding #98
        val label = Texts.Home.guardianLabel("A very long app name that goes on and on before evil-site.com")
        assertFalse(label, label.contains("evil-site") || label.contains("evil-si"))
    }

    @Test fun anAppNamedLikeAWebAddressReachesTheGuardianMasked() {
        val note = Problem.NoisyApp.forGuardian("Ruth", "evil-site.com")!!
        assertFalse(note, note.contains("evil-site.com"))
        assertTrue(note, note.contains("ev*****.com"))
        assertFalse(Texts.Guardian.ALLOWED.format("Ruth", Texts.Home.guardianLabel("Get evil-site.com")).contains("evil-site.com"))
    }

    @Test fun hostMaskHandlesATrailingDotAndWww() {
        assertEquals("ex*****.com", HostMask.of("example.com."))
        assertEquals("ex*****.com", HostMask.of("www.example.com"))
        assertEquals("ex*****.com", HostMask.of("https://m.example.com/x"))
    }

    @Test fun anUnansweredWebsiteQuestionIsNotShownAsHandledAndALateAllowNoteIsNotShownAsNeedsYou() {
        val at = Instant.parse("2026-10-11T16:00:00Z")
        val asked = HistoryRows.of(listOf(HistoryEntry(at, Problem.NewWebsiteNotifications, Outcome.OwnerWarned))).single()
        assertFalse(asked.handled)
        val fake = HistoryRows.of(listOf(HistoryEntry(at, Problem.SiteNotifications, Outcome.OwnerWarned))).single()
        assertTrue("a fake warning that was hidden needs nothing", fake.handled)
        val lateNote = HistoryRows.of(listOf(HistoryEntry(at, Problem.AppAllowed, Outcome.GuardianTold))).single()
        assertTrue(lateNote.handled)
    }

    @Test fun aWebsiteTheOwnerSaidNoToIsQuietForAWeek() {
        assertEquals(Duration.ofDays(7), Problem.NewWebsiteNotifications.quietAfterSeen)
    }
}
