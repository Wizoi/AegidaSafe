package com.aegida.alerts

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Kevin, Oct 10: a website that only asked "Was that you?" is put aside, not deleted, so it can come back if it was fine. */
class CheckOutcomeTest {
    private val now = Instant.now()
    private fun site(problem: Problem, key: String) =
        Finding(FindingKey(DetectorId.PopupSpam, problem, "http://$key.example"), mapOf(Evidence.NOTIFICATION_KEY to key), now, now)

    private val outcome = CheckOutcome(
        listOf(site(Problem.SiteNotifications, "scary"), site(Problem.NewWebsiteNotifications, "new")),
        PipelineResult(alerted = 2, resolved = 0, guardiansNotReached = 0),
    )

    @Test fun aFakeWarningIsRemovedAndANewWebsiteIsSnoozed() {
        assertEquals(listOf("scary"), outcome.notificationsToHide)
        assertEquals(listOf("new"), outcome.notificationsToSnooze)
    }
}
