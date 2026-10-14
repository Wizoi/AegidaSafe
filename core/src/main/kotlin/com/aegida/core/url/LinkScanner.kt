package com.aegida.core.url

/**
 * Finds link candidates in text: a scheme URL, or a bare domain with a letters-only top-level label (so "e.g." and "3.14" are not
 * links). Scanned by hand in one pass: the regex this replaced recursed once per label and overflowed the stack on a long "a.a.a.a"
 * run (R11). It finds the same candidates (LinksTest, LinksLongTextTest). Used by [Links] only.
 */
internal object LinkScanner {
    private const val HTTPS = "https://"
    private const val HTTP = "http://"
    private const val MIN_TOP_LEVEL_LETTERS = 2

    /** Where a candidate ends ([candidateEnd], or -1 for none) and where scanning goes on from ([next]). */
    private class Step(val candidateEnd: Int, val next: Int)

    /** The labels "name." one after another from a start: [dotEnds] are where the text after each dot starts; [end] is where the run stopped. */
    private class LabelRun(val dotEnds: List<Int>, val end: Int)

    fun scan(t: String, onCandidate: (String) -> Unit) {
        var i = 0
        while (i < t.length) {
            val startsCandidate = isAlnum(t[i]) && !(i > 0 && isWord(t[i - 1]))
            i = if (startsCandidate) scanFrom(t, i, onCandidate) else i + 1
        }
    }

    private fun scanFrom(t: String, i: Int, onCandidate: (String) -> Unit): Int {
        val schemeEnd = schemeUrlEnd(t, i)
        val step = if (schemeEnd > 0) Step(schemeEnd, schemeEnd) else bareDomain(t, i)
        if (step.candidateEnd > 0) onCandidate(t.substring(i, step.candidateEnd))
        return step.next
    }

    private fun bareDomain(t: String, i: Int): Step {
        val run = labelRun(t, i)
        val topLevelEnd = topLevelEnd(t, run.dotEnds)
        // No top-level label anywhere in this run of labels, so no later start inside it can match either.
        if (topLevelEnd < 0) return Step(-1, maxOf(i + 1, run.end))
        val end = urlEnd(t, topLevelEnd)
        return Step(end, end)
    }

    private fun labelRun(t: String, i: Int): LabelRun {
        val dotEnds = ArrayList<Int>()
        var j = i
        var more = true
        while (more && j < t.length && isAlnum(t[j])) {
            var e = j
            while (e < t.length && (isAlnum(t[e]) || t[e] == '-')) e++
            more = e < t.length && isAlnum(t[e - 1]) && t[e] == '.'
            if (more) {
                dotEnds.add(e + 1)
                j = e + 1
            }
        }
        return LabelRun(dotEnds, j)
    }

    /** End of the last label made of letters only (at least two), or -1. */
    private fun topLevelEnd(t: String, dotEnds: List<Int>): Int = dotEnds.asReversed().firstNotNullOfOrNull { start ->
        var k = start
        while (k < t.length && Character.isLetter(t[k])) k++
        if (k - start >= MIN_TOP_LEVEL_LETTERS) k else null
    } ?: -1

    /** End of the optional ":port" and the optional "/path" after the top-level label that ends at [from]. */
    private fun urlEnd(t: String, from: Int): Int {
        var end = from
        if (end + 1 < t.length && t[end] == ':' && t[end + 1] in '0'..'9') {
            end++
            while (end < t.length && t[end] in '0'..'9') end++
        }
        if (end < t.length && t[end] == '/') while (end < t.length && !isUrlBreak(t[end])) end++
        return end
    }

    /** End of an http(s):// URL starting at [i] (with at least one character after the scheme), or -1. */
    private fun schemeUrlEnd(t: String, i: Int): Int {
        val prefix = when {
            t.regionMatches(i, HTTPS, 0, HTTPS.length, ignoreCase = true) -> HTTPS.length
            t.regionMatches(i, HTTP, 0, HTTP.length, ignoreCase = true) -> HTTP.length
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
}
