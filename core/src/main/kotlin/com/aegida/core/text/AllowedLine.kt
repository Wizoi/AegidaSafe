package com.aegida.core.text

/** One line of "Things you told Aegida are fine" in My settings. */
object AllowedLine {
    /**
     * An app by its cleaned name (it picks its own label, R17), a website by its name as History shows it (whole on her own phone,
     * plain text, never a link; findings #155).
     */
    fun of(problem: Problem, label: String, subject: String): String {
        val clean = Texts.Home.cleanLabel(label).ifEmpty { subject }
        return if (problem == Problem.LookalikeLink) Texts.Settings.ALLOWED_SITE.format(clean) else clean
    }
}
