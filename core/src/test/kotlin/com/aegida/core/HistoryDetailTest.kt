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
            "The website: news.example. It said: “VIRUS DETECTED”.",
            shown(Problem.SiteNotifications, mapOf(Evidence.SITE to "https://news.example", Evidence.MESSAGE_TITLE to "VIRUS DETECTED")),
        )
        assertEquals("The website: recipes.example.", shown(Problem.NewWebsiteNotifications, mapOf(Evidence.SITE to "https://recipes.example")))
    }

    /** WP17 A12 (findings #149): the words that set it off may be only in the text, so History shows the text after the title. */
    @Test fun aWebsiteWarningShowsTheWordsOfItsTextToo() {
        val site = Evidence.SITE to "https://recipes.example"
        assertEquals(
            "The website: recipes.example. It said: “New recipe posted” “Your phone is infected”.",
            shown(
                Problem.SiteScaryNews,
                mapOf(site, Evidence.MESSAGE_TITLE to "New recipe posted", Evidence.MESSAGE_TEXT to "Your phone is infected"),
            ),
        )
        assertEquals(
            "a link in the text is taken out, and long words are cut short",
            "The website: recipes.example. It said: “Clean now at (link) ${"a".repeat(80)}…”.",
            shown(Problem.SiteNotifications, mapOf(site, Evidence.MESSAGE_TEXT to "Clean now at https://cleaner.example/x " + "a".repeat(200))),
        )
    }

    private fun shown(problem: Problem, evidence: Map<String, String>) = HistoryDetail.display(HistoryDetail.of(problem, evidence))

    @Test fun anAppProblemNamesTheAppAndAnUrgentOneAsTheNameItCallsItself() { // #164: the owner sees it; the guardian still does not
        assertEquals("The app is called: “Fun Game”.", HistoryDetail.of(Problem.NewAppOutsidePlay, mapOf(Evidence.APP_LABEL to "Fun Game")))
        assertEquals("The app is called: “Other Browser”.", HistoryDetail.of(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Other Browser")))
        assertEquals("The app calls itself: “Call 555 now”.", HistoryDetail.of(Problem.StalkerwareApp, mapOf(Evidence.APP_LABEL to "Call 555 now")))
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

    /** WN1, WN24: a website line written before #164 ("The website was …") is the same website as the new one, so its row still closes. */
    @Test fun aWebsiteLineFromBeforeTheUpdateMatchesTheNewLineForTheSameWebsiteOnly() {
        val now = HistoryDetail.of(Problem.NewWebsiteNotifications, mapOf(Evidence.SITE to "https://news.example"))
        assertEquals(true, HistoryDetail.same("The website was news.example.", now))
        assertEquals(false, HistoryDetail.same("The website was ews.example.", now))
        assertEquals(false, HistoryDetail.same("The website was other.example.", now))
    }

    /** WN4, WN24: the same app's line matches though its words grew facts in #164; two apps with one name stay two. */
    @Test fun anAppsLineMatchesByItsPackageAndTwoAppsWithOneNameStayTwo() {
        val old = HistoryDetail.of(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Update", Evidence.PACKAGE to "org.a"))
        val new = HistoryDetail.of(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Update", Evidence.PACKAGE to "org.a", Evidence.APP_SOURCE to "play"))
        val other = HistoryDetail.of(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Update", Evidence.PACKAGE to "org.b", Evidence.APP_SOURCE to "play"))
        assertEquals(true, HistoryDetail.same(old, new))
        assertEquals(false, HistoryDetail.same(new, other))
    }

    /** WN4: an urgent warning's History line names the app as it calls itself, with where it came from but never the day (written once). */
    @Test fun anUrgentAppsHistoryLineNamesItAndWhereItCameFromButNotTheDay() {
        val detail = HistoryDetail.of(
            Problem.ListenerAdded,
            mapOf(Evidence.APP_LABEL to "Pixel Watch", Evidence.APP_SOURCE to "play", Evidence.APP_ADDED to "1760000000000", Evidence.PACKAGE to "org.w"),
        )
        assertEquals("The app calls itself: “Pixel Watch”. It came from the Play Store.", HistoryDetail.display(detail))
    }
}
