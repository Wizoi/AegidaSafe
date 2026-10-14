package com.aegida.guards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every user-facing text in Texts.kt is used somewhere in shipped code (docs/code-health.md). Four were found unused on Oct 11, among
 * them the "can't check / you told me" wording that no screen showed; Kevin chose to delete them (Oct 13). Approximate: a name counts
 * as used when it appears in shipped code other than its own declaration.
 */
class UnusedTextsTest {
    @Test fun everyTextIsUsed() {
        val texts = ShippedSources.main.single { it.path.endsWith("core/text/Texts.kt") }
        val names = Regex("""const val (\w+)\s*=""").findAll(texts.code).map { it.groupValues[1] }.toSet()
        assertTrue("no texts found; the scan is broken", names.size > 50)
        val code = ShippedSources.main.joinToString("\n") { it.code }
        val unused = names.filter { name -> Regex("""\b$name\b""").findAll(code).count() < 2 }.sorted()
        assertEquals("texts no code uses: delete them, or use them", emptyList<String>(), unused)
    }
}
