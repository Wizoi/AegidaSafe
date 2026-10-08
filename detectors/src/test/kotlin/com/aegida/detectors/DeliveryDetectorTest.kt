package com.aegida.detectors

import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class DeliveryDetectorTest {
    private fun problems(heldHours: Long?) =
        DeliveryDetector().evaluate(inputs().copy(oldestUndelivered = heldHours?.let { now.minus(Duration.ofHours(it)) })).map { it.key.problem }

    @Test fun nothingWaitingMeansNoProblem() = assertEquals(emptyList<Problem>(), problems(null))
    @Test fun heldForLessThanADayIsNotYetAProblem() = assertEquals(emptyList<Problem>(), problems(23))
    @Test fun heldForMoreThanADayMeansTheGuardianCannotBeReached() = assertEquals(listOf(Problem.GuardianUnreachable), problems(25))
}
