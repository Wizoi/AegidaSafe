package com.aegida.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Play store page lives in the repo (`app/src/main/play`, pushed by Gradle Play Publisher) so it is reviewed
 * like code. These are Play's own limits, plus Aegida's rule: nothing the store says may claim "safe".
 * Finding #68.
 */
class PlayListingTest {
    private val play = File("src/main/play")
    private val listing = File(play, "listings/en-US")

    /** "protect(s)" is out too (T6); "protection" is allowed because the site names "Website protection". */
    private val bannedWords = Regex("""\b(safe|safely|safety|secure|secured|100%|guarantee[ds]?|protected|protects?)\b""", RegexOption.IGNORE_CASE)

    private fun text(name: String) = File(listing, name).readText().trim()

    @Test fun titleAndDescriptionsFitPlaysLimits() {
        assertTrue("title is ${text("title.txt").length} chars, limit 30", text("title.txt").length in 1..30)
        assertTrue("short description is ${text("short-description.txt").length} chars, limit 80", text("short-description.txt").length in 1..80)
        assertTrue("full description is ${text("full-description.txt").length} chars, limit 4000", text("full-description.txt").length in 1..4000)
    }

    @Test fun theStorePageNeverSaysSafe() {
        val words = bannedWords
        for (name in listOf("title.txt", "short-description.txt", "full-description.txt")) {
            assertEquals("$name makes a claim Aegida does not make", emptyList<String>(), words.findAll(text(name)).map { it.value }.toList())
        }
        for (notes in File(play, "release-notes/en-US").listFiles().orEmpty()) {
            assertEquals("${notes.name} makes a claim Aegida does not make", emptyList<String>(), words.findAll(notes.readText()).map { it.value }.toList())
        }
    }

    /**
     * The repo is private until Kevin opens it, so nothing public may say the code can be read. When he opens it,
     * delete this test and add the sentence back (docs/PUBLISH.md, decisions.md).
     */
    @Test fun nothingPublicClaimsOpenSourceWhileTheRepoIsPrivate() {
        val claim = Regex("""open[ -]source|code is open|MIT licen[cs]e|read how it works""", RegexOption.IGNORE_CASE)
        val files = listOf("full-description.txt", "short-description.txt", "title.txt").map { File(listing, it) } +
            File("../docs/site/privacy.html") + File(play, "release-notes/en-US").listFiles().orEmpty().toList()
        for (file in files) {
            assertEquals("${file.path} claims the code is open while the repo is private", emptyList<String>(),
                claim.findAll(file.readText()).map { it.value }.toList())
        }
    }

    /** Play will not save a listing change for an app with no contact email; one owner for the address, shared with the policy page. */
    @Test fun theStoreContactEmailIsTheAddressOnThePrivacyPage() {
        val email = File(play, "contact-email.txt").readText().trim()
        assertTrue("contact-email.txt is not an email address: $email", Regex("""[^@\s]+@[^@\s]+\.[a-z]{2,}""").matches(email))
        assertTrue("the privacy page does not give $email as the contact", File("../docs/site/privacy.html").readText().contains(email))
    }

    /** Play Console needs a public URL for this page. It is published by the feed workflow (docs/site/privacy.html). */
    @Test fun thePrivacyPolicyPageExistsAndNeverSaysSafe() {
        val page = File("../docs/site/privacy.html")
        assertTrue("no privacy policy at ${page.path}", page.isFile)
        val text = page.readText()
        for (needed in listOf("ntfy.sh", "GitHub Pages", "does <strong>not</strong> send what they say", "child&rsquo;s phone", "Last updated")) {
            assertTrue("the privacy policy no longer mentions: $needed", text.contains(needed))
        }
        val words = bannedWords
        assertEquals("the privacy policy makes a claim Aegida does not make", emptyList<String>(), words.findAll(text).map { it.value }.toList())
    }

    /** The store description and the app both point to the same public page. */
    @Test fun theStoreDescriptionLinksToTheHowItWorksPage() {
        assertTrue(text("full-description.txt").contains(com.aegida.core.net.PublicPages.HOW_IT_WORKS))
    }

    @Test fun theIconAndFeatureGraphicHaveTheSizesPlayAsks() {
        assertEquals(512 to 512, size("graphics/icon/icon.png"))
        assertEquals(1024 to 500, size("graphics/feature-graphic/feature-graphic.png"))
    }

    @Test fun thereAreAtLeastTwoPhoneScreenshotsOfPlayAcceptedShape() {
        val shots = File(listing, "graphics/phone-screenshots").listFiles { f -> f.extension == "png" }.orEmpty()
        assertTrue("Play needs 2 to 8 phone screenshots, found ${shots.size}", shots.size in 2..8)
        for (shot in shots) {
            val (w, h) = pngSize(shot)
            assertTrue("${shot.name} is ${w}x$h; sides must be 320..3840 px", w in 320..3840 && h in 320..3840)
            assertTrue("${shot.name} is ${w}x$h; the long side may be at most twice the short side", maxOf(w, h) <= 2 * minOf(w, h))
        }
    }

    /** Release notes are per versionCode and Play allows 500 characters. A bump without notes fails here. */
    @Test fun thisVersionHasReleaseNotesWithinPlaysLimit() {
        val code = Regex("""versionCode\s*=\s*(\d+)""").find(File("build.gradle.kts").readText())!!.groupValues[1]
        val notes = File(play, "release-notes/en-US/$code.txt")
        assertTrue("no release notes for versionCode $code at ${notes.path}", notes.isFile)
        assertTrue("release notes are ${notes.readText().trim().length} chars, limit 500", notes.readText().trim().length in 1..500)
    }

