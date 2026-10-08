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
    // A scheme URL, or a bare domain with a letters-only top-level label (so "e.g." and "3.14" are not links).
    private val CANDIDATE = Regex(
        """(?i)\b(?:https?://[^\s<>"'`]+|(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+\p{L}{2,}(?::\d+)?(?:/[^\s<>"'`]*)?)""",
    )
    private const val TRAILING = ".,;:!?)]}'\"*"

    /** Every link in [text], in order, without duplicates. Unparseable candidates are dropped. */
    fun extract(text: String): List<Link> =
        CANDIDATE.findAll(text).mapNotNull { parse(it.value.trimEnd { c -> c in TRAILING }) }.distinct().toList()

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
