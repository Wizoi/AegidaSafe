package com.aegida.core

import com.aegida.core.model.WebsiteSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteSitesTest {
    @Test fun theSiteIsWhatFollowsWebUpToTheSemicolon() {
        // The channel id Chrome really used on the emulator (findings #18).
        assertEquals("http://localhost:8765", WebsiteSites.siteOf("web:http://localhost:8765;13435952006713133"))
        assertEquals("https://news.example", WebsiteSites.siteOf("web:https://news.example;42"))
    }

    @Test fun anythingElseIsNotASite() {
        assertNull(WebsiteSites.siteOf(null))
        assertNull(WebsiteSites.siteOf("messages"))
        assertNull(WebsiteSites.siteOf("web:"))
        assertNull(WebsiteSites.siteOf("web:;7"))
    }
}
