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

    private fun text(name: String) = File(listing, name).readText().trim()

    @Test fun titleAndDescriptionsFitPlaysLimits() {
        assertTrue("title is ${text("title.txt").length} chars, limit 30", text("title.txt").length in 1..30)
        assertTrue("short description is ${text("short-description.txt").length} chars, limit 80", text("short-description.txt").length in 1..80)
        assertTrue("full description is ${text("full-description.txt").length} chars, limit 4000", text("full-description.txt").length in 1..4000)
    }

    @Test fun theStorePageNeverSaysSafe() {
        val words = Regex("""\b(safe|safely|safety|secure|secured|100%|guarantee[ds]?|protected)\b""", RegexOption.IGNORE_CASE)
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
            assertEquals("${file.path} claims the code is open while the repo is private", emptyList<String>(), claim.findAll(file.readText()).map { it.value }.toList())
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
        for (needed in listOf("ntfy.sh", "GitHub Pages", "does <strong>not</strong> save, log or send", "child&rsquo;s phone", "Last updated")) {
            assertTrue("the privacy policy no longer mentions: $needed", text.contains(needed))
        }
        val words = Regex("""\b(safe|safely|safety|secure|secured|100%|guarantee[ds]?|protected)\b""", RegexOption.IGNORE_CASE)
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
        fun int(at: Int) = ((b[at].toInt() and 255) shl 24) or ((b[at + 1].toInt() and 255) shl 16) or ((b[at + 2].toInt() and 255) shl 8) or (b[at + 3].toInt() and 255)
        return int(16) to int(20)
    }
}
