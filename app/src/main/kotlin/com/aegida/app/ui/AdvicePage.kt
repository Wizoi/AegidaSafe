package com.aegida.app.ui

import androidx.compose.runtime.Composable
import com.aegida.core.model.Finding
import com.aegida.core.model.mayBeAllowed
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts

/**
 * The page Show me opens for a scam or look-alike link: what the message was, what to do, and Got it. A look-alike is a guess that can
 * be wrong, so it also offers "This link is fine" (findings #155); a link on the scam list never does, nor a name that mixes
 * alphabets ([mayBeAllowed]).
 */
@Composable
fun AdvicePage(pictureId: String?, finding: Finding, shown: ShownAt, onGotIt: () -> Unit, onFine: () -> Unit) {
    AegidaScreen(pictureId) {
        Headline(finding.key.problem.forMom)
        AdviceLines.of(finding, shown.now, shown.zone, shown.locale).forEach { Body(it) }
        BigButton(Texts.Home.GOT_IT, onGotIt)
        if (finding.key.problem == Problem.LookalikeLink && finding.key.mayBeAllowed()) QuietButton(Texts.Home.LINK_FINE, onFine)
    }
}
