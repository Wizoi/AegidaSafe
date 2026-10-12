package com.aegida.guards

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every entry in scripts/mutants.json must still find its text exactly once in its file, or that mutation check silently stops
 * running (findings #94 (7) and #98 (3): it happened twice). The scripts write one entry per line; this reads them plainly.
 */
class MutantsMatchTest {
    private val field = Regex(""""(name|file|find)": "((?:[^"\\]|\\.)*)"""")

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun occurrences(text: String, find: String): Int {
        var count = 0
        var at = text.indexOf(find)
        while (at >= 0) {
            count++
            at = text.indexOf(find, at + 1)
        }
        return count
    }

    @Test fun everyMutationCheckFindsItsTextExactlyOnce() {
        val root = ShippedSources.root
        val entries = File(root, "scripts/mutants.json").readLines().filter { it.trim().startsWith("{") }
        val broken = entries.mapNotNull { line ->
            val fields = field.findAll(line).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            val name = fields["name"] ?: return@mapNotNull "unreadable entry: ${line.take(60)}"
            val file = File(root, fields.getValue("file"))
            if (!file.isFile) return@mapNotNull "$name: no file ${fields["file"]}"
            val count = occurrences(file.readText().replace("\r\n", "\n"), fields.getValue("find"))
            if (count == 1) null else "$name: its text is in the file $count times"
        }
        assertEquals("mutation checks that no longer match their code", emptyList<String>(), broken)
        // One entry per line, every entry read, names unique (findings #99).
        val names = entries.mapNotNull { line -> field.findAll(line).firstOrNull { it.groupValues[1] == "name" }?.groupValues?.get(2) }
        val declared = File(root, "scripts/mutants.json").readText().split("\"name\":").size - 1
        assertEquals("every entry is on its own line and read", declared, names.size)
        assertEquals("names are unique", names.size, names.toSet().size)
    }
}
