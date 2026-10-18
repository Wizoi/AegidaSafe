package com.aegida.detectors

import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import com.aegida.detectors.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * The popular-sites rows of docs/scenarios/scam-texts.md (findings #155 part 3): a site on the most-linked list is never called a
 * look-alike, and nothing else changes. The sites are real Majestic Million entries of Oct 18 (share.google ranks 162,
 * sites.google.com 63, media-amazon.com 2,807).
 */
class LinkDetectorPopularSitesTest {
    private val detector = LinkDetector()
    private val popular = listOf("share.google", "sites.google.com", "media-amazon.com")

    private fun feedWith(popularSites: List<String> = popular, scamHosts: List<String> = listOf("evil.example")) =
        ThreatFeed.of(
            now.minus(Duration.ofHours(3)), listOf(FeedSource("test", "test", scamHosts.size)),
            scamHosts + popularSites.map(ThreatFeed::popularKey), emptyList(),
        )

    private fun problems(text: String, feed: ThreatFeed? = feedWith()) =
        detector.evaluate(inputs(feed = feed, event = message(text))).map { it.key.problem to it.key.subject }

    @Test fun aGoogleShareLinkIsNotALookalikeWhenTheSiteIsPopular() { // the field report
        assertEquals(emptyList<Any>(), problems("About the Emgality refill https://share.google/aBcD123"))
    }

    @Test fun anAmazonImageLinkIsNotALookalikeWhenTheSiteIsPopular() {
        assertEquals(emptyList<Any>(), problems("Your order: https://media-amazon.com/images/I/71x.jpg"))
    }

    @Test fun aSubdomainOfAPopularSiteIsNotALookalike() {
        assertEquals(emptyList<Any>(), problems("Photo https://m.media-amazon.com/images/I/71x.jpg"))
        assertEquals(emptyList<Any>(), problems("See https://www.share.google/aBcD123"))
    }

    @Test fun aBrandNameInFrontOfAPopularSiteIsStillALookalike() { // must never: anyone can make a page on a popular host
        assertEquals(
            listOf(Problem.LookalikeLink to "paypal-login.sites.google.com"),
            problems("Your account is locked: https://paypal-login.sites.google.com/view/verify"),
        )
    }

    /** PS9: ownership is the real host's, so Apple's own names under a popular apple.com or icloud.com stay quiet (findings #159). */
    @Test fun aBrandsOwnNameUnderItsPopularSiteIsNotALookalike() {
        val feed = feedWith(popularSites = popular + listOf("apple.com", "icloud.com", "google.com"))
        assertEquals(emptyList<Any>(), problems("Your photos https://icloud.apple.com/photos", feed))
        assertEquals(emptyList<Any>(), problems("Shared album https://apple.icloud.com/sharedalbum", feed))
        assertEquals(emptyList<Any>(), problems("Directions https://maps.google.com/?q=clinic", feed))
        assertEquals(
            listOf(Problem.LookalikeLink to "paypal-login.sites.google.com"),
            problems("Your account is locked: https://paypal-login.sites.google.com/view/verify", feed),
        )
    }

    @Test fun mixedAlphabetsInFrontOfAPopularSiteAreStillALookalike() {
        val found = problems("Locked: https://раypal.sites.google.com/view/verify") // "раypal": two Cyrillic letters
        assertEquals(listOf(Problem.LookalikeLink), found.map { it.first })
        assertTrue(found.single().second.endsWith(".sites.google.com"))
    }

    @Test fun aPopularSiteOnTheScamListIsStillAScamLink() { // must never: the scam list wins over the popular list
        assertEquals(
            listOf(Problem.ScamLink to "share.google"),
            problems("Claim it https://share.google/aBcD123", feedWith(scamHosts = listOf("share.google"))),
        )
    }

    @Test fun aLookAlikeLetterVersionOfAPopularSiteIsStillALookalike() {
        assertEquals(listOf(Problem.LookalikeLink to "media-amaz0n.com"), problems("Refund https://media-amaz0n.com/claim"))
    }

    @Test fun aPopularSiteNameInsideAnotherAddressIsStillALookalike() {
        assertEquals(
            listOf(Problem.LookalikeLink to "media-amazon.com.refund-help.example"),
            problems("Refund https://media-amazon.com.refund-help.example/claim"),
        )
    }

    @Test fun anOldFeedWithoutThePopularListBehavesAsToday() {
        assertEquals(
            listOf(Problem.LookalikeLink to "media-amazon.com"),
            problems("Your order: https://media-amazon.com/images/I/71x.jpg", feedWith(popularSites = emptyList())),
        )
    }

    @Test fun noFeedAtAllBehavesAsToday() { // offline since install: no list has come down yet
        assertEquals(
            listOf(Problem.LookalikeLink to "media-amazon.com"),
            problems("Your order: https://media-amazon.com/images/I/71x.jpg", feed = null),
        )
    }
}
