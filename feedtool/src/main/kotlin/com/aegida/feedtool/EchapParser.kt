package com.aegida.feedtool

/** What the builder takes from Echap's list of spying apps. */
data class EchapIndicators(
    val families: Int,
    /** Android package names, lower case. */
    val packages: Set<String>,
    /** SHA-1 of signing certificates, lower-case hex. */
    val certificates: Set<String>,
)

/**
 * Reads the Echap stalkerware-indicators `ioc.yaml` (CC BY 4.0). The file is a plain YAML list of apps, each with
 * `packages:` and `certificates:` lists. It is read by hand rather than with a YAML library because only those two
 * lists are needed, and every line that does not fit is counted, so a change in Echap's layout shows up as a number
 * in the build output (and [EchapParser.parse] refuses a file that yields almost nothing).
 */
object EchapParser {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val CERTIFICATE = Regex("^[0-9A-Fa-f]{40}$")
    private const val MIN_PACKAGES = 100
    private const val MIN_CERTIFICATES = 100

    private val FIELD = Regex("^  ([a-z_0-9]+):.*$")

    /** What has been read so far, line by line. */
    private class Reading {
        var families = 0
        var skipped = 0
        val packages = linkedSetOf<String>()
        val certificates = linkedSetOf<String>()
        private var field: String? = null

        fun read(line: String) {
            when {
                line.startsWith("- name:") -> { families++; field = null }
                FIELD.matches(line) -> field = line.trim().substringBefore(':')
                line.startsWith("  - ") -> readValue(line.removePrefix("  - ").trim().trim('"', '\''))
            }
        }

        private fun readValue(value: String) {
            when (field) {
                "packages" -> add(value, PACKAGE, packages)
                "certificates" -> add(value, CERTIFICATE, certificates)
            }
        }

        private fun add(value: String, shape: Regex, into: MutableSet<String>) {
            if (shape.matches(value)) into += value.lowercase() else skipped++
        }
    }

    fun parse(text: String): EchapIndicators {
        val reading = Reading()
        text.lineSequence().forEach { reading.read(it.trimEnd()) }
        val families = reading.families
        val skipped = reading.skipped
        val packages = reading.packages
        val certificates = reading.certificates
        require(packages.size >= MIN_PACKAGES && certificates.size >= MIN_CERTIFICATES) {
            "Echap list looks wrong: ${packages.size} packages, ${certificates.size} certificates in $families entries ($skipped lines skipped)"
        }
        return EchapIndicators(families, packages, certificates)
    }
}
