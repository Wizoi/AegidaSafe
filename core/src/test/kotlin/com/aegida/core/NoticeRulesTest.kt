package com.aegida.core

import com.aegida.core.model.InstalledApp
import com.aegida.core.model.NotificationEvent
import com.aegida.core.notice.NoticeRow
import com.aegida.core.notice.NoticeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Kevin, Oct 11 (finding #92): tell an app that spams ads as notices from a real one. Numbers are the first guesses, kept as written in the design. */
class NoticeRulesTest {
    private val now = Instant.parse("2026-10-11T16:00:00Z")
    private fun event(
        category: String? = null, channelName: String? = null, importance: Int = 3, ongoing: Boolean = false, conversation: Boolean = false,
        title: String = "Your files", text: String = "Open the app", pkg: String = "com.example.pdf", channelId: String? = "ch", negative: Boolean = false, summary: Boolean = false,
    ) = NotificationEvent(
        "0|$pkg|1|null|1", pkg, channelId, title, text, now,
        category = category, channelName = channelName, importance = importance, ongoing = ongoing, conversation = conversation, sentimentNegative = negative, groupSummary = summary,
    )
    private val tool = InstalledApp("com.example.pdf", "Image to PDF", "com.android.vending", false, category = 3) // CATEGORY_IMAGE

    // what counts

    @Test fun messagesCallsAndTheOtherRealKindsAreNeverCountedNorFlagged() {
        for (kind in listOf("msg", "call", "email", "alarm", "navigation", "progress", "service", "status", "transport", "reminder", "event", "social")) {
            assertFalse(kind, NoticeRules.counted(event(category = kind), listOf(tool), emptySet()))
        }
        assertFalse("a conversation", NoticeRules.counted(event(conversation = true), listOf(tool), emptySet()))
        assertFalse("an ongoing notice (a music player, a download)", NoticeRules.counted(event(ongoing = true), listOf(tool), emptySet()))
    }

    @Test fun systemAppsTheDefaultMessagesAndPhoneAppsAndAegidaAreNeverCounted() {
        val system = tool.copy(isSystem = true)
        assertFalse(NoticeRules.counted(event(), listOf(system), emptySet()))
        assertFalse("the default messages app", NoticeRules.counted(event(pkg = "org.sms"), emptyList(), setOf("org.sms")))
        assertFalse("a website channel is the pop-up check's", NoticeRules.counted(event(channelId = "web:http://x.example;1"), listOf(tool), emptySet()))
        assertFalse("Aegida's own notices", NoticeRules.counted(event(pkg = "com.aegida.app"), listOf(tool), emptySet()))
    }

    @Test fun anOrdinaryNoticeFromAnOrdinaryAppIsCounted() {
        assertTrue(NoticeRules.counted(event(), listOf(tool), emptySet()))
        assertTrue("an app the phone could not list is still counted", NoticeRules.counted(event(), emptyList(), emptySet()))
    }

    // the six signals

    @Test fun aGroupSummaryIsNotANotice() { // finding #93: the app's own summary and the system's autogroup one
        assertFalse(NoticeRules.counted(event(summary = true), listOf(tool), emptySet()))
    }

    /**
     * Finding #93: a sign that is the same on every notice an app sends (it pops up, its category, the phone's sentiment) is context,
     * not proof of an ad. Without a sign in the notice itself (promo category, an ad word in the channel name, pushy or scary words)
     * a notice scores nothing, so a bank, a delivery app or breaking news with a pop-up channel is never flagged.
     */
    @Test fun aNoticeScoresOnlyWithASignOfAnAdInItselfAndThenContextAdds() {
        assertEquals(0, NoticeRules.signals(event(), null))
        assertEquals("context alone is not an ad", 0, NoticeRules.signals(event(importance = 4, negative = true), 3))
        assertEquals(1, NoticeRules.signals(event(category = "promo"), null))
        assertEquals(1, NoticeRules.signals(event(category = "recommendation"), null))
        assertEquals(1, NoticeRules.signals(event(channelName = "Special offers"), null))
        assertEquals(1, NoticeRules.signals(event(title = "File converter is ready!", text = "Convert"), null))
        assertEquals(1, NoticeRules.signals(event(title = "Warning", text = "Your phone is infected"), null)) // a fake-virus word (finding #96)
        assertEquals(2, NoticeRules.signals(event(category = "promo", importance = 4), null))
        assertEquals(2, NoticeRules.signals(event(category = "promo"), 3))
        assertEquals(2, NoticeRules.signals(event(category = "promo", negative = true), null))
        assertEquals("the notice in the screenshot", 3, NoticeRules.signals(event(title = "File converter is ready!", text = "Convert", importance = 4), 3))
    }

    @Test fun realAppsWithPopUpChannelsScoreNothing() {
        assertEquals("a bank", 0, NoticeRules.signals(event(title = "Card purchase", text = "\$42.10 at Grocer. Free ATM withdrawals this month", channelName = "Transactions", importance = 4), 7))
        assertEquals("breaking news", 0, NoticeRules.signals(event(title = "Breaking news", text = "Storm warning tonight", channelName = "Breaking news", importance = 4), 5))
        assertEquals("a sports score", 0, NoticeRules.signals(event(title = "Final", text = "Home team win 3-1", channelName = "Scores", importance = 4), null))
        assertEquals("a bank's sign-in alert", 0, NoticeRules.signals(event(title = "Security alert", text = "New sign-in to your account", channelName = "Security", importance = 4), 7))
        assertEquals("a watch", 0, NoticeRules.signals(event(title = "Watch", text = "Your device is connected", importance = 4), null))
        assertEquals("a news story", 0, NoticeRules.signals(event(title = "Retailer hacked", text = "Card data stolen", channelName = "Top stories", importance = 4), 5))
        assertEquals("a pharmacy", 0, NoticeRules.signals(event(title = "Pharmacy", text = "Your prescription is ready!", importance = 4), null))
        assertEquals("a delivery", 0, NoticeRules.signals(event(title = "Your order", text = "Arriving today. Install the app update for tracking", channelName = "Orders", importance = 4), null))
    }

    // when to flag

    private fun rows(vararg ago: Pair<Long, Int>) = ago.map { (minutes, signals) -> NoticeRow(now.minus(Duration.ofMinutes(minutes)), signals) }

    /** Kevin, Oct 11: the pop-ups keep coming, several an hour, so the short window is an hour, not a day. */
    @Test fun threeNoticesInAnHourTwoOfThemLookingLikeAdsFlagsTheApp() {
        assertTrue(NoticeRules.flagged(rows(10L to 2, 25L to 3, 50L to 0), now))
    }

    @Test fun theSameThreeSpreadOverADayDoNotFlag() {
        assertFalse(NoticeRules.flagged(rows(10L to 2, 200L to 3, 400L to 0), now))
    }

    @Test fun manyPlainNoticesDoNotFlag() {
        assertFalse(NoticeRules.flagged(rows(10L to 0, 200L to 0, 400L to 1, 500L to 0, 600L to 0), now))
    }

    @Test fun twoNoticesAreNeverEnough() {
        assertFalse(NoticeRules.flagged(rows(10L to 3, 20L to 3), now))
    }

    @Test fun sixInAWeekMostlyWithASignalFlagsEvenWhenNoDayHadThree() {
        val week = rows(60L to 1, 60L * 30 to 1, 60L * 54 to 1, 60L * 80 to 1, 60L * 105 to 1, 60L * 130 to 0)
        assertTrue(NoticeRules.flagged(week, now))
        assertFalse("not mostly", NoticeRules.flagged(rows(60L to 1, 60L * 30 to 0, 60L * 54 to 0, 60L * 80 to 0, 60L * 105 to 0, 60L * 130 to 0), now))
    }

    @Test fun noticesStampedInTheFutureDoNotCount() { // finding #93: a clock set ahead, then corrected
        val ahead = listOf(NoticeRow(now.plus(Duration.ofHours(5)), 3), NoticeRow(now.plus(Duration.ofHours(6)), 3), NoticeRow(now.plus(Duration.ofHours(7)), 3))
        assertFalse(NoticeRules.flagged(ahead, now))
    }

    @Test fun noticesOlderThanAWeekDoNotCount() {
        assertFalse(NoticeRules.flagged(rows(60L * 24 * 8 to 3, 60L * 24 * 9 to 3, 60L * 24 * 10 to 3), now))
    }
}
