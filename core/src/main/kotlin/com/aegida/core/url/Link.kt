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
        scan(if (text.length > MAX_PART_CHARS) text.substring(0, MAX_PART_CHARS) else text) { candidate ->
            parse(candidate.trimEnd { c -> c in TRAILING })?.let(found::add)
        }
        return found.toList()
    }

    /**
     * For hiding and masking only (never for detection, which [extract] owns): every address in [text], also one that [extract]
     * drops because its candidate does not parse, and ideographic or fullwidth dots ("paypal。com") taken as dots (findings #100).
     * Erring towards finding too much is right here: a found address is only hidden or masked.
     */
    fun findForMasking(text: String): List<Link> {
        val found = LinkedHashSet<Link>()
        val cut = if (text.length > MAX_PART_CHARS) text.substring(0, MAX_PART_CHARS) else text
        scan(withPlainDots(cut)) { candidate -> parseOrInner(candidate.trimEnd { c -> c in TRAILING })?.let(found::add) }
        return found.toList()
    }

    /** Ideographic and fullwidth dots ("paypal。com") are dots to a browser, so they are dots here too (findings #100). Same length. */
    private fun withPlainDots(text: String): String = text.replace('。', '.').replace('．', '.').replace('｡', '.')

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

    // A scheme URL, or a bare domain with a letters-only top-level label (so "e.g." and "3.14" are not links).
    // Scanned by hand in one pass: the regex this replaced recursed once per label and overflowed the stack
    // on a long "a.a.a.a" run (R11). It finds the same candidates (LinksTest, LinksLongTextTest).
    private fun scan(t: String, onCandidate: (String) -> Unit) {
        val n = t.length
        var i = 0
        while (i < n) {
            if (!isAlnum(t[i]) || (i > 0 && isWord(t[i - 1]))) { i++; continue }
            val schemeEnd = schemeUrlEnd(t, i)
            if (schemeEnd > 0) { onCandidate(t.substring(i, schemeEnd)); i = schemeEnd; continue }
            // Labels "name." one after another; dotEnds[k] is where the text after the (k+1)th dot starts.
            val dotEnds = ArrayList<Int>()
            var j = i
            while (j < n && isAlnum(t[j])) {
                var e = j
                while (e < n && (isAlnum(t[e]) || t[e] == '-')) e++
                if (!isAlnum(t[e - 1]) || e >= n || t[e] != '.') break
                dotEnds.add(e + 1)
                j = e + 1
            }
            var end = -1
            for (m in dotEnds.indices.reversed()) {
                var k = dotEnds[m]
                while (k < n && Character.isLetter(t[k])) k++
                if (k - dotEnds[m] >= 2) { end = k; break }
            }
            // No top-level label anywhere in this run of labels, so no later start inside it can match either.
            if (end < 0) { i = maxOf(i + 1, j); continue }
            if (end + 1 < n && t[end] == ':' && t[end + 1] in '0'..'9') {
                end++
                while (end < n && t[end] in '0'..'9') end++
            }
            if (end < n && t[end] == '/') while (end < n && !isUrlBreak(t[end])) end++
            onCandidate(t.substring(i, end))
            i = end
        }
    }

    /** End of an http(s):// URL starting at [i] (with at least one character after the scheme), or -1. */
    private fun schemeUrlEnd(t: String, i: Int): Int {
        val prefix = when {
            t.regionMatches(i, "https://", 0, 8, ignoreCase = true) -> 8
            t.regionMatches(i, "http://", 0, 7, ignoreCase = true) -> 7
            else -> return -1
        }
        var e = i + prefix
        while (e < t.length && !isUrlBreak(t[e])) e++
        return if (e > i + prefix) e else -1
    }

    private fun isUrlBreak(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r' ||
        c == '<' || c == '>' || c == '"' || c == '\'' || c == '`'

    private fun isAlnum(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> Character.isLetter(c)
    }

    private fun isWord(c: Char) = c == '_' || Character.isLetterOrDigit(c)

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
        if (trimmed.isEmpty() || '.' !in trimmed) return null
        val ascii = try {
            IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED)
        } catch (e: IllegalArgumentException) {
            return null
        }.lowercase()
        if (ascii.split('.').any { it.isEmpty() || it.length > 63 }) return null
        return ascii
    }

    /** Host plus path without a trailing slash; the feed builder uses this same function. */
    fun urlKey(host: String, path: String): String = host + path.trimEnd('/').ifEmpty { "" }
}
