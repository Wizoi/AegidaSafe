package com.aegida.detectors

import com.aegida.core.model.Evidence
import com.aegida.core.model.InstalledApp
import com.aegida.core.notice.NoticeRow
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import com.aegida.detectors.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** Finding #92: one question, "is an app flooding the owner with ads as notices?", from the counted notices of the app that just posted. */
class NoisyAppDetectorTest {
    private val detector = NoisyAppDetector()
    private val pdf = InstalledApp("com.android.shell", "Image to PDF", "com.android.vending", false, category = 3)
    private val phone = snapshot().copy(apps = listOf(pdf))
    private val event = message("File converter is ready! Convert", title = "Image to PDF")
    private fun rows(vararg signals: Int) = signals.mapIndexed { i, s -> NoticeRow(Fixtures.now.minus(Duration.ofMinutes(10L * (i + 1))), s) }

    @Test fun anAppWithEnoughAdLikeNoticesIsFlaggedAndNamed() {
        val finding = detector.evaluate(inputs(snapshot = phone, event = event).copy(notices = rows(3, 2, 0))).single()
        assertEquals(Problem.NoisyApp, finding.key.problem)
        assertEquals("com.android.shell", finding.key.subject)
        assertEquals("Image to PDF", finding.evidence[Evidence.APP_LABEL])
        assertEquals("3", finding.evidence[Evidence.NOTICES])
        assertEquals("Image to PDF", finding.evidence[Evidence.MESSAGE_TITLE]) // one example, cleared when seen
    }

    @Test fun anAppWithFewOrPlainNoticesIsLeftAlone() {
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(snapshot = phone, event = event).copy(notices = rows(3, 3))))
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(snapshot = phone, event = event).copy(notices = rows(0, 0, 1, 0, 0))))
    }

    @Test fun nothingWithoutANotificationAndNothingForARealKindOfNotice() {
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(snapshot = phone, event = null).copy(notices = rows(3, 3, 3))))
        val chat = event.copy(category = "msg")
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(snapshot = phone, event = chat).copy(notices = rows(3, 3, 3))))
    }

    @Test fun anAppTheOwnerSaidIsFineIsNeverFlagged() {
        val finding = detector.evaluate(inputs(snapshot = phone, event = event).copy(notices = rows(3, 3, 3), allowedApps = setOf("com.android.shell")))
        assertEquals(emptyList<Any>(), finding)
    }
}
