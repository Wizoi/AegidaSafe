package com.aegida.platform

import android.content.Intent
import com.aegida.core.net.PublicPages
import com.aegida.platform.notify.FixActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The "How does this work?" tap opens the public page in her browser, and only that page, over https. */
@RunWith(RobolectricTestRunner::class)
class HowItWorksLinkTest {
    @Test fun itOpensTheHowItWorksPageInTheBrowser() {
        val intent = FixActions.howItWorks()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(PublicPages.HOW_IT_WORKS, intent.dataString)
        assertTrue(intent.dataString!!.startsWith("https://"))
    }
}
