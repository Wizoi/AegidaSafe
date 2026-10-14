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

    // Scenario row ST19 (docs/scenarios/scam-texts.md, findings #127): each case is a row of its table, named by its ID.
    private fun upsSignWrongFor(cases: List<Pair<String, String>>, expected: Boolean): List<String> =
        cases.filter { (_, url) -> (LinkSign.BrandLookalike in signs(url)) != expected }.map { it.first }

    @Test fun aParcelScamNameWithUpsInItImitatesUps() {
        val scams = listOf(
            "U1" to "http://ups-redelivery.com/", "U2" to "http://track-ups.com/", "U3" to "http://pick-ups-redelivery.com/",
            "U4" to "http://track-pick-ups.com/", "U5" to "http://parcel-pick-ups.com/", "U6" to "http://ups-parcel-help.com/",
            "U7" to "http://upsdelivery-help.com/", "U8" to "http://pickups-redelivery.com/", "U9" to "http://ups-track.info/",
            "U10" to "http://ups.com-track.top/", "U11" to "http://ups.parcel-fee.example/", "U12" to "http://pop-ups-ups.example/",
            "U13" to "http://set-ups-redelivery.example/", "U14" to "http://www.check-ups-parcel.example/",
            "U15" to "http://upstracking-center.com/", "U16" to "http://upsparcel-redeliver.com/", "U17" to "http://up5-redelivery.top/",
            "U18" to "http://pick-ups-parce1.top/", "U19" to "http://ups-tracking.help-center.top/",
        )
        assertEquals("no UPS sign, but a parcel scam", emptyList<String>(), upsSignWrongFor(scams, expected = true))
    }

    @Test fun anOrdinaryNameWithUpsInItIsNotTheParcelCompany() {
        val ordinary = listOf(
            "O1" to "https://pop-ups-blocker.com/", "O2" to "https://check-ups-clinic.com/", "O3" to "https://sign-ups-form.com/",
            "O4" to "https://setups.com/", "O5" to "https://startups.com/", "O6" to "https://groups.google.com/", "O7" to "https://cups.com/",
            "O8" to "https://pickups-trucks.com/", "O9" to "https://www.pop-ups.com/", "O10" to "https://pick-ups.example/",
            "O11" to "https://sign-ups.example/", "O12" to "https://check-ups.example/", "O13" to "https://upstairs-furniture.com/",
            "O14" to "https://upset-stomach-help.com/", "O15" to "https://backups-online.com/", "O16" to "https://follow-ups-reminder.com/",
            "O17" to "https://www.ups.com/track", "O18" to "https://wwwapps.ups.com/", "O19" to "https://pop-ups.check-ups.example/",
        )
        assertEquals("a UPS sign on an ordinary name", emptyList<String>(), upsSignWrongFor(ordinary, expected = false))
    }

    @Test fun wordsThatMerelyContainABrandAreNot() {
        assertEquals(emptySet<LinkSign>(), signs("https://purchase.example/"))
        assertEquals(emptySet<LinkSign>(), signs("https://groups.example/"))
    }
}
