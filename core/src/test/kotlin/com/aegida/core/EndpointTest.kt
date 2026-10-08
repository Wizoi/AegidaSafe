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
}
