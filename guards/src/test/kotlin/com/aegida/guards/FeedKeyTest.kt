package com.aegida.guards

import com.aegida.core.feed.FeedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/** The phone and the publisher must use the same public key, or every feed is rejected as unsigned. */
class FeedKeyTest {
    private val published = File(ShippedSources.root, "feedtool/feed-public-key.txt").readText().trim()

    @Test fun theAppKeyIsTheKeyTheWorkflowVerifiesWith() {
        assertEquals(published, FeedKey.PUBLIC_BASE64)
    }

    @Test fun itIsAPublicKeyNotAPrivateOne() {
        val der = Base64.getDecoder().decode(FeedKey.PUBLIC_BASE64)
        assertTrue("X.509 SubjectPublicKeyInfo for P-256 is 91 bytes, got ${der.size}", der.size == 91)
        FeedKey.verifier() // parses
    }
}
