package com.aegida.app

import com.aegida.app.ui.HistoryScreen
import com.aegida.core.alert.HistoryRow
import com.aegida.core.text.Texts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.time.Duration

/** History, in light and dark at 100% and 200% font. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class HistoryScreenTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    @Test fun historyListsEventsUnderADayHeadingAndSaysHowLongItIsKept() {
        val rows = listOf(
            HistoryRow(now.minus(Duration.ofMinutes(10)), "A website sent fake warnings", "You were warned.", handled = true, facts = "The website was news.example."),
            HistoryRow(now.minus(Duration.ofDays(1)), "Website protection was off", "You were warned. Fixed.", handled = true),
        )
        show { HistoryScreen(rows, picture, now, zone, locale) }
        reachable(Texts.History.TODAY)
        reachable("A website sent fake warnings")
        reachable("The website was news.example.") // what it was (Kevin, Oct 10)
        reachable(Texts.History.YESTERDAY)
        reachable(Texts.History.KEPT)
    }

    @Test fun emptyHistorySaysNothingHasHappened() {
        show { HistoryScreen(emptyList(), picture, now, zone, locale) }
        reachable(Texts.History.EMPTY)
    }
}
