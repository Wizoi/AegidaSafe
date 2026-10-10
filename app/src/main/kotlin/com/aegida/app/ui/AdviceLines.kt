package com.aegida.app.ui

import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.text.Texts
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * What the advice page for a scam message says, top to bottom: the app, when it came in, who it was from and what it said,
 * then what to do. Each line is left out when the phone does not know it (an older finding, or an app with no name).
 * Only draws what the finding holds; the words of the message come from the finding's evidence, kept on the phone only.
 */
object AdviceLines {
    fun of(finding: Finding, now: Instant, zone: ZoneId, locale: Locale): List<String> {
        val e = finding.evidence
        return listOfNotNull(
            Texts.Home.adviceFrom(e[Evidence.APP_LABEL]),
            e[Evidence.MESSAGE_AT]?.toLongOrNull()?.let { TimeText.cameInLine(Instant.ofEpochMilli(it), now, zone, locale) },
            Texts.Home.senderLine(e[Evidence.MESSAGE_TITLE]),
            Texts.Home.saysLine(e[Evidence.MESSAGE_TEXT], e[Evidence.HOST]),
            Texts.Home.SCAM_ADVICE,
        )
    }
}
