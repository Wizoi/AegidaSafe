package com.aegida.guards

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The scenario tables in the markdown files in docs/scenarios name the test (and the mutation check) that covers each row. A row that names
 * something that does not exist fails here, so a table cannot drift away from the tests. `GAP` rows are allowed: they say
 * plainly that nothing covers the situation yet. So are the markers in [NOT_A_NAME], each with its reason in brackets: a row
 * waiting for a Samsung or a real device, a risk Kevin accepted, or (Mutant column) a row where no one-line break makes sense.
 */
class ScenarioTablesTest {
    private val root = ShippedSources.root
    private val tables: List<File> = File(root, "docs/scenarios").listFiles { f -> f.extension == "md" }?.sortedBy { it.name }.orEmpty()

    private val testFiles: List<File> by lazy {
        root.listFiles { f -> f.isDirectory }.orEmpty()
            .map { File(it, "src/test") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }

    private val mutantNames: Set<String> by lazy {
        Regex(""""name"\s*:\s*"([^"]+)"""").findAll(File(root, "scripts/mutants.json").readText()).map { it.groupValues[1] }.toSet()
    }

    /** One cell of a scenario table: the file it is in, the row's id (first column) and the cell's text. */
    private class Cell(val table: String, val id: String, val text: String)

    /** One name written in a cell (cells list several, separated by ";"); "GAP" and the [NOT_A_NAME] markers are not names. */
    private class Name(val table: String, val id: String, val token: String)

    /** The cells of one table row under the column called [column], or empty for a line that is not a row of that table. */
    private fun cells(lines: List<String>, column: String): List<Pair<String, String>> {
        var index = -1
        val found = mutableListOf<Pair<String, String>>()
        for (line in lines) {
            if (!line.startsWith("|")) {
                index = -1
            } else {
                val parts = line.trim().trim('|').split('|').map { it.trim() }
                if (index < 0) {
                    index = parts.indexOf(column)
                } else if (!parts.all { it.matches(Regex("-+")) } && index < parts.size) {
                    found += parts[0] to parts[index]
                }
            }
        }
        return found
    }

    private fun cellsUnder(column: String): List<Cell> =
        tables.flatMap { table -> cells(table.readLines(), column).map { (id, text) -> Cell(table.name, id, text) } }

    private fun namesUnder(column: String): List<Name> = cellsUnder(column).flatMap { cell ->
        cell.text.split(';').map { it.trim() }.filter { it.isNotEmpty() && !NOT_A_NAME.matches(it) }.map { Name(cell.table, cell.id, it) }
    }

    private companion object {
        /** Not a test or a mutation check, and allowed in their place; every marker but GAP says why in brackets. */
        val NOT_A_NAME = Regex("""GAP|(needs a Samsung|needs a real device|accepted|none fits) \(.+\)""")
    }

    private fun methodExists(cls: String, method: String): Boolean = testFiles.any { file ->
        val text = file.readText()
        (file.nameWithoutExtension == cls || Regex("""\bclass\s+$cls\b""").containsMatchIn(text)) &&
            (Regex("""\bfun\s+$method\s*\(""").containsMatchIn(text) || text.contains("`$method`"))
    }

    private val classAndMethod = Regex("""^([A-Z]\w*)[#.](\w+)$""")

    /** What is wrong with a test named in a row, or null when it exists. */
    private fun testProblem(name: Name): String? {
        val match = classAndMethod.find(name.token)
        return when {
            match == null -> "${name.table} ${name.id}: '${name.token}' is not Class#method or GAP"
            !methodExists(match.groupValues[1], match.groupValues[2]) -> "${name.table} ${name.id}: no test ${name.token}"
            else -> null
        }
    }

    @Test fun thereIsOneTableForEachOfTheHighRiskAreas() {
        val names = tables.map { it.name }.toSet()
        for (needed in listOf("scam-texts.md", "website-popups.md", "ads-as-notices.md", "guardian-notes.md", "phone-changes.md")) {
            assertTrue("docs/scenarios/$needed is missing", needed in names)
        }
    }

    @Test fun everyTestNamedInAScenarioRowExists() {
        val names = namesUnder("Test")
        val missing = names.mapNotNull { testProblem(it) }
        assertTrue("no test is named in any row, so the check is not reading the tables", names.any { classAndMethod.containsMatchIn(it.token) })
        assertTrue("rows name tests that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun everyMutationCheckNamedInAScenarioRowExists() {
        val missing = namesUnder("Mutant").filter { it.token !in mutantNames }
            .map { "${it.table} ${it.id}: no mutation check '${it.token}' in scripts/mutants.json" }
        assertTrue("rows name mutation checks that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun everyEmulatorScriptNamedInAScenarioRowExists() {
        val script = Regex("""emulator[_-][a-z_-]+\.(?:py|sh)""")
        val missing = cellsUnder("Emulator").flatMap { cell -> script.findAll(cell.text).map { cell to it.value } }
            .filter { (_, name) -> !File(root, "scripts/$name").isFile }
            .map { (cell, name) -> "${cell.table} ${cell.id}: no scripts/$name" }
        assertTrue("rows name emulator scripts that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
