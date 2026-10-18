package com.aegida.core.url

/**
 * Every top-level domain, from IANA's list (resources/com/aegida/core/url/tlds.txt, data.iana.org/TLD/tlds-alpha-by-domain.txt).
 * Used to tell "St.Louis" (louis is no domain) from "usps.Sbs" (sbs is) on the phone's screens (findings #100). If the file cannot
 * be read, every ending counts as a domain, so nothing is let through. Refresh it with each release; TopLevelDomainsTest fails when
 * it is more than a year old.
 */
object TopLevelDomains {
    private val list: Set<String> by lazy {
        TopLevelDomains::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.lowercase() }.toSet()
        }.orEmpty()
    }

    /** The "# Version 2026101001, ..." line of the file, or null. */
    val version: String? by lazy {
        TopLevelDomains::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.useLines { lines -> lines.firstOrNull { it.startsWith("#") } }
    }

    fun isDomain(ending: String): Boolean = list.isEmpty() || ending.lowercase() in list

    /**
     * True only when [ending] is on the list read from the file. For letting a link through (a brand's own ending, findings #155), so
     * an unreadable file lets nothing through, the other way round from [isDomain].
     */
    fun isListed(ending: String): Boolean = ending.lowercase() in list

    /** Absolute: the release build renames this class, so a name relative to it would find nothing there (findings #102). */
    private const val RESOURCE = "/com/aegida/core/url/tlds.txt"
}
