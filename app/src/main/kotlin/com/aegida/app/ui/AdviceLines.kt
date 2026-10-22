package com.aegida.app.ui

import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.text.Texts
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What set a finding off, as Home and the "Was that you?" page draw it: the one wording rule, [Texts.Home.whatItIs] (#164). */
object WhatItIsLines {
    fun of(finding: Finding, shown: ShownAt): List<String> =
        Texts.Home.whatItIs(finding.key.problem, finding.evidence, shown.now, shown.zone, shown.locale)
}

/**
 * What the advice page for a scam message says, top to bottom: the app (or its sender), when it came in, who it was from and what
 * it said, then what to do. Each line is left out when the phone does not know it (an older finding, or an app with no name).
 * Only draws what the finding holds; the words of the message come from the finding's evidence, kept on the phone only.
 */
object AdviceLines {
    fun of(finding: Finding, now: Instant, zone: ZoneId, locale: Locale): List<String> {
        val e = finding.evidence
        val what = WhatItIsLines.of(finding, ShownAt(now, zone, locale))
        return what + listOfNotNull(
            e[Evidence.MESSAGE_AT]?.toLongOrNull()?.let { TimeText.cameInLine(Instant.ofEpochMilli(it), now, zone, locale) },
            Texts.Home.senderLine(e[Evidence.MESSAGE_TITLE])?.takeIf { it !in what },
            Texts.Home.saysLine(e[Evidence.MESSAGE_TEXT]),
            Texts.Home.SCAM_ADVICE,
        )
    }

    /** The same for an app that keeps sending ads as notices: which app and its facts, when the last one came, one example, how many, and what to do. */
    fun ofNoisyApp(finding: Finding, now: Instant, zone: ZoneId, locale: Locale): List<String> {
        val e = finding.evidence
        return WhatItIsLines.of(finding, ShownAt(now, zone, locale)) + listOfNotNull(
            e[Evidence.MESSAGE_AT]?.toLongOrNull()?.let { TimeText.cameInLine(Instant.ofEpochMilli(it), now, zone, locale) },
            Texts.Home.saidLine(e[Evidence.MESSAGE_TITLE]),
            e[Evidence.NOTICES]?.let { Texts.Home.NOISY_COUNT.format(it) },
            Texts.Home.NOISY_HOW,
        )
    }
}
