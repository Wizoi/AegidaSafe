package com.aegida.platform

import android.content.Intent
import android.provider.Settings
import com.aegida.core.alert.Guardians
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import com.aegida.platform.invite.InviteEmail
import com.aegida.platform.notify.FixActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class InviteAndFixTest {
    private val topic = NtfyTopic.generate(java.util.Random(7))
    private val guardians = Guardians(
        alertsOn = false, ownerName = "Ruth", firstTopic = topic,
        firstGuardianName = "Kevin", firstGuardianEmail = " kevin@example.org ", firstGuardianPhone = "(555) 010-0199",
    )
    private val now = Instant.now()

    private fun finding(problem: Problem, evidence: Map<String, String> = emptyMap()) =
        Finding(FindingKey(DetectorId.Dns, problem, "x"), evidence, now, now)

    // The invitation: an email draft in the owner's own mail app, never sent by Aegida.

    @Test fun theInviteIsAnEmailDraftToTheGuardianWithBothInstallLinksAndHerPrivateLink() {
        val intent = InviteEmail.intent(guardians)!!
        // A "share" with the email, subject and text: Gmail on Android 17 ignores the subject and body of a mailto: SENDTO (findings #63).
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(listOf("kevin@example.org"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList()) // whitespace trimmed
        assertEquals("Ruth has picked you as a guardian", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(body.startsWith("Hi Kevin,"))
        assertTrue("android install link", body.contains("https://play.google.com/store/apps/details?id=io.heckel.ntfy"))
        assertTrue("iphone install link", body.contains("https://apps.apple.com/app/ntfy/id1625396347"))
        assertTrue("her private link", body.contains("https://ntfy.sh/${topic.value}"))
        assertTrue("the topic name to type by hand", body.contains("type this name: ${topic.value}"))
    }

    @Test fun theInviteCannotBeBuiltWithoutAnEmailOrATopic() {
        assertNull(InviteEmail.intent(guardians.copy(firstGuardianEmail = "  ")))
        assertNull(InviteEmail.intent(guardians.copy(firstTopic = null)))
    }

    @Test fun theInviteNeverSaysSafeOrAsksForMoneyOrCodes() {
        val body = InviteEmail.intent(guardians)!!.getStringExtra(Intent.EXTRA_TEXT)!!.lowercase()
        listOf("safe", "secure", "password", "pay", "code").forEach { assertFalse("body mentions '$it'", Regex("\\b$it\\b").containsMatchIn(body)) }
    }

    @Test fun callingAGuardianOpensTheDialerWithTheNumberAndNeedsNoPermission() {
        val intent = FixActions.callGuardian("(555) 010-0199")
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data!!.scheme)
        assertEquals("5550100199", intent.data!!.schemeSpecificPart)
        assertEquals("+15550100199", FixActions.callGuardian("+1 555 010 0199").data!!.schemeSpecificPart)
        assertNull("no number: the plain dialer", FixActions.callGuardian("  ").data)
    }

    // "Fix my phone" and the banner both use these.

    @Test fun eachPermissionProblemOpensItsOwnStandardScreen() {
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, FixActions.settingsIntentFor(finding(Problem.ListenerOff), "com.aegida.app")!!.action)
        val blocked = FixActions.settingsIntentFor(finding(Problem.NotificationsBlocked), "com.aegida.app")!!
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, blocked.action)
        assertEquals("com.aegida.app", blocked.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun aWebsitePopUpOpensThatSitesSwitchOnlyWhenItsChannelIsKnown() {
        val withChannel = finding(Problem.SiteNotifications, mapOf(Evidence.PACKAGE to "com.android.chrome", Evidence.CHANNEL_ID to "web:http://x;1"))
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, FixActions.settingsIntentFor(withChannel, "p")!!.action)
        assertNull(FixActions.settingsIntentFor(finding(Problem.SiteNotifications), "p"))
    }

    @Test fun problemsFixedInsideAegidaHaveNoSettingsScreen() {
        listOf(Problem.WebsiteFilterOff, Problem.ScamLink, Problem.LookalikeLink, Problem.MissedCheck, Problem.ListsOld)
            .forEach { assertNull(it.name, FixActions.settingsIntentFor(finding(it), "p")) }
    }

    @Test fun withOneMailAppTheInviteGoesStraightToItAndWithSeveralTheOwnerChoosesAmongMailAppsOnly() {
        val one = InviteEmail.intent(guardians, listOf("com.mail.app"))!!
        assertEquals("com.mail.app", one.`package`)
        val many = InviteEmail.intent(guardians, listOf("com.mail.app", "org.other.mail"))!!
        assertEquals(Intent.ACTION_CHOOSER, many.action)
        val inner = many.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("com.mail.app", inner.`package`)
        assertEquals(listOf("org.other.mail"), many.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)!!.map { (it as Intent).`package` })
        // with no mail app found it is still a share the owner can finish somewhere
        assertEquals(Intent.ACTION_SEND, InviteEmail.intent(guardians, emptyList())!!.action)
    }

    @Test fun mailAppsAreThoseThatTakeBothAMailtoAndAShareOfText() {
        val pm = org.robolectric.Shadows.shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().packageManager)
        fun resolve(pkg: String) = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = pkg; name = "Compose" }
        }
        val mailto = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
        val share = Intent(Intent.ACTION_SEND).setType("text/plain")
        pm.addResolveInfoForIntent(mailto, resolve("com.mail.app"))
        pm.addResolveInfoForIntent(mailto, resolve("com.mailonly.app"))
        pm.addResolveInfoForIntent(share, resolve("com.mail.app"))
        pm.addResolveInfoForIntent(share, resolve("com.chat.app"))
        assertEquals(listOf("com.mail.app"), InviteEmail.emailApps(androidx.test.core.app.ApplicationProvider.getApplicationContext()))
    }

    @Test fun theWebsiteFilterAddressIsPutOnTheClipboardSoSheCanPasteItInsteadOfTypingIt() { // findings #69
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        FixActions.copyToClipboard(context, "security.cloudflare-dns.com")
        val clip = context.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!
        assertEquals("security.cloudflare-dns.com", clip.getItemAt(0).text.toString())
    }
}
