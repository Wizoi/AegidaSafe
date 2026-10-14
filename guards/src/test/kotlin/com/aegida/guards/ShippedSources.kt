package com.aegida.guards

import java.io.File

/** One source file, with its path relative to the repo root using forward slashes. */
class SourceFile(val path: String, val text: String) {
    val module: String get() = path.substringBefore('/')

    /** The text with comments removed, so a KDoc that names a rule does not break it. */
    val code: String by lazy { stripComments(text) }
}

/** Everything that ships: main source sets of every module, plus build files. Like TagPup's shipped_sources.py. */
object ShippedSources {
    val root: File = File(
        System.getProperty("aegida.root") ?: error("aegida.root not set; run through Gradle"),
    )

    val modules = listOf("core", "detectors", "platform", "store", "alerts", "app")

    val main: List<SourceFile> by lazy {
        modules.flatMap { module ->
            File(root, "$module/src/main").walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
                .map { read(it) }
                .toList()
        }
    }

    val buildFiles: List<SourceFile> by lazy {
        (modules + "guards" + "feedtool").map { File(root, "$it/build.gradle.kts") }.filter { it.isFile }.map { read(it) } +
            listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties").map { File(root, it) }.filter { it.isFile }.map { read(it) }
    }

    /**
     * Every tracked text file in the repo (for the secrets scan): what `git ls-files` lists, plus files not yet added. Reading the
     * whole folder took 30 s and scanned release mappings and other agents' working copies (docs/code-health.md). Without git it
     * falls back to walking the folder, skipping build output and VCS/IDE folders.
     */
    val allText: List<SourceFile> by lazy {
        val listed = gitFiles() ?: root.walkTopDown()
            .onEnter { it.name !in setOf(".git", ".gradle", ".idea", ".kotlin", "build", ".claude", "release-mappings") }
            .filter { it.isFile }
            .toList()
        // Everything under a module's src ships or builds the app, ignored by git or not: a key dropped into assets is caught (findings #105).
        val sources = root.listFiles().orEmpty().filter { it.isDirectory && File(it, "src").isDirectory }
            .flatMap { module -> File(module, "src").walkTopDown().onEnter { it.name != "build" }.filter { it.isFile }.toList() }
        (listed + sources).distinctBy { it.canonicalPath }.filter { it.isFile && it.extension in TEXT_EXTENSIONS }.map { read(it) }
    }

    /** Tracked and untracked-but-not-ignored files, or null when git cannot be run. NUL-separated, so odd names are not quoted. */
    private fun gitFiles(): List<File>? = try {
        val process = ProcessBuilder("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard")
            .directory(root).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val names = process.inputStream.bufferedReader().readText().split('\u0000').filter { it.isNotEmpty() }
        if (process.waitFor() == 0 && names.isNotEmpty()) names.map { File(root, it) } else null
    } catch (e: java.io.IOException) {
        null
    }

    private val TEXT_EXTENSIONS = setOf("kt", "kts", "java", "xml", "json", "properties", "pro", "toml", "md", "sh", "py", "html", "yml", "yaml", "txt", "svg")

    private fun read(file: File) = SourceFile(file.relativeTo(root).invariantSeparatorsPath, file.readText())
}

private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val LINE_COMMENT = Regex("""(?<![:"])//[^\n]*""")
private val XML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

fun stripComments(text: String): String =
    text.replace(BLOCK_COMMENT, " ").replace(XML_COMMENT, " ").replace(LINE_COMMENT, "")
