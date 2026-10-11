package com.aegida.core.alert

import com.aegida.core.model.Evidence
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.core.url.HostMask

/**
 * The line under a History entry that says what it was (Kevin, Oct 10): the app, who sent a message, the first words of it
 * with any link taken out, and a masked address; the website that was sending notifications; the app that changed.
 * Written once, when the entry is made, from the finding's evidence. Null when there is nothing more to say. A scam
 * address never shows in full, and an app's own words never show for the urgent kinds (R17).
 */
object HistoryDetail {
    fun of(problem: Problem, evidence: Map<String, String>): String? {
        val parts: List<String?> = when (problem) {
            Problem.ScamLink, Problem.LookalikeLink -> listOf(
                Texts.Home.adviceFrom(evidence[Evidence.APP_LABEL]),
                Texts.Home.senderLine(evidence[Evidence.MESSAGE_TITLE]),
                Texts.Home.saysLine(evidence[Evidence.MESSAGE_TEXT], evidence[Evidence.HOST]),
                evidence[Evidence.HOST]?.takeIf { it.isNotBlank() }?.let { Texts.History.LINK_WENT.format(HostMask.of(it)) },
            )
            Problem.SiteNotifications, Problem.NewWebsiteNotifications -> listOf(
                evidence[Evidence.SITE]?.let(HostMask::hostOf)?.takeIf { it.isNotEmpty() }?.let { Texts.History.WEBSITE_WAS.format(it) },
                Texts.Home.saidLine(evidence[Evidence.MESSAGE_TITLE]),
            )
            Problem.NoisyApp -> listOf(
                Texts.Home.cleanLabel(evidence[Evidence.APP_LABEL].orEmpty()).takeIf { it.isNotEmpty() }?.let { label ->
                    Texts.History.NOISY.format("“$label”", evidence[Evidence.NOTICES] ?: "several")
                },
            )
            else -> listOf(Texts.Home.confirmApp(problem, evidence[Evidence.APP_LABEL]))
        }
        return parts.filterNotNull().joinToString(" ") { if (it.endsWith(".") || it.endsWith(".”")) it else "$it." }.ifEmpty { null }
    }
}
