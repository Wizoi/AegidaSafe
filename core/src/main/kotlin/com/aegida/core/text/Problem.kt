package com.aegida.core.text

import com.aegida.core.model.Severity
import com.aegida.core.url.HostMask

/**
 * Every kind of finding, with the one sentence the owner sees and the one a guardian gets (written in her name,
 * coarse, no links, no message text; null = guardians are not told). Severity is decided here once.
 */
enum class Problem(
    val severity: Severity,
    /** The banner and Home sentence: what is wrong and, where there is a fix, what to tap. */
    val forMom: String,
    private val guardianTemplate: String?,
    /** The History line, past tense, with no instruction in it. */
    val whatHappened: String,
    /** What "Fix my phone" does for it. */
    val fix: Fix = Fix.None,
    /**
     * After the owner has seen it, the same warning is not raised again for this long, even if it keeps happening (an app that keeps
     * sending ads as notices would otherwise warn on every notice). Null: it comes back as soon as it is seen again.
     */
    val quietAfterSeen: java.time.Duration? = null,
) {
    NoisyApp(
        Severity.NeedsTap,
        "An app keeps sending you ads as notices. Tap to see which one.",
        "An app keeps showing %1\$s ads as notices. Aegida told %1\$s how to turn them off.",
        whatHappened = "An app sent ads as notices",
        fix = Fix.AppNotices,
        quietAfterSeen = ONE_WEEK,
    ),
    /**
     * Not a warning: the owner told Aegida an app is fine (finding #93). Used only for her History line and for the note to the
     * guardian, so a queued allow note is its own kind and never counts as a NoisyApp note for back-off.
     */
    AppAllowed(Severity.Info, "You told Aegida an app is fine.", null, whatHappened = "You said an app is fine"),
    /** The same for a website she said is fine on a look-alike link warning ("This link is fine", findings #155). */
    SiteAllowed(Severity.Info, "You told Aegida a link is fine.", null, whatHappened = "You said a link is fine"),
    ScamLink(
        Severity.NeedsTap,
        "A message has a link that looks like a scam. Please don't tap it.",
        "A message with a scam link arrived on %1\$s's phone. Aegida warned %1\$s.",
        whatHappened = "A message had a scam link",
        fix = Fix.Acknowledge,
    ),
    LookalikeLink(
        Severity.NeedsTap,
        "A message has a link pretending to be a company you know. Please don't tap it.",
        "A message pretending to be a well-known company arrived on %1\$s's phone. Aegida warned %1\$s.",
        whatHappened = "A message had a link pretending to be a company",
        fix = Fix.Acknowledge,
    ),
    SiteNotifications(
        Severity.NeedsTap,
        "A website was sending you warnings. They are not real. Aegida hid them. Tap to stop that website.",
        "A website was sending fake warnings to %s's phone. Aegida hid them.",
        whatHappened = "A website sent fake warnings",
        fix = Fix.SettingsThenAcknowledge,
    ),
    /**
     * A website she had or said Yes to sent a notice with alarming words (the scare-word gate): hidden, and she is told, but it is
     * not called fake and the guardian is not told; whether it was news or a fake is not guessed (Kevin, Oct 15; findings #148).
     * The name is kept from before, since finding rows in the database store it.
     */
    SiteScaryNews(
        Severity.NeedsTap,
        "A website you allowed sent an alarming message. Aegida hid it. Tap to stop that website.",
        null,
        whatHappened = "A website you allowed sent an alarming message",
        fix = Fix.SettingsThenAcknowledge,
        // A news site may send such a message every day; she is told once a week at most (each one is still hidden).
        quietAfterSeen = ONE_WEEK,
    ),
    NewWebsiteNotifications(
        Severity.NeedsTap,
        "A website you have not used before wants to send you notifications. Was that you?",
        "A website that was not there before started sending notifications on %1\$s's phone. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "A new website started sending notifications",
        fix = Fix.ConfirmEvent,
        // "No" does not stop a site sending; the same question is not asked again for a week (findings #94).
        quietAfterSeen = ONE_WEEK,
    ),
    WebsiteFilterOff(
        Severity.NeedsTap,
        "Website protection is off. Tap to turn it back on.",
        "Website protection was turned off on %s's phone.",
        whatHappened = "Website protection was off",
        fix = Fix.WebsiteFilterGuide,
    ),
    ListenerOff(
        Severity.NeedsTap,
        "Aegida can no longer see your notifications. Tap to fix it.",
        "Aegida can no longer check notifications on %s's phone.",
        whatHappened = "Aegida could not see your notifications",
        fix = Fix.Settings,
    ),
    /**
     * Notification access is on, but the phone has not let Aegida listen for over an hour (finding #112). The fix opens the notification
     * access screen: switching Aegida off and on there makes Android bind it again; opening Aegida alone does not.
     */
    ListenerNotConnected(
        Severity.NeedsTap,
        "Aegida can't see your notices right now. Tap, then turn Aegida off and on again.",
        null,
        whatHappened = "Aegida could not see your notices for a while",
        fix = Fix.Settings,
    ),
    NotificationsBlocked(
        Severity.NeedsTap,
        "Aegida can't show you warnings. Tap to fix it.",
        "Aegida can't show warnings on %s's phone.",
        whatHappened = "Aegida could not show you warnings",
        fix = Fix.Settings,
    ),
    GuardianUnreachable(
        Severity.NeedsTap,
        "Aegida couldn't reach your guardian for a day. Please call them.",
        null,
        whatHappened = "Aegida could not reach your guardian",
        fix = Fix.CallGuardian,
    ),
    BrowserChanged(
        Severity.NeedsTap,
        "The app that opens websites on your phone was changed. Was that you?",
        "The app that opens websites on %1\$s's phone was changed. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "The app that opens websites was changed",
        fix = Fix.ConfirmChange,
    ),
    SmsAppChanged(
        Severity.NeedsTap,
        "The app that handles your text messages was changed. Was that you?",
        "The app for text messages on %1\$s's phone was changed. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "The app that handles text messages was changed",
        fix = Fix.ConfirmChange,
    ),
    DialerChanged(
        Severity.NeedsTap,
        "The app that makes your phone calls was changed. Was that you?",
        "The app for phone calls on %1\$s's phone was changed. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "The app that makes phone calls was changed",
        fix = Fix.ConfirmChange,
    ),
    AssistantChanged(
        Severity.NeedsTap,
        "The app that answers when you ask your phone for help was changed. Was that you?",
        "The assistant app on %1\$s's phone was changed. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "The phone's assistant app was changed",
        fix = Fix.ConfirmChange,
    ),
    ListenerAdded(
        Severity.Urgent,
        "An app can now read your notifications. Was that you?",
        "An app was given access to read notifications on %1\$s's phone. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "An app was given access to read notifications",
        fix = Fix.ConfirmChange,
    ),
    AccessibilityAdded(
        Severity.Urgent,
        "An app can now see and press things on your screen. Was that you?",
        "An app was given control of the screen on %1\$s's phone. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "An app was given control of the screen",
        fix = Fix.ConfirmChange,
    ),
    AdminAdded(
        Severity.Urgent,
        "An app was given special control over your phone. Was that you?",
        "An app was given special control over %1\$s's phone. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "An app was given special control over the phone",
        fix = Fix.ConfirmChange,
    ),
    NewAppOutsidePlay(
        Severity.NeedsTap,
        "A new app was added to your phone from outside the Play Store. Was that you?",
        "A new app from outside the Play Store was added to %1\$s's phone. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "A new app was added from outside the Play Store",
        fix = Fix.ConfirmChange,
    ),
    RemoteControlApp(
        Severity.Urgent,
        "An app that lets someone control your phone from far away is on your phone. Was that you?",
        "An app that lets someone control %1\$s's phone from far away is on it. Aegida asked %1\$s: “Was that you?”",
        whatHappened = "An app that lets someone control the phone from far away was found",
        fix = Fix.ConfirmChange,
    ),
    StalkerwareApp(
        Severity.Urgent,
        "A spying app is on your phone. Tap to see it and remove it.",
        "An app that can secretly spy was found on %1\$s's phone. Aegida warned %1\$s.",
        whatHappened = "An app that can secretly spy was found",
        fix = Fix.Settings,
    ),
    MissedCheck(Severity.Info, "Aegida missed its daily check.", null, whatHappened = "Aegida missed its daily check"),
    ListsOld(
        Severity.Info,
        "Aegida couldn't update its scam lists for a few days.",
        null,
        whatHappened = "Aegida's scam lists were out of date",
    ),
    ;

    /**
     * Aegida hides the notification that caused it. Only website notifications: they are the fake virus
     * pop-ups. A message with a scam link is left in place (it may be from family) and the owner gets a warning.
     */
    val hidesNotification: Boolean get() = this == SiteNotifications || this == SiteScaryNews || this == NewWebsiteNotifications

    /**
     * The guardian is not told when it starts (it has no note of its own), only by the reminder for a warning still waiting after
     * a day: a listener the phone unbound is often bound again within the day (finding #112).
     */
    val remindsGuardianOnly: Boolean get() = this == ListenerNotConnected

    /**
     * What an owner may tell Aegida to leave alone ("This app is fine", "This link is fine"), and the detector whose warnings that quiets;
     * null for everything else. Never an urgent kind (a coached "it is fine" must not silence a spying app), and never a link on the scam
     * list ([ScamLink]): a look-alike is a guess Aegida can get wrong, the scam list is not (findings #155). The one place this is decided:
     * the page shows the button and the action accepts it from here.
     */
    val allowableFrom: com.aegida.core.model.DetectorId?
        get() = when (this) {
            NoisyApp -> com.aegida.core.model.DetectorId.NoisyApp
            LookalikeLink -> com.aegida.core.model.DetectorId.Link
            else -> null
        }

    /**
     * The note for the guardian. For a scam link or a look-alike it also names the app the message came from and a masked
     * web address; for a website that was sending notifications, a masked address of the site. Only those, nothing of the
     * message. [appLabel] is cleaned and cut short because an app picks its own label.
     */
    fun forGuardian(ownerName: String, appLabel: String? = null, host: String? = null): String? {
        val base = guardianTemplate?.format(ownerName) ?: return null
        val app = Texts.Home.guardianLabel(appLabel.orEmpty()).takeIf { it.isNotEmpty() }?.let { "“$it”" }
        val where = host?.takeIf { it.isNotBlank() }?.let { HostMask.of(it) }
        return when (this) {
            ScamLink, LookalikeLink -> {
                val extra = listOfNotNull(app?.let { ", from the $it app" }, where?.let { ", with a link to $it" }).joinToString("")
                base.replace("'s phone.", "'s phone$extra.")
            }
            SiteNotifications, NewWebsiteNotifications -> if (where == null) base else base.replaceFirst("A website ", "A website ($where) ")
            NoisyApp -> if (app == null) base else base.replaceFirst("An app ", "An app ($app) ")
            else -> base
        }
    }

}

private val ONE_WEEK: java.time.Duration = java.time.Duration.ofDays(7)

/** How a problem is put right, so the screens do the same thing for it every time. */
enum class Fix {
    /** Nothing for the owner to do (an Info line). */
    None,

    /** Open the Android screen that fixes it; the next check sees it fixed. */
    Settings,

    /** Open that screen, and count it as seen once she has been there (website pop-ups never come back by themselves). */
    SettingsThenAcknowledge,

    /** Aegida's own step-by-step page for the website filter. */
    WebsiteFilterGuide,

    /** Open the phone dialer with the guardian's number, if she gave one. */
    CallGuardian,

    /** "Was that you?": yes moves the baseline to what the phone has now; no opens the screen to change it back. */
    ConfirmChange,

    /**
     * "Was that you?" about something the phone cannot undo for her (a website that started notifying): yes adds it to the
     * baseline; no opens its switch and counts it as seen. The site may keep sending, so the question then waits a week (findings #94).
     */
    ConfirmEvent,

    /** An app keeps sending ads as notices: its notice settings or its App info page, or leave it alone (finding #92). */
    AppNotices,

    /** Say what it is and let her mark it seen ("Messages like this are scams. You can delete it."). */
    Acknowledge,
}
