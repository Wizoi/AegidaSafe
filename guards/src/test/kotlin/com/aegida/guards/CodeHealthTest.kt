package com.aegida.guards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Code-health guards (docs/code-health.md): file size and duplication. Rules, not call-site counts.

/** Lines in a file the way `wc -l` counts them. */
internal fun lineCount(text: String): Int = text.count { it == '\n' } + if (text.isNotEmpty() && !text.endsWith("\n")) 1 else 0

class FileSizeTest {
    companion object {
        const val MAIN_LIMIT = 350
        const val TEST_LIMIT = 450

        /**
         * Files already over the limit, with their line count today. A file may not grow past its number; when one
         * shrinks, lower its number (or delete the row once it is under the limit). Do not add rows: split the file.
         */
        val ALLOWED_OVER: Map<String, Int> = emptyMap()
    }

    private fun kotlinFiles(sourceSet: (File) -> Boolean): List<Pair<String, Int>> =
        (ShippedSources.modules + "guards" + "feedtool").flatMap { module ->
            File(ShippedSources.root, "$module/src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && sourceSet(it) }
                .map { it.relativeTo(ShippedSources.root).invariantSeparatorsPath to lineCount(it.readText()) }
                .toList()
        }

    internal fun problems(files: List<Pair<String, Int>>, limitFor: (String) -> Int, allowed: Map<String, Int>): List<String> {
        val found = files.toMap()
        val over = files.filter { (path, lines) ->
            val allowance = allowed[path]
            if (allowance == null) lines > limitFor(path) else lines > allowance
        }.map { (path, lines) ->
            val allowance = allowed[path]
            if (allowance == null) "$path has $lines lines, over ${limitFor(path)}: split it" else "$path grew to $lines lines (allowed $allowance): split it"
        }
        val stale = allowed.filter { (path, n) ->
            val lines = found[path]
            lines == null || lines <= limitFor(path) || lines < n
        }.map { (path, n) -> "$path: allow-list row ($n) is stale; lower it to the current size or delete it if under the limit" }
        return over + stale
    }

    private fun limitFor(path: String) = if (path.contains("/src/main/")) MAIN_LIMIT else TEST_LIMIT

    @Test fun mainFilesStayUnder350AndTestFilesUnder450LinesExceptTheListedOnes() {
        val files = kotlinFiles { true }
        assertTrue("no Kotlin files found under ${ShippedSources.root}", files.size > 50)
        val problems = problems(files, ::limitFor, ALLOWED_OVER)
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
    }

    // Self-tests: the guard must actually catch a new offender, a grown one and a stale row.
    @Test fun aNewOversizedFileIsCaught() {
        assertTrue(problems(listOf("core/src/main/kotlin/A.kt" to 351), ::limitFor, emptyMap()).isNotEmpty())
        assertTrue(problems(listOf("core/src/test/kotlin/A.kt" to 451), ::limitFor, emptyMap()).isNotEmpty())
        assertTrue(problems(listOf("core/src/test/kotlin/A.kt" to 450), ::limitFor, emptyMap()).isEmpty())
    }

    @Test fun aListedFileThatGrowsOrShrinksIsCaught() {
        val path = "core/src/test/kotlin/A.kt"
        assertTrue(problems(listOf(path to 501), ::limitFor, mapOf(path to 500)).isNotEmpty())
        assertTrue(problems(listOf(path to 480), ::limitFor, mapOf(path to 500)).isNotEmpty())
        assertTrue(problems(listOf(path to 400), ::limitFor, mapOf(path to 500)).isNotEmpty())
        assertTrue(problems(listOf(path to 500), ::limitFor, mapOf(path to 500)).isEmpty())
    }
}

/**
 * Copy-paste in main Kotlin. Same rules as scripts/duplication-report.py (run it to see the repeated blocks):
 * windows of [WINDOW] normalized lines that appear in two or more places. The count of lines inside such windows
 * may not grow past [ALLOWED_DUPLICATED_LINES]; when it falls, lower the number.
 */
class DuplicationTest {
    companion object {
        const val WINDOW = 8

        /** Today's number from `python scripts/duplication-report.py` (OkHttpHttpClient's two request paths). */
        const val ALLOWED_DUPLICATED_LINES = 0
    }

    private fun normalized(text: String): List<String> =
        text.lines().map { it.trim().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ") }
            .filter { it.length > 2 && !it.startsWith("import ") && !it.startsWith("package ") && listOf("//", "/*", "*").none(it::startsWith) }

    internal fun duplicatedLines(sources: Map<String, String>, window: Int = WINDOW): Int {
        val lines = sources.mapValues { normalized(it.value) }
        val seen = HashMap<List<String>, MutableList<Pair<String, Int>>>()
        lines.forEach { (name, list) ->
            for (i in 0..list.size - window) seen.getOrPut(list.subList(i, i + window)) { mutableListOf() }.add(name to i)
        }
        val covered = HashSet<Pair<String, Int>>()
        seen.values.filter { it.size > 1 }.forEach { places -> places.forEach { (name, i) -> for (k in i until i + window) covered.add(name to k) } }
        return covered.size
    }

    @Test fun duplicatedLinesInMainKotlinDoNotGrow() {
        val sources = ShippedSources.main.filter { it.path.endsWith(".kt") }.associate { it.path to it.text }
        assertTrue("no Kotlin sources found", sources.size > 50)
        val count = duplicatedLines(sources)
        assertTrue(
            "duplicated lines grew to $count (allowed $ALLOWED_DUPLICATED_LINES): run `python scripts/duplication-report.py` and reuse the existing code",
            count <= ALLOWED_DUPLICATED_LINES,
        )
        assertTrue("duplicated lines fell to $count: lower ALLOWED_DUPLICATED_LINES (and the number in docs/code-health.md)", count >= ALLOWED_DUPLICATED_LINES)
    }

    @Test fun aCopiedBlockIsCountedAndAnUnrepeatedOneIsNot() {
        val block = (1..8).joinToString("\n") { "val x$it = compute($it)" }
        assertEquals(16, duplicatedLines(mapOf("a.kt" to block, "b.kt" to "  " + block.replace("\n", "\n  "))))
        assertEquals(0, duplicatedLines(mapOf("a.kt" to block, "b.kt" to "val other = 1")))
        // Blank lines, imports and comments between copies do not hide the copy.
        assertEquals(16, duplicatedLines(mapOf("a.kt" to block, "b.kt" to "import a.b\n\n// note\n" + block)))
    }
}

class NoByteOrderMarkTest {
    /** A BOM from a Windows editor makes a table first line not a table to ScenarioTablesTest and shows in diffs (finding #125). */
    @Test fun noTextFileInTheRepoStartsWithAByteOrderMark() {
        val marked = ShippedSources.allText.filter { it.text.startsWith('\uFEFF') }.map { it.path }
        assertTrue("these files start with a UTF-8 byte-order mark; save them without one: $marked", marked.isEmpty())
    }
}
