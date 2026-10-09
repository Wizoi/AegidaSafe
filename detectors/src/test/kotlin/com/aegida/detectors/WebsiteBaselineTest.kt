package com.aegida.detectors

import com.aegida.core.model.Baseline
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Android will not tell a plain listener which websites already have notifications (findings #84), so the baseline starts with
 * no websites and every site is asked about once: "yes, that was me" adds it (Kevin, Oct 9).
 */
class WebsiteBaselineTest {
    private val site = "http://localhost:8765"

    @Test fun theBaselineStartsWithNoWebsitesSoEachIsAskedAboutOnce() {
        assertEquals(emptySet<String>(), Baseline.of(snapshot()).websites)
    }

    @Test fun sayingItWasHersAddsTheSiteAndKeepsTheOthers() {
        val before = Baseline.of(snapshot()).copy(websites = setOf("https://news.example"))
        assertEquals(setOf("https://news.example", site), before.accepting(Problem.NewWebsiteNotifications, site, snapshot()).websites)
    }

    @Test fun aBaselineFromBeforeWebsitesWereRecordedTakesHerYesToo() {
        assertEquals(setOf(site), Baseline.of(snapshot()).copy(websites = null).accepting(Problem.NewWebsiteNotifications, site, snapshot()).websites)
    }
}
