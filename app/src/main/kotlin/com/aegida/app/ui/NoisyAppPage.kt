package com.aegida.app.ui

import androidx.compose.runtime.Composable
import com.aegida.core.model.Finding
import com.aegida.core.text.Texts
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The page for an app that keeps sending ads as notices (finding #92): which app and an example, then four ways out. Aegida cannot
 * switch the notices off itself; the first two buttons open the phone's own pages for that app. "This app is fine" is the owner's
 * word and goes on the list in My settings (the guardian is told).
 */
@Composable
fun NoisyAppPage(
    pictureId: String?,
    finding: Finding,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
    onTurnOff: () -> Unit,
    onRemove: () -> Unit,
    onFine: () -> Unit,
    onGotIt: () -> Unit,
) {
    AegidaScreen(pictureId) {
        Headline(finding.key.problem.forMom)
        AdviceLines.ofNoisyApp(finding, now, zone, locale).forEach { Body(it) }
        BigButton(Texts.Home.NOISY_TURN_OFF, onTurnOff)
        QuietButton(Texts.Home.NOISY_REMOVE, onRemove)
        QuietButton(Texts.Home.NOISY_FINE, onFine)
        QuietButton(Texts.Home.GOT_IT, onGotIt)
    }
}
