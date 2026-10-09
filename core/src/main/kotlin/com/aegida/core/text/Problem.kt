package com.aegida.core.text

import com.aegida.core.model.Severity

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
    /**
     * Aegida hides the notification that caused it. Only website notifications: they are the fake virus
     * pop-ups. A message with a scam link is left in place (it may be from family) and the owner gets a warning.
     */
    val hidesNotification: Boolean = false,
    /** What "Fix my phone" does for it. */
    val fix: Fix = Fix.None,
) {
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
        hidesNotification = true,
        fix = Fix.SettingsThenAcknowledge,
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

    fun forGuardian(ownerName: String): String? = guardianTemplate?.format(ownerName)
}

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

    /** Say what it is and let her mark it seen ("Messages like this are scams. You can delete it."). */
    Acknowledge,
}
