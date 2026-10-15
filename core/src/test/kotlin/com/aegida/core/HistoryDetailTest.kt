package com.aegida.core

import com.aegida.core.alert.HistoryDetail
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Evidence
import com.aegida.core.text.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Kevin, Oct 10: every alert says what it was in History, in plain words, and never shows a scam address as text. */
class HistoryDetailTest {
    @Test fun aScamMessageSaysTheAppTheSenderTheWordsWithoutTheLinkAndTheMaskedAddress() {
        val detail = HistoryDetail.of(
            Problem.LookalikeLink,
            mapOf(
                Evidence.APP_LABEL to "Messages",
                Evidence.MESSAGE_TITLE to "Chase Bank",
                Evidence.MESSAGE_TEXT to "Refund: paypa1-help.example/claim now",
                Evidence.HOST to "paypa1-help.example",
            ),
        )
        assertEquals(
            "It came from the “Messages” app. From: “Chase Bank”. It says: “Refund: (link) now”. The link went to paypa1-help.example.",
            detail,
        )
    }

    @Test fun aWebsiteWarningSaysWhichWebsiteAndWhatItSaid() {
        assertEquals(
            "The website was news.example. It said: “VIRUS DETECTED”.",
            HistoryDetail.of(Problem.SiteNotifications, mapOf(Evidence.SITE to "https://news.example", Evidence.MESSAGE_TITLE to "VIRUS DETECTED")),
        )
        assertEquals("The website was recipes.example.", HistoryDetail.of(Problem.NewWebsiteNotifications, mapOf(Evidence.SITE to "https://recipes.example")))
    }

    /** WP17 A12 (findings #149): the words that set it off may be only in the text, so History shows the text after the title. */
    @Test fun aWebsiteWarningShowsTheWordsOfItsTextToo() {
        val site = Evidence.SITE to "https://recipes.example"
        assertEquals(
            "The website was recipes.example. It said: “New recipe posted” “Your phone is infected”.",
            HistoryDetail.of(
                Problem.SiteScaryNews,
                mapOf(site, Evidence.MESSAGE_TITLE to "New recipe posted", Evidence.MESSAGE_TEXT to "Your phone is infected"),
            ),
        )
        assertEquals(
            "a link in the text is taken out, and long words are cut short",
            "The website was recipes.example. It said: “Clean now at (link) ${"a".repeat(80)}…”.",
            HistoryDetail.of(Problem.SiteNotifications, mapOf(site, Evidence.MESSAGE_TEXT to "Clean now at https://cleaner.example/x " + "a".repeat(200))),
        )
    }

    @Test fun anAppProblemNamesTheAppExceptWhereTheLabelIsNeverShown() {
        assertEquals("The app is called: “Fun Game”.", HistoryDetail.of(Problem.NewAppOutsidePlay, mapOf(Evidence.APP_LABEL to "Fun Game")))
        assertEquals("The app is called: “Other Browser”.", HistoryDetail.of(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Other Browser")))
        assertNull("urgent kinds never show an app's own words (R17)", HistoryDetail.of(Problem.StalkerwareApp, mapOf(Evidence.APP_LABEL to "Call 555 now")))
    }

    @Test fun problemsWithNothingMoreToSayHaveNoDetail() {
        assertNull(HistoryDetail.of(Problem.ListenerOff, emptyMap()))
        assertNull(HistoryDetail.of(Problem.WebsiteFilterOff, mapOf(Evidence.HOST to "security.cloudflare-dns.com")))
        assertNull(HistoryDetail.of(Problem.ScamLink, emptyMap()))
    }

    @Test fun theHistoryRowCarriesTheDetailOfTheEventAndOlderRowsHaveNone() {
        val at = Instant.parse("2026-10-10T16:00:00Z")
        val rows = HistoryRows.of(
            listOf(
                HistoryEntry(at, Problem.ScamLink, Outcome.OwnerWarned, "It came from the “Messages” app."),
                HistoryEntry(at, Problem.ScamLink, Outcome.GuardianTold, "It came from the “Messages” app."),
                HistoryEntry(at.minusSeconds(86400), Problem.ListenerOff, Outcome.OwnerWarned),
            ),
        )
        assertEquals(listOf("It came from the “Messages” app.", null), rows.map { it.facts })
    }
}
