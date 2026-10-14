package com.aegida.guards

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The scenario tables in the markdown files in docs/scenarios name the test (and the mutation check) that covers each row. A row that names
 * something that does not exist fails here, so a table cannot drift away from the tests. `GAP` rows are allowed: they say
 * plainly that nothing covers the situation yet.
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

    /** The cells of one table row under the column called [column], or empty for a line that is not a row of that table. */
    private fun cells(lines: List<String>, column: String): List<Pair<String, String>> {
        var index = -1
        val found = mutableListOf<Pair<String, String>>()
        for (line in lines) {
            if (!line.startsWith("|")) { index = -1; continue }
            val parts = line.trim().trim('|').split('|').map { it.trim() }
            if (index < 0) {
                index = parts.indexOf(column)
                continue
            }
            if (parts.all { it.matches(Regex("-+")) }) continue
            if (index < parts.size) found += parts[0] to parts[index]
        }
        return found
    }

    private fun methodExists(cls: String, method: String): Boolean = testFiles.any { file ->
        val text = file.readText()
        (file.nameWithoutExtension == cls || Regex("""\bclass\s+$cls\b""").containsMatchIn(text)) &&
            (Regex("""\bfun\s+$method\s*\(""").containsMatchIn(text) || text.contains("`$method`"))
    }

    @Test fun thereIsOneTableForEachOfTheHighRiskAreas() {
        val names = tables.map { it.name }.toSet()
        for (needed in listOf("scam-texts.md", "website-popups.md", "ads-as-notices.md", "guardian-notes.md", "phone-changes.md")) {
            assertTrue("docs/scenarios/$needed is missing", needed in names)
        }
    }

    @Test fun everyTestNamedInAScenarioRowExists() {
        val missing = mutableListOf<String>()
        var named = 0
        for (table in tables) {
            for ((id, cell) in cells(table.readLines(), "Test")) {
                for (token in cell.split(';').map { it.trim() }.filter { it.isNotEmpty() && it != "GAP" }) {
                    val match = Regex("""^([A-Z]\w*)[#.](\w+)$""").find(token)
                    if (match == null) {
                        missing += "${table.name} $id: '$token' is not Class#method or GAP"
                        continue
                    }
                    named++
                    if (!methodExists(match.groupValues[1], match.groupValues[2])) missing += "${table.name} $id: no test $token"
                }
            }
        }
        assertTrue("no test is named in any row, so the check is not reading the tables", named > 0)
        assertTrue("rows name tests that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun everyMutationCheckNamedInAScenarioRowExists() {
        val missing = mutableListOf<String>()
        for (table in tables) {
            for ((id, cell) in cells(table.readLines(), "Mutant")) {
                for (token in cell.split(';').map { it.trim() }.filter { it.isNotEmpty() && it != "GAP" }) {
                    if (token !in mutantNames) missing += "${table.name} $id: no mutation check '$token' in scripts/mutants.json"
                }
            }
        }
        assertTrue("rows name mutation checks that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun everyEmulatorScriptNamedInAScenarioRowExists() {
        val missing = mutableListOf<String>()
        for (table in tables) {
            for ((id, cell) in cells(table.readLines(), "Emulator")) {
                for (script in Regex("""emulator[_-][a-z_-]+\.(?:py|sh)""").findAll(cell).map { it.value }) {
                    if (!File(root, "scripts/$script").isFile) missing += "${table.name} $id: no scripts/$script"
                }
            }
        }
        assertTrue("rows name emulator scripts that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
