package com.aegida.app

import com.aegida.core.net.PublicPages
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The public "how it works" page (docs/site/how-it-works.html, published by the feed workflow): one page, a Simple view for
 * someone who is not technical and a Detailed view for someone who wants to check the mechanism. It is marketing, so it is held
 * to the same words as the app and the store, and it must quote the app's own sentences so the two cannot drift apart.
 */
class PublicPagesTest {
    private val file = File("../docs/site/how-it-works.html")
    private val html get() = file.readText()

    /** The page as text: tags removed and the few entities the page uses turned back into characters. */
    private val plain get() = html
        .replace(Regex("<script.*?</script>|<style.*?</style>", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")

    @Test fun thePageExistsAndTheAppLinksToItsFileName() {
        assertTrue("no page at ${file.path}", file.isFile)
        assertTrue(PublicPages.HOW_IT_WORKS, PublicPages.HOW_IT_WORKS.startsWith("https://") && PublicPages.HOW_IT_WORKS.endsWith("/${file.name}"))
    }

    @Test fun bothViewsAreOnThePageAndSimpleIsTheOneShownFirst() {
        for (id in listOf("view-simple", "view-detailed", "simple-view", "detailed-view")) assertTrue("missing id=\"$id\"", html.contains("id=\"$id\""))
        assertTrue("the Simple radio must be checked by default", Regex("""<input[^>]*id="view-simple"[^>]*\bchecked\b""").containsMatchIn(html))
        assertTrue("the switch needs labels a reader can press", html.contains("""for="view-simple"""") && html.contains("""for="view-detailed""""))
    }

    @Test fun itWorksWithoutScriptsFontsAnalyticsOrAnyOutsideFile() {
        assertTrue("no external scripts", !Regex("""<script[^>]*\bsrc=""").containsMatchIn(html))
        assertTrue("no external files", !Regex("""\b(src|srcset)="https?://""").containsMatchIn(html) && !html.contains("<link rel=\"stylesheet\""))
        val hosts = Regex("""href="https?://([^/"]+)""").findAll(html).map { it.groupValues[1] }.toSet()
        assertEquals("links may go only to our own site, ntfy and the two app stores", emptySet<String>(), hosts - setOf("wizoi.github.io", "ntfy.sh", "play.google.com", "apps.apple.com"))
    }

    @Test fun itNeverSaysSafeOrPromisesMoreThanTheAppDoesOrClaimsTheCodeIsOpen() {
        val banned = Regex("""\b(safe|safely|safety|secure|secured|100%|guarantee[ds]?|protected|bulletproof|unbreakable)\b""", RegexOption.IGNORE_CASE)
        assertEquals(emptyList<String>(), banned.findAll(plain).map { it.value }.toList())
        val open = Regex("""open[ -]source|code is open|MIT licen[cs]e|read how it works""", RegexOption.IGNORE_CASE)
        assertEquals("the repo is private until Kevin opens it", emptyList<String>(), open.findAll(plain).map { it.value }.toList())
        val labels = Regex("""\b(seniors?|elderly|vulnerable)\b""", RegexOption.IGNORE_CASE)
        assertEquals("say \"you\", never label her", emptyList<String>(), labels.findAll(plain).map { it.value }.toList())
    }

    @Test fun itQuotesTheAppsOwnWordsSoThePageAndTheAppCannotDriftApart() {
        val page = plain
        val quoted = listOf(
            "the warning she sees" to Problem.ScamLink.forMom,
            "the calm Home" to Texts.Home.NO_KNOWN_PROBLEMS,
            "the Home that needs a tap" to Texts.Home.NEEDS_ONE,
            "what the guardian is told" to Problem.ScamLink.forGuardian("Ruth")!!,
            "the guardian's first message" to Texts.Guardian.SET_UP.format("Ruth"),
            "the notice on her screen" to Texts.STATUS_NOTICE,
        )
        for ((what, text) in quoted) assertTrue("the page no longer quotes $what: $text", page.contains(text))
    }

    /** The page lists every note a guardian can receive, taken from the app's own text, so a reworded note fails here until the page is updated. */
    @Test fun itListsEveryNoteAGuardianCanReceiveInTheAppsOwnWords() {
        val page = plain
        val notes = Problem.entries.mapNotNull { it.forGuardian("Ruth") } + listOf(
            Texts.Guardian.SET_UP, Texts.Guardian.STILL_OPEN, Texts.Guardian.STILL_OPEN_URGENT, Texts.Guardian.CONFIRMED,
            Texts.Guardian.CONFIRMED_URGENT, Texts.Guardian.CHANGED, Texts.Guardian.NOT_CHECKED_IN,
        ).map { it.format("Ruth") }
        val missing = notes.filterNot { page.contains(it) }
        assertEquals("these notes are not on the page", emptyList<String>(), missing)
    }

    @Test fun itSaysWhichVersionOfTheAppItDescribesAndLinksToThePrivacyPage() {
        val version = Regex("""versionName\s*=\s*"([^"]+)"""").find(File("build.gradle.kts").readText())!!.groupValues[1]
        assertTrue("the page must say it describes Aegida $version (update it with each release)", plain.contains("Aegida $version"))
        assertTrue("link to the privacy page", html.contains("href=\"privacy.html\""))
    }

    @Test fun theSimpleViewAnswersTheQuestionsAnEightyYearOldAndHerFamilyWouldAsk() {
        assertTrue("six questions, each a details block", Regex("<details").findAll(html).count() >= 6)
        assertTrue(plain.contains("Does it cost anything?"))
        assertTrue(plain.contains("Can I turn it off"))
    }

    @Test fun theDetailedViewShowsTheMechanismAndTheLimits() {
        for (needed in listOf("ECDSA", "ntfy.sh", "what leaves the phone", "What it cannot do", "Permissions")) {
            assertTrue("the Detailed view must cover: $needed", plain.contains(needed, ignoreCase = true))
        }
        assertTrue("at least two diagrams", Regex("<figure").findAll(html).count() >= 2)
        assertTrue("every diagram has a caption and a label for a screen reader", Regex("<svg[^>]*role=\"img\"[^>]*aria-label=").findAll(html).count() >= Regex("<figure").findAll(html).count())
    }
}
