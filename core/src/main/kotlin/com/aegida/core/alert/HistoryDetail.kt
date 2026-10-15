package com.aegida.core.alert

import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.core.url.HostMask

/**
 * The line under a History entry that says what it was (Kevin, Oct 10): the app, who sent a message, the first words of it
 * with any link taken out, and a masked address; the website that was sending notifications; the app that changed.
 * Written once, when the entry is made, from the finding's evidence. Null when there is nothing more to say. On the phone the
 * address is whole (Kevin, Oct 11: mask only what is sent); links in the words are still taken out, and an app's own words never
 * show for the urgent kinds (R17).
 */
object HistoryDetail {
    /**
     * Separates the words from a hidden matching key (the app's package) on the apps' lines (findings #140). Two apps can show the same name
     * (or the same first 40 characters), so "the same app" cannot be told from the words alone. A control character: [Texts.Home.cleanLabel]
     * turns every control character into a space, so no label can ever contain it. Stored in the existing detail column (no migration);
     * [display] takes it off before anything is drawn, and [same] is the one way to compare two details.
     */
    private val NOT_ABOUT_AN_APP = setOf(
        Problem.ScamLink, Problem.LookalikeLink, Problem.SiteNotifications, Problem.SiteScaryNews, Problem.NewWebsiteNotifications,
    )
    private const val KEY = ''

    /** The words only, as Mom reads them: the hidden key is taken off. */
    fun display(detail: String?): String? = detail?.substringBefore(KEY)

    /**
     * True when two stored details are about the same thing. Both carry a key: the keys must match as well as the words (two apps both
     * called "Update" are two apps). A detail without a key (written before this version, or a kind with no app) matches on the words alone.
     */
    fun same(a: String?, b: String?): Boolean = when {
        a == null || b == null -> a == b
        KEY in a && KEY in b -> a == b
        else -> display(a) == display(b)
    }

    /** The detail for a finding: its words, and for an app's line the hidden key (the package from the evidence, else the finding's own subject). */
    fun of(finding: Finding): String? = of(finding.key.problem, finding.evidence, finding.key.subject)

    fun of(problem: Problem, evidence: Map<String, String>, subject: String? = null): String? {
        val words = wordsOf(problem, evidence) ?: return null
        val key = if (problem !in NOT_ABOUT_AN_APP) (evidence[Evidence.PACKAGE] ?: subject).orEmpty().filter { it >= ' ' }.trim() else ""
        return if (key.isEmpty()) words else words + KEY + key
    }

    private fun wordsOf(problem: Problem, evidence: Map<String, String>): String? {
        val parts: List<String?> = when (problem) {
            Problem.ScamLink, Problem.LookalikeLink -> listOf(
                Texts.Home.adviceFrom(evidence[Evidence.APP_LABEL]),
                Texts.Home.senderLine(evidence[Evidence.MESSAGE_TITLE]),
                Texts.Home.saysLine(evidence[Evidence.MESSAGE_TEXT]),
                // On the phone, whole; masked only when sent (Kevin, Oct 11).
                evidence[Evidence.HOST]?.takeIf { it.isNotBlank() }?.let { Texts.History.LINK_WENT.format(it) },
            )
            Problem.SiteNotifications, Problem.SiteScaryNews, Problem.NewWebsiteNotifications -> listOf(
                evidence[Evidence.SITE]?.let(HostMask::hostOf)?.takeIf { it.isNotEmpty() }?.let { Texts.History.WEBSITE_WAS.format(it) },
                Texts.Home.saidLine(evidence[Evidence.MESSAGE_TITLE], evidence[Evidence.MESSAGE_TEXT]),
            )
            Problem.NoisyApp -> listOf(
                Texts.Home.cleanLabel(evidence[Evidence.APP_LABEL].orEmpty()).takeIf { it.isNotEmpty() }?.let { label ->
                    evidence[Evidence.NOTICES]?.let { Texts.History.NOISY.format("“$label”", it) } ?: Texts.History.NOISY_NO_COUNT.format("“$label”")
                },
            )
            else -> listOf(Texts.Home.confirmApp(problem, evidence[Evidence.APP_LABEL]))
        }
        return parts.filterNotNull().joinToString(" ") { if (it.endsWith(".") || it.endsWith(".”")) it else "$it." }.ifEmpty { null }
    }
}
