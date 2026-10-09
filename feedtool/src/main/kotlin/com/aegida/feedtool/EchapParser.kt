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

    fun parse(text: String): EchapIndicators {
        var families = 0
        var field: String? = null
        val packages = linkedSetOf<String>()
        val certificates = linkedSetOf<String>()
        var skipped = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("- name:") -> { families++; field = null }
                Regex("^  ([a-z_0-9]+):.*$").matches(line) -> field = line.trim().substringBefore(':')
                line.startsWith("  - ") -> {
                    val value = line.removePrefix("  - ").trim().trim('"', '\'')
                    when (field) {
                        "packages" -> if (PACKAGE.matches(value)) packages += value.lowercase() else skipped++
                        "certificates" -> if (CERTIFICATE.matches(value)) certificates += value.lowercase() else skipped++
                    }
                }
            }
        }
        require(packages.size >= MIN_PACKAGES && certificates.size >= MIN_CERTIFICATES) {
            "Echap list looks wrong: ${packages.size} packages, ${certificates.size} certificates in $families entries ($skipped lines skipped)"
        }
        return EchapIndicators(families, packages, certificates)
    }
}
