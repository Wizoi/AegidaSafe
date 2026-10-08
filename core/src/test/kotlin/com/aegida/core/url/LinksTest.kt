package com.aegida.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The traps real messages and feeds contain (design section 7, feed contract tests). */
class LinksTest {
    private fun hosts(text: String) = Links.extract(text).map { it.host }

    @Test fun findsSchemeAndBareLinksAndDropsTrailingPunctuation() {
        assertEquals(
            listOf("examp1e-bank.test", "usps-redelivery.example"),
            hosts("Locked! Verify at http://examp1e-bank.test/login. Or usps-redelivery.example/track)"),
        )
    }

    @Test fun userInfoTrickResolvesToTheRealHost() {
        assertEquals("evil.example", Links.parse("https://www.paypal.com@evil.example/signin")!!.host)
    }

    @Test fun uppercaseTrailingDotAndPortAreNormalised() {
        val link = Links.parse("HTTPS://WWW.Example.COM.:8443/Path/?q=1#top")!!
        assertEquals("www.example.com", link.host)
        assertEquals("/Path/", link.path)
        assertEquals("www.example.com/Path", link.urlKey)
    }

    @Test fun internationalNamesBecomePunycode() {
        assertEquals("xn--pypal-4ve.com", Links.parse("https://pаypal.com/")!!.host) // Cyrillic "а"
        assertEquals("xn--pypal-4ve.com", Links.parse("xn--pypal-4ve.com")!!.host)
    }

    @Test fun wwwAndSubdomainsMatchTheApexThroughCandidates() {
        assertEquals(
            listOf("a.b.evil.example", "b.evil.example", "evil.example"),
            Links.parse("http://a.b.evil.example/x")!!.hostCandidates,
        )
    }

    @Test fun thingsThatAreNotLinks() {
        assertEquals(emptyList<String>(), hosts("e.g. pi is 3.14, call 555-0100, see you at 5.30"))
        assertNull(Links.parse("http://localhost/x"))
        assertNull(Links.parse("http://a..b/x"))
    }

    @Test fun extractionNeverRepeatsALink() {
        assertEquals(1, Links.extract("go to bad.example now: bad.example").size)
    }

    @Test fun emptyPathIsSlash() {
        assertEquals("/", Links.parse("bad.example")!!.path)
        assertEquals("bad.example", Links.parse("bad.example")!!.urlKey)
        assertTrue(Links.parse("bad.example/")!!.urlKey == "bad.example")
    }
}
