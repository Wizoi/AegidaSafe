package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.Severity
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EvaluatorTest {
    private val now = Instant.now()
    private val snapshot = Snapshot(now.minus(Duration.ofMinutes(1)), Reading.NotPresent, Reading.NotPresent)

    private class OneFinding(override val id: DetectorId) : Detector {
        override fun evaluate(input: Inputs) = listOf(
            Finding(FindingKey(id, "kind", "subject"), Severity.NeedsTap, "evidence", input.now, input.now),
        )
    }

    @Test fun runningTwiceGivesTheSameFindings() = runTest {
        val evaluator = Evaluator(setOf(OneFinding(DetectorId.Dns), OneFinding(DetectorId.Health))) { now }
        assertEquals(evaluator.run(snapshot), evaluator.run(snapshot))
    }

    @Test fun twoTriggersAtOnceBothGetTheFullResult() = runTest {
        val evaluator = Evaluator(setOf(OneFinding(DetectorId.Dns))) { now }
        val results = listOf(async { evaluator.run(snapshot) }, async { evaluator.run(snapshot) }).awaitAll()
        assertEquals(1, results[0].size)
        assertEquals(results[0], results[1])
    }
}
