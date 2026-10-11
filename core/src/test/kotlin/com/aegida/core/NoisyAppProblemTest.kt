package com.aegida.core

import com.aegida.core.alert.HistoryDetail
import com.aegida.core.model.Evidence
import com.aegida.core.model.Severity
import com.aegida.core.text.Fix
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** Finding #92: an app that keeps sending ads as notices is a warning with a button, never urgent, and not raised again for a week once seen. */
class NoisyAppProblemTest {
    @Test fun itNeedsATapIsNeverUrgentAndOpensTheAppsOwnNoticeSettings() {
        assertEquals(Severity.NeedsTap, Problem.NoisyApp.severity)
        assertEquals(Fix.AppNotices, Problem.NoisyApp.fix)
        assertEquals(false, Problem.NoisyApp.hidesNotification)
        assertEquals("An app keeps sending you ads as notices. Tap to see which one.", Problem.NoisyApp.forMom)
        assertEquals("An app sent ads as notices", Problem.NoisyApp.whatHappened)
    }

    @Test fun onceTheOwnerHasSeenItAppIsLeftAloneForAWeek() {
        assertEquals(Duration.ofDays(7), Problem.NoisyApp.quietAfterSeen)
        assertEquals("other warnings come back as before", null, Problem.ScamLink.quietAfterSeen)
    }

    @Test fun historySaysWhichAppAndHowManyNotices() {
        assertEquals(
            "The “Image to PDF” app sent 8 notices that look like ads.",
            HistoryDetail.of(Problem.NoisyApp, mapOf(Evidence.APP_LABEL to "Image to PDF", Evidence.NOTICES to "8")),
        )
        assertNotNull(Texts.History.NOISY)
        assertTrue(HistoryDetail.of(Problem.NoisyApp, emptyMap()) == null)
    }
}
