package com.aegida.detectors

import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.model.InstalledApp
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.now
import com.aegida.detectors.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class StalkerwareDetectorTest {
    private val feed = ThreatFeed.of(
        now, listOf(FeedSource("Echap", "CC BY 4.0", 3)), emptyList(), emptyList(),
        packageNames = listOf("com.thetruth"), certificateSha1s = listOf("31a6ececd97cf39bc4126b8745cd94a7c30bf81c"),
    )

    private fun app(pkg: String, certs: Set<String> = emptySet(), system: Boolean = false, installer: String? = null) =
        InstalledApp(pkg, pkg.substringAfterLast('.'), installer, system, certs)

    private val listedCert = "31a6ececd97cf39bc4126b8745cd94a7c30bf81c"

    private fun found(vararg apps: InstalledApp, withFeed: ThreatFeed? = feed) =
        StalkerwareDetector().evaluate(inputs(snapshot = snapshot().copy(apps = apps.toList()), feed = withFeed)).map { it.key.problem to it.key.subject }

    @Test fun aListedPackageIsRaisedWhenItWasSideloadedOrHasNoRecordedInstaller() =
        assertEquals(
            listOf(Problem.StalkerwareApp to "com.thetruth"),
            found(app("com.android.chrome"), app("com.thetruth", installer = "com.android.packageinstaller")),
        )

    @Test fun aListedPackageNameAloneIsNotEnoughForAnAppInstalledFromPlay() =
        assertEquals(emptyList<Pair<Problem, String>>(), found(app("com.thetruth", certs = setOf("abcd"), installer = "com.android.vending")))

    @Test fun anAppFromPlayIsRaisedWhenItsCertificateIsListedToo() =
        assertEquals(
            listOf(Problem.StalkerwareApp to "com.thetruth"),
            found(app("com.thetruth", certs = setOf(listedCert), installer = "com.android.vending")),
        )

    @Test fun aSystemAppIsNeverRaisedEvenIfListed() =
        assertEquals(
            emptyList<Pair<Problem, String>>(),
            found(app("com.thetruth", system = true), app("org.renamed.app", certs = setOf(listedCert), system = true)),
        )

    @Test fun anAppSignedWithAListedCertificateIsRaisedEvenUnderAnotherName() =
        assertEquals(listOf(Problem.StalkerwareApp to "org.renamed.app"), found(app("org.renamed.app", certs = setOf("0000", "31A6ECECD97CF39BC4126B8745CD94A7C30BF81C"))))

    @Test fun otherAppsAreLeftAlone() =
        assertEquals(emptyList<Pair<Problem, String>>(), found(app("com.android.chrome", certs = setOf("abcd")), app("org.family.photos")))

    @Test fun withNoFeedYetNothingIsRaisedAndNothingIsGuessed() =
        assertEquals(emptyList<Pair<Problem, String>>(), found(app("com.thetruth"), withFeed = null))

    @Test fun evidenceNamesTheAppSoSheCanFindIt() {
        val finding = StalkerwareDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(app("com.thetruth"))), feed = feed)).single()
        assertEquals("thetruth", finding.evidence[com.aegida.core.model.Evidence.APP_LABEL])
    }
}
