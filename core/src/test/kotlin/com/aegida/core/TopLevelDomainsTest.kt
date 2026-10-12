package com.aegida.core

import com.aegida.core.url.TopLevelDomains
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** IANA's list of top-level domains ships in :core (findings #100). New domains are added over time, so it is refreshed with releases. */
class TopLevelDomainsTest {
    @Test fun realEndingsAreDomainsAndOrdinaryWordsAreNot() {
        for (ending in listOf("com", "Sbs", "gov", "lol", "bank", "top")) assertTrue(ending, TopLevelDomains.isDomain(ending))
        for (word in listOf("smith", "Louis", "please", "lee")) assertFalse(word, TopLevelDomains.isDomain(word))
    }

    @Test fun theListIsLessThanAYearOld() {
        val version = TopLevelDomains.version!!
        val date = Regex("""Version (\d{4})(\d{2})(\d{2})""").find(version)!!.destructured.let { (y, m, d) -> LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }
        val age = ChronoUnit.DAYS.between(date, LocalDate.now())
        assertTrue("tlds.txt is $age days old: download data.iana.org/TLD/tlds-alpha-by-domain.txt again", age < 365)
    }
}
