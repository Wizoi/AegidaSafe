package com.aegida.core.net

import com.aegida.core.text.Texts
import java.net.URLEncoder

/**
 * The part after "ntfy://ntfy.sh/" in the invitation's Android tap-to-subscribe link (finding #163).
 *
 * Format from ntfy's own docs (https://docs.ntfy.sh/subscribe/phone/, "Deep links"): `ntfy://<host>/<topic>?display=<name>`,
 * example `ntfy://ntfy.sh/mytopic?display=My+Topic`, "also defines a display name for the topic". The release notes
 * (https://docs.ntfy.sh/releases/) put "Support for port and display name in ntfy:// links" in Android app v1.21.1
 * (Jan 6, 2026); an older app ignores the parameter and still subscribes. The topic itself is never changed.
 * Only the invitation email uses this; nothing parses the link.
 */
object NtfyAppLink {
    /** The topic, plus `?display=<"Name (Aegida)">` encoded as a query value; the topic alone when she gave no name. */
    fun pathFor(topic: NtfyTopic, ownerName: String): String {
        val owner = ownerName.trim()
        if (owner.isEmpty()) return topic.value
        return "${topic.value}?display=" + URLEncoder.encode(Texts.Invite.LABEL.format(owner), "UTF-8")
    }
}
