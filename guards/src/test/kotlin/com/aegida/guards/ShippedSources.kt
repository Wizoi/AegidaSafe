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
        (modules + "guards").map { File(root, "$it/build.gradle.kts") }.filter { it.isFile }.map { read(it) } +
            listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties").map { File(root, it) }.filter { it.isFile }.map { read(it) }
    }

    /** Every text file in the repo except build output and VCS/IDE folders (for the secrets scan). */
    val allText: List<SourceFile> by lazy {
        val skipDirs = setOf(".git", ".gradle", ".idea", ".kotlin", "build")
        root.walkTopDown()
            .onEnter { it.name !in skipDirs }
            .filter { it.isFile && it.extension in TEXT_EXTENSIONS }
            .map { read(it) }
            .toList()
    }

    private val TEXT_EXTENSIONS = setOf("kt", "kts", "java", "xml", "json", "properties", "pro", "toml", "md", "sh", "py", "html", "yml", "yaml", "txt", "svg")

    private fun read(file: File) = SourceFile(file.relativeTo(root).invariantSeparatorsPath, file.readText())
}

private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val LINE_COMMENT = Regex("""(?<![:"])//[^\n]*""")
private val XML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

fun stripComments(text: String): String =
    text.replace(BLOCK_COMMENT, " ").replace(XML_COMMENT, " ").replace(LINE_COMMENT, "")
