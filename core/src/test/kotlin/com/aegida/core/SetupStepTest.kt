package com.aegida.core

import com.aegida.core.model.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupStepTest {
    @Test fun backGoesToTheStepBeforeAndSkipsTheSamsungStepOnAPhoneThatCannotHaveIt() { // findings #74
        assertEquals(null, SetupStep.About.previous(blockerRelevant = false))
        assertEquals(SetupStep.About, SetupStep.Picture.previous(false))
        assertEquals(SetupStep.Filter, SetupStep.Finish.previous(blockerRelevant = false))
        assertEquals(SetupStep.Blocker, SetupStep.Finish.previous(blockerRelevant = true))
        assertEquals(SetupStep.Access, SetupStep.Filter.previous(true))
    }

    @Test fun nextAndPreviousAreInverseOnEveryStep() {
        for (relevant in listOf(true, false)) {
            SetupStep.entries.forEach { step ->
                if (step == SetupStep.Blocker && !relevant) return@forEach // never shown on that phone
                val next = step.next(relevant) ?: return@forEach
                assertEquals("$step with blockerRelevant=$relevant", step, next.previous(relevant))
            }
        }
    }
}
