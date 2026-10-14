package com.aegida.guards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every user-facing text in Texts.kt is used somewhere in shipped code (docs/code-health.md). Four were found unused on Oct 11, among
 * them the "can't check / you told me" wording no screen showed; Kevin chose to delete them (Oct 13).
 *
 * A text counts as used when shipped code names it with its object (`Home.SHOW_ME`, `Texts.APP_NAME`), or when code inside its own
 * object uses its bare name. String literals are removed first, so "ON" in an SQL string or two texts both called TITLE cannot
 * make an unused text pass (findings #105).
 */
class UnusedTextsTest {
    private val literals = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\"")

    private fun withoutLiterals(code: String) = literals.replace(code, "\"\"")

    @Test fun everyTextIsUsed() {
        val textsFile = ShippedSources.main.single { it.path.endsWith("core/text/Texts.kt") }
        val texts = withoutLiterals(textsFile.code)
        val objects = Regex("""object (\w+)\s*\{""").findAll(texts).map { m ->
            val open = m.range.last
            var depth = 0
            var close = open
            for (i in open until texts.length) {
                if (texts[i] == '{') depth++
                if (texts[i] == '}') { depth--; if (depth == 0) { close = i; break } }
            }
            Triple(m.groupValues[1], open, close)
        }.toList()
        val others = ShippedSources.main.filter { it !== textsFile }.joinToString("\n") { withoutLiterals(it.code) }

        val declarations = Regex("""const val (\w+)\s*=""").findAll(texts).toList()
        assertTrue("no texts found; the scan is broken", declarations.size > 50)
        val unused = declarations.mapNotNull { d ->
            val name = d.groupValues[1]
            val at = d.range.first
            val owner = objects.filter { at in it.second..it.third }.minByOrNull { it.third - it.second } ?: return@mapNotNull name
            val qualified = Regex("""\b${owner.first}\.$name\b""")
            val ownBody = texts.substring(owner.second, owner.third).replaceFirst(d.value, "")
            val usedInside = Regex("""(?<![.\w])$name\b""").containsMatchIn(ownBody)
            if (qualified.containsMatchIn(others) || qualified.containsMatchIn(texts) || usedInside) null else "${owner.first}.$name"
        }.sorted()
        assertEquals("texts no code uses: delete them, or use them", emptyList<String>(), unused)
    }
}
