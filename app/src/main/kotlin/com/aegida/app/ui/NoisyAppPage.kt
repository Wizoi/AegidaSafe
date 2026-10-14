package com.aegida.app.ui

import androidx.compose.runtime.Composable
import com.aegida.core.model.Finding
import com.aegida.core.text.Texts

/**
 * The page for an app that keeps sending ads as notices (finding #92): which app and an example, then four ways out. Aegida cannot
 * switch the notices off itself; the first two buttons open the phone's own pages for that app. "This app is fine" is the owner's
 * word and goes on the list in My settings (the guardian is told).
 */
@Composable
fun NoisyAppPage(
    pictureId: String?,
    finding: Finding,
    shown: ShownAt,
    actions: NoisyAppActions,
) {
    AegidaScreen(pictureId) {
        Headline(finding.key.problem.forMom)
        AdviceLines.ofNoisyApp(finding, shown.now, shown.zone, shown.locale).forEach { Body(it) }
        BigButton(Texts.Home.NOISY_TURN_OFF, actions.onTurnOff)
        QuietButton(Texts.Home.NOISY_REMOVE, actions.onRemove)
        QuietButton(Texts.Home.NOISY_FINE, actions.onFine)
        QuietButton(Texts.Home.GOT_IT, actions.onGotIt)
    }
}
