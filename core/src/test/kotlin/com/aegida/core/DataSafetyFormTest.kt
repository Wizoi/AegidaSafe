package com.aegida.core

import com.aegida.core.net.Endpoint
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The Play Data safety form must match what Aegida sends off the phone (Kevin, Oct 12). This fingerprints every sentence a guardian
 * can receive (with an app name and an address filled in, so a new detail shows up) and the hosts the app may contact. When it
 * changes, re-check the form in Play Console, then copy the new fingerprint into docs/privacy-and-data.md.
 */
class DataSafetyFormTest {
    private fun whatIsSent(): String {
        val notes = Problem.entries.mapNotNull { it.forGuardian("Ruth", "Some App", "example.com") }
        val fixed = Texts.Guardian::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.isAccessible = true; "${it.name}=${it.get(null)}" }
        return (notes + fixed + Endpoint.ALLOWED_HOSTS.map { "host=$it" }).sorted().joinToString("\n")
    }

    @Test fun theDataSafetyFormWasCheckedForWhatAegidaSendsToday() {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(whatIsSent().toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        val recorded = Regex("""Fingerprint of what is sent: `([0-9a-f]{16})`""").find(File("../docs/privacy-and-data.md").readText())?.groupValues?.get(1)
        assertEquals(
            "What Aegida sends off the phone changed. Re-check the Play Data safety form (docs/privacy-and-data.md), then record this fingerprint there.",
            fingerprint,
            recorded,
        )
    }
}
