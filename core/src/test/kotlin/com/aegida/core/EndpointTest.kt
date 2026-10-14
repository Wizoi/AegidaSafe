package com.aegida.core

import com.aegida.core.net.Endpoint
import com.aegida.core.net.NtfyTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointTest {
    private val topic = "aegida_" + "a1B2c3D4".repeat(4)

    @Test fun ntfyUrlIsTheTopicOnNtfySh() {
        assertEquals("https://ntfy.sh/$topic", Endpoint.Ntfy(NtfyTopic(topic)).url)
    }

    @Test fun topicRejectsAnythingThatIsNotAShortUrlSafeName() {
        assertThrows(IllegalArgumentException::class.java) { NtfyTopic("short") }
        assertThrows(IllegalArgumentException::class.java) { NtfyTopic("evil.example/" + "a".repeat(40)) }
    }

    @Test fun newPrivateNamesNeverHoldLettersThatLookAlike() {
        val random = java.util.Random(7)
        val lookAlikes = "Il1O0"
        repeat(2000) {
            val random40 = NtfyTopic.generate(random).value.removePrefix("aegida_")
            assertEquals(40, random40.length)
            assertEquals("found a look-alike in $random40", null, random40.firstOrNull { it in lookAlikes })
        }
    }

    @Test fun aNameAlreadyGivenToAGuardianWithLookAlikesStillWorks() {
        val old = "aegida_" + "Il1O0Il1O0".repeat(4)
        assertEquals("https://ntfy.sh/$old", Endpoint.Ntfy(NtfyTopic(old)).url)
    }
}
