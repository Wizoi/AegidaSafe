package com.aegida.detectors

import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import org.junit.Assert.assertEquals
import org.junit.Test

/** R11: a scam text padded with a dotted run must still be evaluated, not abort the check. */
class LinkDetectorLongTextTest {
    private fun problems(text: String) =
        LinkDetector().evaluate(inputs(event = message(text).copy(packageName = "com.android.shell"))).map { it.key.problem to it.key.subject }

    @Test fun aListedLinkAfterAThousandDotsOfPaddingIsStillRaised() {
        var result: List<Pair<Problem, String>>? = null
        val thread = Thread(null, { result = problems("a.".repeat(1_500) + " Verify at https://login.evil.example/x") }, "small-stack", 256 * 1024)
        thread.start()
        thread.join()
        assertEquals(listOf(Problem.ScamLink to "login.evil.example"), result)
    }
}
