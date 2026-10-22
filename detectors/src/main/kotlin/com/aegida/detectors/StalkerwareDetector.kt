package com.aegida.detectors

import com.aegida.core.feed.AppMatch
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * "Is any app on this phone a known spying app?" Checked against the Echap list in the signed feed, by package name
 * or by the certificate the app is signed with. Never raised for a system app. An app installed from Google Play
 * needs a certificate match as well as the package name. Raised again every check until it is gone: unlike the
 * other app questions there is no "that was me". Nothing is raised, and nothing guessed, before the first feed
 * download.
 */
class StalkerwareDetector @Inject constructor() : Detector {
    override val id = DetectorId.Stalkerware

    override fun evaluate(input: Inputs): List<Finding> {
        val feed = input.feed ?: return emptyList()
        return input.snapshot.apps
            .filter { !it.isSystem } // a bad or over-broad feed entry must never tell her to remove a system app
            .filter {
                when (feed.matchApp(it.packageName, it.certSha1s)) {
                    null -> false
                    AppMatch.Certificate -> true
                    // Spying apps arrive by hand; one from Google Play must match by certificate too (R18).
                    AppMatch.Package -> !it.fromPlayStore || feed.matchesCertificate(it.certSha1s)
                }
            }
            .sortedBy { it.packageName }
            .map {
                Finding(
                    FindingKey(id, Problem.StalkerwareApp, it.packageName),
                    mapOf(Evidence.APP_LABEL to it.label) + it.facts(input.snapshot.makerStores),
                    input.now, input.now,
                )
            }
    }
}
