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

    @Test fun wordsThatMerelyContainABrandAreNot() {
        assertEquals(emptySet<LinkSign>(), signs("https://purchase.example/"))
        assertEquals(emptySet<LinkSign>(), signs("https://groups.example/"))
    }
}
