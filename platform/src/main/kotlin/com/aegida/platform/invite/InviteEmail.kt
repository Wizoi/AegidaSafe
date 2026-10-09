package com.aegida.platform.invite

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aegida.core.alert.Guardians
import com.aegida.core.text.Texts

/**
 * The email the owner's phone drafts to her guardian, opened in her own email app so she presses Send. Aegida
 * never sends email itself (no Gmail access, decisions.md). It carries the ntfy install links and her
 * private alert link, so the guardian can get her alerts.
 */
object InviteEmail {

    /**
     * Null until the owner has entered an email address and Aegida has made her private topic.
     *
     * Built as a "share" (SEND) with the email, subject and text, not as a `mailto:` SENDTO: Gmail on Android 17
     * leaves the subject and body empty for SENDTO extras (findings #63, seen on a Pixel 11). [mailApps] are the
     * installed apps that take both (see [emailApps]): one is used directly, several are offered to choose from, and
     * none leaves it to the system's share list.
     */
    fun intent(guardians: Guardians, mailApps: List<String> = emptyList()): Intent? {
        val topic = guardians.firstTopic?.value ?: return null
        val email = guardians.firstGuardianEmail.trim().ifEmpty { return null }
        val owner = guardians.ownerName.ifBlank { Texts.APP_NAME }
        val guardian = guardians.firstGuardianName.ifBlank { "there" }
        fun send() = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            .putExtra(Intent.EXTRA_SUBJECT, Texts.Invite.SUBJECT.format(owner))
            .putExtra(Intent.EXTRA_TEXT, Texts.Invite.BODY.format(guardian, topic, topic, owner))
        val intent = when (mailApps.size) {
            0 -> send()
            1 -> send().setPackage(mailApps.single())
            else -> Intent.createChooser(send().setPackage(mailApps.first()), Texts.Invite.CHOOSE)
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, mailApps.drop(1).map { send().setPackage(it) }.toTypedArray())
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Apps that are mail apps (take a mailto:) and also take a share of plain text, which is how the draft is filled in. */
    fun emailApps(context: Context): List<String> {
        val pm = context.packageManager
        val mail = pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0).map { it.activityInfo.packageName }.toSet()
        val share = pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("text/plain"), 0).map { it.activityInfo.packageName }
        return share.filter { it in mail }.distinct()
    }
}
