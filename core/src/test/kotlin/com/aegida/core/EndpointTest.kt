package com.aegida.core

import com.aegida.core.net.Endpoint
import com.aegida.core.net.NtfyTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    /** FR12: the random part is never shorter or weaker, with no name, a short one or the longest one. */
    @Test fun newPrivateNamesNeverHoldLettersThatLookAlike() {
        val random = java.util.Random(7)
        val lookAlikes = "Il1O0"
        val plain = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        listOf("", "Ruth", "W".repeat(40)).forEach { owner ->
            repeat(700) {
                val random40 = NtfyTopic.generate(owner, random).value.substringAfter("aegida-")
                assertEquals(40, random40.length)
                assertEquals("found a look-alike in $random40", null, random40.firstOrNull { it in lookAlikes })
                assertTrue("only the 57 plain characters: $random40", random40.all { it in plain })
            }
        }
    }

    @Test fun aNameAlreadyGivenToAGuardianWithLookAlikesStillWorks() {
        val old = "aegida_" + "Il1O0Il1O0".repeat(4)
        assertEquals("https://ntfy.sh/$old", Endpoint.Ntfy(NtfyTopic(old)).url)
    }

    /** The name at the start of a new channel name (empty when there is none). */
    private fun nameOf(owner: String) = NtfyTopic.generate(owner, java.util.Random(3)).value.substringBefore("aegida-").removeSuffix("-")

    private fun hasNoName(owner: String) = NtfyTopic.generate(owner, java.util.Random(3)).value.matches(Regex("aegida-[A-Za-z0-9]{40}"))

    @Test fun aNewChannelNameStartsWithTheOwnersFirstName() { // FR8, finding #162
        val made = NtfyTopic.generate("Ruth", java.util.Random(3)).value
        assertTrue(made, made.matches(Regex("Ruth-aegida-[A-Za-z0-9]{40}")))
        assertEquals("Ruth", nameOf("ruth"))
        assertEquals("Ruth", nameOf("Ruth Ann Smith"))
        assertEquals("Ruth", nameOf("  Ruth Smith "))
        assertEquals("an ideographic space ends the word", "Ruth", nameOf("Ruth　Ann"))
        assertEquals("Ruth", nameOf("Ruth\tAnn"))
        assertEquals("a first word with no letters is passed over", "Ruth", nameOf("🌸 Ruth"))
        assertEquals("Ruth", nameOf("- Ruth"))
        assertEquals("only the first letter is raised", "McDonald", nameOf("mcDonald"))
        assertEquals("ANN", nameOf("ANN"))
        assertEquals("raised the same way whatever the phone's language (never a dotted I)", "Ilknur", nameOf("ilknur"))
        assertEquals("Ilknur", nameOf("İlknur"))
    }

    @Test fun accentsAreTakenOffTheOwnersNameInTheChannelName() { // FR9
        assertEquals("Jose", nameOf("José"))
        assertEquals("Zoe", nameOf("Zoë"))
        assertEquals("Lucja", nameOf("Łucja"))
        assertEquals("Soren", nameOf("Søren"))
        assertEquals("Strasse", nameOf("Straße"))
        assertEquals("Aeswin", nameOf("Æswin"))
        assertEquals("Francois", nameOf("François"))
        assertEquals("fullwidth letters", "Ruth", nameOf("Ｒｕｔｈ"))
    }

    @Test fun aNameWithNoPlainLettersGivesAChannelNameWithoutAName() { // FR10
        val ivan = "Иван"
        val wangFang = "王芳"
        val cyrillicU = "у"
        // A letter outside the basic range takes two chars (Adlam, CJK Extension B): still never half a name (findings #163).
        val adlam = String(Character.toChars(0x1E900))
        listOf(ivan, wangFang, "", "   ", "🌸", "1234", "'-'", "R${cyrillicU}th", "Ruth李", "Ruthα", "Ruth$adlam").forEach { owner ->
            assertTrue("'$owner' gave ${NtfyTopic.generate(owner, java.util.Random(3)).value}", hasNoName(owner))
        }
        assertEquals("letters before an emoji are kept", "Ruth", nameOf("Ruth🌸"))
    }

    @Test fun theOwnersNameCanNeverMakeTheChannelNameInvalidForNtfy() { // FR11, must never
        val ntfy = Regex("[A-Za-z0-9_-]{1,64}")
        val hostile = listOf(
            "O'Brien", "Mary-Jane", "Ruth2", "ruth.example.com/x?y#z", "https://evil.example", "a/b?c#d%e,f.g", "Ruth_Ann",
            "W".repeat(40), "Zoë🌸O'Brien-Smith", "\u0000Ruth", "Ruth\t", "José-María", "​Ruth",
        )
        hostile.forEach { owner ->
            val made = NtfyTopic.generate(owner, java.util.Random(3)).value
            assertTrue("'$owner' gave $made", ntfy.matches(made))
            assertEquals("'$owner' gave $made", 40, made.substringAfter("aegida-").length)
        }
        assertEquals("OBrien", nameOf("O'Brien"))
        assertEquals("MaryJane", nameOf("Mary-Jane"))
        assertEquals("digits are left out", "Ruth", nameOf("Ruth2"))
        assertEquals("Ruthexamplec", nameOf("ruth.example.com/x?y#z"))
        assertEquals("RuthAnn", nameOf("Ruth_Ann"))
    }

    @Test fun aLongNameIsCutToTwelveLetters() { // FR11
        assertEquals("W".repeat(12), nameOf("W".repeat(40)))
        assertEquals("Wolfeschlege", nameOf("Wolfeschlegelsteinhausenbergerdorff"))
        assertEquals("twelve letters stay whole", "Abcdefghijkl", nameOf("Abcdefghijkl"))
        assertEquals("Abcdefghijkl", nameOf("Abcdefghijklm"))
        assertEquals("cut after the letters are written plainly", "Thorunnsssss", nameOf("Þórunnßßßß"))
        assertEquals(60, NtfyTopic.generate("W".repeat(40), java.util.Random(3)).value.length)
    }
}
