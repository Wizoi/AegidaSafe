package com.aegida.core.url

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkSignsTest {
    private fun signs(url: String) = LinkSigns.of(Links.parse(url)!!)

    @Test fun shortenersAreSeenEvenOnSubdomains() {
        assertEquals(setOf(LinkSign.Shortener), signs("https://bit.ly/3xYz"))
        assertEquals(setOf(LinkSign.Shortener), signs("https://www.tinyurl.com/abc"))
    }

    @Test fun mixedAlphabetsInOneNameAreALookalike() {
        // "pаypal" with a Cyrillic "а": both mixed script and a brand look-alike.
        assertEquals(setOf(LinkSign.MixedScript, LinkSign.BrandLookalike), signs("https://pаypal.com/"))
    }

    @Test fun anAllCyrillicNameIsNotMixed() {
        assertEquals(emptySet<LinkSign>(), signs("https://пример.рф/"))
    }

    @Test fun brandWordsOnOtherDomainsAreLookalikes() {
        assertEquals(setOf(LinkSign.BrandLookalike), signs("http://paypa1-login.example/"))
        assertEquals(setOf(LinkSign.BrandLookalike), signs("http://usps.redelivery-fee.example/"))
        assertEquals(setOf(LinkSign.BrandLookalike), signs("http://www.paypal.com.account-check.example/"))
        assertEquals(setOf(LinkSign.BrandLookalike), signs("http://arnazon.example/")) // rn -> m
    }

    @Test fun theBrandsOwnSitesAreNot() {
        assertEquals(emptySet<LinkSign>(), signs("https://www.paypal.com/signin"))
        assertEquals(emptySet<LinkSign>(), signs("https://tools.usps.com/redelivery"))
        assertEquals(emptySet<LinkSign>(), signs("https://www.irs.gov/refunds"))
    }

    // Scenario rows ST13, ST14, ST16-ST18 (docs/scenarios/scam-texts.md) and WP13 (website-popups.md); findings #108.

    @Test fun aRealBrandShortOrCountryLinkIsNotALookalike() {
        for (url in listOf(
            "https://apple.co/3abc", "https://paypal.me/kevin", "https://www.amazon.de/gp/css", "https://www.amazon.fr/x",
            "https://www.google.co.uk/maps", "https://www.google.de/", "https://amzn.to/3xyz", "https://a.co/d/abc", "https://g.co/help",
            "https://youtu.be/dQw4w9WgXcQ", "https://www.amazon.co.uk/orders",
        )) {
            assertEquals(url, false, LinkSign.BrandLookalike in signs(url))
        }
    }

    @Test fun aBrandWordOnAnUnlistedEndingOrBeforeAnotherDomainIsStillALookalike() {
        for (url in listOf(
            "http://amazon.de-login.example/", "http://paypal.me.verify-account.example/", "http://apple.co.id-check.example/",
            "http://www.google.co.uk.signin.example/", "http://amazon.xyz/", "http://paypal.ru/", "http://apple.com.co/",
        )) {
            assertEquals(url, true, LinkSign.BrandLookalike in signs(url))
        }
    }

    @Test fun anAllCyrillicAppleIsALookalike() {
        // а р р ӏ е: every letter Cyrillic, so one script and not "mixed", but it reads as "apple" (ӏ is the palochka).
        assertEquals(setOf(LinkSign.BrandLookalike), signs("https://аррӏе.com/id"))
        assertEquals(setOf(LinkSign.BrandLookalike), signs("https://аррӏе.рф/"))
    }

    @Test fun theWordPopUpsIsNotTheParcelCompany() {
        for (url in listOf("https://www.pop-ups.com/", "https://pop-ups.example/x", "pop-ups.tap", "https://sign-ups.example/", "https://check-ups.example/")) {
            assertEquals(url, emptySet<LinkSign>(), signs(url))
        }
    }

    @Test fun upsBeforeOrAfterAHyphenIsStillTheParcelCompany() {
        for (url in listOf("http://ups-redelivery.example/", "http://track-ups.example/", "http://ups.parcel-fee.example/", "http://pop-ups-ups.example/")) {
            assertEquals(url, setOf(LinkSign.BrandLookalike), signs(url))
        }
    }

    /** ST18 variant (findings #120): an English "-ups" word in front does not hide the parcel company when more follows it. */
    @Test fun aParcelScamWithPickUpsOrSetUpsBeforeMoreWordsStillImitatesUps() {
        for (url in listOf("http://pick-ups-redelivery.example/", "http://set-ups-redelivery.example/", "http://www.check-ups-parcel.example/")) {
            assertEquals(url, setOf(LinkSign.BrandLookalike), signs(url))
        }
        for (url in listOf("https://pick-ups.example/", "https://www.pop-ups.com/", "https://sign-ups.example/", "https://check-ups.example/")) {
            assertEquals(url, emptySet<LinkSign>(), signs(url))
        }
    }

    @Test fun wordsThatMerelyContainABrandAreNot() {
        assertEquals(emptySet<LinkSign>(), signs("https://purchase.example/"))
        assertEquals(emptySet<LinkSign>(), signs("https://groups.example/"))
    }
}
