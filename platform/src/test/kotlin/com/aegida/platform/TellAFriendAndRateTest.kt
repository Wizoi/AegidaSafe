package com.aegida.platform

import android.content.Intent
import com.aegida.core.net.PublicPages
import com.aegida.core.text.Texts
import com.aegida.platform.notify.StoreActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T10 (Tell a friend) and T11 (Rate on Google Play): fixed words and Aegida's own store page, nothing of hers. */
@RunWith(RobolectricTestRunner::class)
class TellAFriendAndRateTest {
    @Suppress("DEPRECATION")
    private fun inner(chooser: Intent): Intent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!

    @Test fun tellingAFriendOpensTheShareSheetWithTheFixedTextAndNothingElse() { // T10.1, T10.2
        val chooser = StoreActions.shareAegida()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Texts.Settings.SHARE_CHOOSER, chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        val send = inner(chooser)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras!!.keySet())
        assertEquals(
            "I use Aegida on my phone. It looks for scam links and fake warnings and explains them in plain words. " +
                "It's free. Look for Aegida in the Play Store.",
            send.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun theSharedTextHasNoLinkAndNoWordAegidaForbids() { // T10.9, T10.3
        val text = Texts.Settings.SHARE_TEXT
        listOf("http", "://", "www.", ".com", ".org").forEach { assertFalse(it, text.contains(it)) }
        assertFalse(Regex("""(?i)\b(safe|safely|protect|protects|protected|secure|guarantee\w*)\b""").containsMatchIn(text))
    }

    @Test fun rateOpensAegidasOwnPageInThePlayStoreAppThenTheSamePageInTheBrowser() { // T11.1, T11.2, T11.5
        val app = StoreActions.playStore(inApp = true)
        assertEquals(Intent.ACTION_VIEW, app.action)
        assertEquals("market://details?id=com.aegida.app", app.dataString)
        assertEquals("com.android.vending", app.`package`)
        val web = StoreActions.playStore(inApp = false)
        assertEquals(PublicPages.PLAY_STORE, web.dataString)
        assertEquals("https://play.google.com/store/apps/details?id=com.aegida.app", PublicPages.PLAY_STORE)
        assertNull(web.`package`)
        assertTrue(app.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 && web.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun neverSuggestsWhatToWriteInAReview() { // T11.10
        val rate = StoreActions.playStore(inApp = true)
        assertNull(rate.extras)
        assertNull(rate.type)
    }
}
