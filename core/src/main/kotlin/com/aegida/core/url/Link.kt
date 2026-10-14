package com.aegida.core.url

import java.net.IDN

/**
 * A link found in text. It is only ever looked at, never fetched (NetworkSingleOwnerTest).
 *
 * [host] is lower-case ASCII (punycode for international names), without a trailing dot, user info
 * or port. [path] starts with "/" and has no query or fragment; it is what feed URL entries match on.
 */
data class Link(val raw: String, val host: String, val path: String) {
    /** The host and each parent domain down to two labels: "a.b.example.com" -> itself, "b.example.com", "example.com". */
    val hostCandidates: List<String>
        get() {
            val labels = host.split('.')
            return (0..labels.size - 2).map { labels.drop(it).joinToString(".") }
        }

    /** The key feed URL entries are built from, by the same function (Links.urlKey). */
    val urlKey: String get() = Links.urlKey(host, path)
}

object Links {
    private const val MAX_LABEL_CHARS = 63 // the DNS limit for one label
    private const val TRAILING = ".,;:!?)]}'\"*"

    /**
     * At most this many characters of one title or text are examined. Longer notifications are cut here, so a
     * link beyond it is not found; a scam link that far down is rarer than a padded text is cheap to write.
     */
    const val MAX_PART_CHARS = 4_096

    /** Links in each of [parts] (each cut at [MAX_PART_CHARS] on its own), in order, without duplicates. */
    fun extractParts(vararg parts: String): List<Link> = parts.flatMap { extract(it) }.distinct()

    /** Every link in the first [MAX_PART_CHARS] characters of [text], in order, without duplicates. Unparseable candidates are dropped. */
    fun extract(text: String): List<Link> {
        val found = LinkedHashSet<Link>()
        LinkScanner.scan(if (text.length > MAX_PART_CHARS) text.substring(0, MAX_PART_CHARS) else text) { candidate ->
            parse(candidate.trimEnd { c -> c in TRAILING })?.let(found::add)
        }
        return found.toList()
    }

    /**
     * Every address in [text], also one that [extract] drops because its candidate does not parse (findings #100). Used on text
     * prepared by [LinkText.prepare] (which turns odd dots into dots), by the detector and by hiding and masking
     * (findings #104); [extract] stays the plain finder pinned by LinksDifferentialTest.
     */
    fun findThorough(text: String): List<Link> {
        val found = LinkedHashSet<Link>()
        val cut = if (text.length > MAX_PART_CHARS) text.substring(0, MAX_PART_CHARS) else text
        LinkScanner.scan(cut) { candidate -> parseOrInner(candidate.trimEnd { c -> c in TRAILING })?.let(found::add) }
        return found.toList()
    }

    /**
     * A candidate that does not parse (a label too long, mixed writing directions) may still hold an address after its first dot:
     * "xא.evil.com" holds evil.com. Try the rest after each dot until one parses, so it is never dropped whole (findings #100).
     */
    private fun parseOrInner(candidate: String): Link? {
        var rest = candidate
        while (rest.contains('.')) {
            parse(rest)?.let { return it }
            rest = rest.substringAfter('.')
        }
        return null
    }

    /** Parses one URL or bare domain. Returns null if there is no usable host. */
    fun parse(raw: String): Link? {
        val noScheme = raw.replace(Regex("""^(?i)https?://"""), "")
        val authorityEnd = noScheme.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) noScheme.length else it }
        val authority = noScheme.substring(0, authorityEnd)
        val rest = noScheme.substring(authorityEnd)
        val hostPort = authority.substringAfterLast('@') // "bank.com@evil.example" really goes to evil.example
        val host = normalizeHost(hostPort.substringBefore(':')) ?: return null
        val path = rest.substringBefore('?').substringBefore('#').ifEmpty { "/" }
        return Link(raw, host, if (path.startsWith("/")) path else "/$path")
    }

    /** Lower-case ASCII host without trailing dots, or null if it is not a plausible domain name. */
    fun normalizeHost(host: String): String? {
        val trimmed = host.trim().trimEnd('.')
        val ascii = if (trimmed.isEmpty() || '.' !in trimmed) null else toAscii(trimmed)
        return ascii?.takeIf { name -> name.split('.').none { it.isEmpty() || it.length > MAX_LABEL_CHARS } }
    }

    private fun toAscii(name: String): String? = try {
        IDN.toASCII(name, IDN.ALLOW_UNASSIGNED).lowercase()
    } catch (ignored: IllegalArgumentException) {
        null // a name IDN cannot encode (bad label, mixed directions) is not a plausible host; the caller treats null as "no host"
    }

    /** Host plus path without a trailing slash; the feed builder uses this same function. */
    fun urlKey(host: String, path: String): String = host + path.trimEnd('/').ifEmpty { "" }
}
