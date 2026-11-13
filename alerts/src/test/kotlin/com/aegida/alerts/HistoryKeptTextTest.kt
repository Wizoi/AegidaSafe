package com.aegida.alerts

import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Test

/** T1.3: the 90 days are typed in two places (the code that prunes and the words History shows); they must not drift. */
class HistoryKeptTextTest {
    @Test fun theHistoryPageSaysTheSameNumberOfDaysTheDailyCheckKeeps() {
        val days = Regex("""(\d+) days""").find(Texts.History.KEPT)!!.groupValues[1].toLong()
        assertEquals(DailyPruning.HISTORY_KEPT.toDays(), days)
    }
}
