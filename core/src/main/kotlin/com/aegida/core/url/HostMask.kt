package com.aegida.core.url

/**
 * A web address made safe to put in a note to a guardian or a History line: the first two letters, a fixed run of stars
 * (so the length of the name is not shown) and the ending, like "pa*****.example". Enough to tell two sites apart and to
 * recognize your own; not enough to type, to search for, or to learn what the owner was reading (Kevin, Oct 10).
 */
object HostMask {
    private const val STARS = "*****"
    private const val SHOWN = 2
    private val NOT_THE_NAME = setOf("www", "m", "mobile")

    fun of(address: String): String {
        val host = hostOf(address)
        if (host.isEmpty() || host.all { it.isDigit() || it == '.' }) return STARS
        // A trailing dot is the same host; a leading "www." or "m." says nothing about which site it is (findings #94).
        val labels = host.trimEnd('.').split('.').let { if (it.size > 2 && it.first() in NOT_THE_NAME) it.drop(1) else it }
        val first = labels.first()
        val shown = if (first.length > SHOWN) first.take(SHOWN) else "" // a short name shows nothing, so something is always hidden
        val ending = if (labels.size > 1) "." + labels.last() else ""
        return shown + STARS + ending
    }

    /** The host part of "https://user@host:8443/path?x#y" or of a bare host. */
    fun hostOf(address: String): String =
        address.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':')
}