    private fun size(path: String): Pair<Int, Int> = pngSize(File(listing, path))

    /** Width and height sit big-endian at bytes 16..23 of a PNG (the IHDR chunk); the test classpath has no ImageIO. */
    private fun pngSize(file: File): Pair<Int, Int> {
        val b = file.readBytes()
        require(b.size > 24 && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()) { "${file.name} is not a PNG" }
        fun int(at: Int) = ((b[at].toInt() and 255) shl 24) or ((b[at + 1].toInt() and 255) shl 16) or
            ((b[at + 2].toInt() and 255) shl 8) or (b[at + 3].toInt() and 255)
        return int(16) to int(20)
    }

    /** GS13 (findings #106): the store text says the Galaxy Store counts like Play on a Samsung, wherever it says which apps count as new. */
    @Test fun whereTheStoreTextSaysWhichAppsCountAsNewItNamesTheGalaxyStoreOnASamsung() {
        val lines = text("full-description.txt").lines().filter { it.contains("outside the Play Store") || it.contains("outside Google Play") }
        assertTrue("the store text says which apps count as new", lines.isNotEmpty())
        for (line in lines) assertTrue(line, line.contains("on a Samsung, the Galaxy Store"))
    }

    /** Kevin, Oct 10: how to say the name, in the store text and on the page (AY-gih-dah). */
    @Test fun theNameComesWithItsPronunciationInTheStoreTextAndOnThePage() {
        assertTrue(File(play, "listings/en-US/full-description.txt").readText().contains("Aegida (say it AY-gih-dah)"))
        val page = File("../docs/site/how-it-works.html").readText()
        assertTrue(page.contains("<b>AY-gih-dah</b>"))
        assertTrue(page.contains("How Aegida (AY-gih-dah) works"))
    }

    /** T1.2: Aegida does keep a few words, on the phone, so no public page may say it keeps none. */
    @Test fun noPublicPageSaysAegidaDoesNotKeepWhatMessagesSay() {
        val denial = Regex("""\b(does not|doesn't|doesn&rsquo;t|do not|don't|never) keep\b""", RegexOption.IGNORE_CASE)
        for (file in listOf(File(listing, "full-description.txt"), File("../docs/site/how-it-works.html"), File("../docs/site/privacy.html"))) {
            assertEquals("${file.path} says Aegida does not keep something it does keep", emptyList<String>(),
                denial.findAll(file.readText()).map { it.value }.toList())
        }
    }

    /**
     * NoisyAppDetector keeps one example title (up to 200 characters) with the warning until she has seen it, so the
     * pages may not say a notice's words are never kept, or that only a time and a count are kept, and must name the title.
     */
    @Test fun thePagesSayTheOneExampleTitleAnAdsWarningKeepsUntilItIsSeen() {
        val privacy = File("../docs/site/privacy.html").readText()
        val how = File("../docs/site/how-it-works.html").readText()
        for ((page, old) in listOf(
            privacy to "Never what the notice said.",
            privacy to "When one looks like a scam, Aegida keeps",
            how to "Aegida keeps only the time and a count of those signs per notice, for a week.",
        )) assertTrue("a page still says: $old", !page.contains(old))
        assertTrue("privacy.html does not name the example title", privacy.contains("one example title"))
        assertTrue("how-it-works.html does not name the example title", how.contains("one example title"))
    }

    /** T1.3: any "N days" History claim is the real time (DailyPruning.HISTORY_KEPT), and the listing makes the claim. */
    @Test fun theHistoryDaysOnThePublicPagesAreTheRealOnes() {
        val real = com.aegida.alerts.DailyPruning.HISTORY_KEPT.toDays().toInt()
        val claim = Regex("""(\d+)[ -]days? (?:of )?history|history keeps[^.]{0,60}?(\d+) days""", RegexOption.IGNORE_CASE)
        fun days(file: File) = claim.findAll(file.readText()).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt() }.toList()
        val listed = days(File(listing, "full-description.txt"))
        assertTrue("the listing no longer says how long History keeps its line", listed.isNotEmpty())
        for (file in listOf(File(listing, "full-description.txt"), File("../docs/site/how-it-works.html"), File("../docs/site/privacy.html"))) {
            for (n in days(file)) assertEquals("${file.path} says History keeps $n days", real, n)
        }
    }

    /** T6: the short description and the "For families" paragraph the testers asked for. */
    @Test fun theListingSpeaksToFamiliesSettingUpForAParent() {
        assertEquals("Spots scam links and fake virus pop-ups. Can tell one family member you choose.", text("short-description.txt"))
        assertTrue(text("full-description.txt").contains("Setting this up for a parent or grandparent?"))
    }

    /** T7: the family-facing page gives the store contact address in its own sentence (the footer has it too), and asks for a review. */
    @Test fun theHowItWorksPageGivesTheStoreContactEmail() {
        val email = File(play, "contact-email.txt").readText().trim()
        val page = File("../docs/site/how-it-works.html").readText()
        assertTrue("how-it-works.html has no \"Questions or a problem? Email\" sentence for $email",
            page.contains("Questions or a problem? Email <a href=\"mailto:$email\">$email</a>."))
        assertTrue("how-it-works.html no longer asks for a short review on Google Play", page.contains("a short review on Google Play"))
    }
}
