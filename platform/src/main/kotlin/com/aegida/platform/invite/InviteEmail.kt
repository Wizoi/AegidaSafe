package com.aegida.platform.invite

import android.content.Intent
import android.net.Uri
import com.aegida.core.alert.Guardians
import com.aegida.core.text.Texts

/**
 * The email Mom's phone drafts to her guardian, opened in her own email app so she presses Send. Aegida
 * never sends email itself (no Gmail access, decisions.md). It carries the ntfy install links and her
 * private alert link, so the guardian can get her alerts.
 */
object InviteEmail {

    /** Null until Mom has entered an email address and Aegida has made her private topic. */
    fun intent(guardians: Guardians): Intent? {
        val topic = guardians.firstTopic?.value ?: return null
        val email = guardians.firstGuardianEmail.trim().ifEmpty { return null }
        val mom = guardians.momName.ifBlank { Texts.APP_NAME }
        val guardian = guardians.firstGuardianName.ifBlank { "there" }
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))
            .putExtra(Intent.EXTRA_SUBJECT, Texts.Invite.SUBJECT.format(mom))
            .putExtra(Intent.EXTRA_TEXT, Texts.Invite.BODY.format(guardian, topic, topic, mom))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
