package com.aegida.core.alert

import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.Severity
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.core.text.WhatItIs
import com.aegida.core.url.HostMask
import java.time.ZoneOffset
import java.util.Locale

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
    private val LINK_KINDS = setOf(Problem.ScamLink, Problem.LookalikeLink)
    private val SITE_KINDS = setOf(Problem.SiteNotifications, Problem.SiteScaryNews, Problem.NewWebsiteNotifications)
    private const val KEY = ''

    /** The words only, as Mom reads them: the hidden key is taken off. */
    fun display(detail: String?): String? = detail?.substringBefore(KEY)

    /**
     * True when two stored details are about the same thing. Both carry a key: the keys decide (two apps both called "Update" are two
     * apps; the same app is the same app though its words grew its facts in #164). Only one carries a key (a line written before this
     * version): the same words, or the key-less words name the key's website (a site line before #164 said "The website was host.").
     * Neither: the words alone.
     */
    fun same(a: String?, b: String?): Boolean = when {
        a == null || b == null -> a == b
        KEY in a && KEY in b -> a.substringAfter(KEY) == b.substringAfter(KEY)
        KEY in a -> display(a) == b || namesSite(b, a.substringAfter(KEY))
        KEY in b -> display(b) == a || namesSite(a, b.substringAfter(KEY))
        else -> a == b
    }

    /** After the host: a space, the end, or a final "." then a space or the end (findings #168: "paypal.com.secure-login.net" is not paypal.com). */
    private fun namesSite(words: String, key: String): Boolean = Regex("""(^| )${Regex.escape(key)}( |$|\.( |$))""").containsMatchIn(words)

    /**
     * True when a line written before #164 had no words for this finding, so a told-line with none may be about it: an urgent app's
     * name was hidden then, and an app with no name had no line. Every app line has words and a key now. A line that should have had
     * words but has none (a note queued before Room version 4) still proves nothing (findings #116).
     */
    fun hadNoWordsBefore164(finding: Finding): Boolean {
        val problem = finding.key.problem
        if (problem in LINK_KINDS || problem in SITE_KINDS || of(finding) == null) return false
        return problem.severity == Severity.Urgent || Texts.Home.cleanLabel(finding.evidence[Evidence.APP_LABEL].orEmpty()).isEmpty()
    }

    /** The detail for a finding: its words, and for an app's line the hidden key (the package from the evidence, else the finding's own subject). */
    fun of(finding: Finding): String? = of(finding.key.problem, finding.evidence, finding.key.subject)

    fun of(problem: Problem, evidence: Map<String, String>, subject: String? = null): String? {
        val words = wordsOf(problem, evidence) ?: return null
        val key = when (problem) {
            in LINK_KINDS -> ""
            in SITE_KINDS -> evidence[Evidence.SITE]?.let(HostMask::hostOf).orEmpty()
            else -> (evidence[Evidence.PACKAGE] ?: subject).orEmpty()
        }.filter { it >= ' ' }.trim()
        return if (key.isEmpty()) words else words + KEY + key
    }

    private fun wordsOf(problem: Problem, evidence: Map<String, String>): String? {
        // No day the app was added (now = null): the line is written once and compared later, so it must not depend on the clock.
        val what = Texts.Home.whatItIs(problem, evidence, null, ZoneOffset.UTC, Locale.ROOT)
        val parts: List<String?> = when (problem) {
            in LINK_KINDS -> what + listOf(
                Texts.Home.senderLine(evidence[Evidence.MESSAGE_TITLE])?.takeIf { it !in what },
                Texts.Home.saysLine(evidence[Evidence.MESSAGE_TEXT]),
                // On the phone, whole; masked only when sent (Kevin, Oct 11).
                evidence[Evidence.HOST]?.takeIf { it.isNotBlank() }?.let { Texts.History.LINK_WENT.format(it) },
            )
            in SITE_KINDS -> what + Texts.Home.saidLine(evidence[Evidence.MESSAGE_TITLE], evidence[Evidence.MESSAGE_TEXT])
            Problem.NoisyApp -> listOf(
                WhatItIs.appWords(evidence[Evidence.APP_LABEL])?.let { label ->
                    evidence[Evidence.NOTICES]?.let { Texts.History.NOISY.format("“$label”", it) } ?: Texts.History.NOISY_NO_COUNT.format("“$label”")
                },
            ) + what.drop(1)
            else -> what
        }
        return parts.filterNotNull().joinToString(" ") { if (it.endsWith(".") || it.endsWith(".”")) it else "$it." }.ifEmpty { null }
    }
}
