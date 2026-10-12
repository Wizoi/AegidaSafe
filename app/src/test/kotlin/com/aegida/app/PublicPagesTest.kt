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

    /** Kevin, Oct 9: the page says "the owner" (or "you" in the Simple view), never "her" or "she", as the app's own messages do. */
    @Test fun itNeverAssumesHowTheOwnerIsAddressed() {
        val pronouns = Regex("""\b(her|hers|herself|she|he|him|his|himself)\b""", RegexOption.IGNORE_CASE)
        assertEquals(emptyList<String>(), pronouns.findAll(plain).map { it.value }.toList())
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
        ).map { it.format("Ruth") } +
            listOf(Problem.NoisyApp.forGuardian("Ruth", "Image to PDF")!!, Texts.Guardian.ALLOWED.format("Ruth", "Image to PDF")) +
            // Kevin, Oct 10: the notes that name the app and a masked address.
            listOf(Problem.ScamLink, Problem.LookalikeLink).map { it.forGuardian("Ruth", "Messages", "paypa1-help.example")!! } +
            listOf(Problem.SiteNotifications, Problem.NewWebsiteNotifications).map { it.forGuardian("Ruth", null, "paypa1-help.example")!! }
        val missing = notes.filterNot { page.contains(it) }
        assertEquals("these notes are not on the page", emptyList<String>(), missing)
    }

    /**
     * Kevin, Oct 9: public text never mentions a feature that is not built. This list is what the product brief and the old
     * plans promised that the code does not do (findings #81): a source we left out, a checker we never added, a second guardian.
     */
    @Test fun noPublicTextMentionsAFeatureThatIsNotBuilt() {
        val unbuilt = Regex("""URLhaus|Safe Browsing|Play Protect|Play Integrity|MalwareBazaar|second guardian|weekly digest|call screening|coming soon|roadmap""", RegexOption.IGNORE_CASE)
        val publicFiles = listOf(
            "../docs/site/how-it-works.html", "../docs/site/privacy.html", "../README.md", "../THIRD_PARTY_NOTICES.md", "../SECURITY.md",
            "src/main/play/listings/en-US/title.txt", "src/main/play/listings/en-US/short-description.txt", "src/main/play/listings/en-US/full-description.txt",
        ).map { File(it) } + File("src/main/play/release-notes/en-US").listFiles().orEmpty().toList()
        for (file in publicFiles) {
            assertEquals("${file.path} mentions something that is not built", emptyList<String>(), unbuilt.findAll(file.readText()).map { it.value }.toList())
        }
    }

    /** Kevin, Oct 9: US English in everything public ("license", "recognized", "optimization"), as in the app. */
    @Test fun publicTextIsInUsEnglish() {
        val british = Regex("""(?i)\b(licen[c]es?|optimis\w+|serialis\w+|recognis\w+|organis\w+|colour\w*|behaviour\w*|centre\w*|favour\w*|catalogue|programme|analyse\w*|defence|authoris\w+|whilst|grey|summaris\w+|realis\w+|minimis\w+|customis\w+|personalis\w+)\b""")
        val publicFiles = listOf(
            "../docs/site/how-it-works.html", "../docs/site/privacy.html", "../README.md", "../THIRD_PARTY_NOTICES.md", "../SECURITY.md",
            "src/main/play/listings/en-US/title.txt", "src/main/play/listings/en-US/short-description.txt", "src/main/play/listings/en-US/full-description.txt",
        ).map { File(it) } + File("src/main/play/release-notes/en-US").listFiles().orEmpty().toList()
        for (file in publicFiles) {
            assertEquals("${file.path} uses British spelling", emptyList<String>(), british.findAll(file.readText()).map { it.value }.toList())
        }
    }

    /** The README is the first public page of the repo: the same words as the app ("looks out for", never "safe", no "her"). */
    @Test fun theReadmeUsesTheAppsWordsAndNeverAssumesAPronoun() {
        val readme = File("../README.md").readText()
        assertEquals(emptyList<String>(), Regex("""\b(safe|safely|safety|secure|dangerous|guarantee[ds]?|protected)\b""", RegexOption.IGNORE_CASE).findAll(readme).map { it.value }.toList())
        assertEquals(emptyList<String>(), Regex("""\b(her|hers|she)\b""", RegexOption.IGNORE_CASE).findAll(readme).map { it.value }.toList())
    }

    /** Kevin, Oct 9 (findings #85): the optional website-blocking step is explained, and both pages say the phone then asks Cloudflare. */
    @Test fun theWebsiteBlockingStepIsExplainedAndCloudflareIsNamedOnEveryPublicPage() {
        val simple = html.substringAfter("id=\"simple-view\"").substringBefore("id=\"detailed-view\"")
        val detailed = html.substringAfter("id=\"detailed-view\"")
        for ((name, part) in listOf("Simple view" to simple, "Detailed view" to detailed)) {
            assertTrue("$name must say what the website-blocking step is", part.contains("website-blocking", ignoreCase = true) || part.contains("Website blocking"))
            assertTrue("$name must name Cloudflare", part.contains("Cloudflare"))
        }
        assertTrue("the Detailed view must say Aegida only reads the setting", detailed.contains("Aegida reads the setting and nothing more"))
        val privacy = File("../docs/site/privacy.html").readText()
        assertTrue("the privacy page must name Cloudflare and the host", privacy.contains("Cloudflare") && privacy.contains(com.aegida.core.model.WebsiteFilter.HOST))
        assertTrue("it must no longer claim nothing else is sent", !privacy.contains("Aegida sends nothing else. "))
    }

    /** Kevin, Oct 10: the note can now name the app and a masked address, and the app keeps what a scam message said; no public page may still say otherwise. */
    @Test fun noPublicPageStillClaimsTheNoteNeverNamesAnAppOrThatNothingAboutAMessageIsKept() {
        val privacy = File("../docs/site/privacy.html").readText()
        for (text in listOf(plain, privacy)) {
            assertTrue(!text.contains("the names of your apps"))
            assertTrue(!text.contains("Never links, message text or app names"))
            assertTrue(!text.contains("The text of the notification is not stored or logged"))
            assertTrue(!text.contains("save, log or send what they say"))
        }
        assertTrue("the privacy page must say what is kept", privacy.contains("first words of what it said"))
    }

    /** Kevin, Oct 10 (findings #88): how a guardian sets up more devices, with the account as an optional step. */
    @Test fun thePageSaysHowAGuardianSetsUpMoreDevicesAndThatAnAccountIsOptional() {
        val simple = html.substringAfter("id=\"simple-view\"").substringBefore("id=\"detailed-view\"")
        val detailed = html.substringAfter("id=\"detailed-view\"")
        assertTrue("the Simple view says what the guardian does", simple.contains("What does my guardian need to do?") && simple.contains("no account is needed"))
        assertTrue("the Detailed view has the guardian-devices section", detailed.contains("Setting up the guardian's devices") && detailed.contains("Optional: an ntfy account"))
        assertTrue("it says Aegida never needs one", detailed.contains("Aegida does not need an account and never asks for one"))
    }

    /** Kevin, Oct 11: the Simple view fell behind the app. It must explain what Mom actually meets on her screens, in the app's own words. */
    @Test fun theSimpleViewExplainsWhatMomMeetsOnHerScreens() {
        val simpleHtml = html.substringAfter("id=\"simple-view\"").substringBefore("id=\"detailed-view\"")
        for (phrase in listOf("needs a tap", Texts.Home.CONFIRM_YES, Texts.Home.CONFIRM_NO, Texts.Home.NOISY_FINE, "waits a week", "copy the name")) {
            assertTrue("the Simple view does not explain: $phrase", simpleHtml.contains(phrase))
        }
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
