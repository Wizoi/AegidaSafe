package com.aegida.core.text

import com.aegida.core.model.Severity

/**
 * Every kind of finding, with the one sentence Mom sees and the one a guardian gets (written in her name,
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
     * pop-ups. A message with a scam link is left in place (it may be from family) and Mom gets a warning.
     */
    val hidesNotification: Boolean = false,
    /** What "Fix my phone" does for it. */
    val fix: Fix = Fix.None,
) {
    ScamLink(
        Severity.NeedsTap,
        "A message has a link that looks like a scam. Please don't tap it.",
        "A message with a scam link arrived on %s's phone. Aegida warned her.",
        whatHappened = "A message had a scam link",
        fix = Fix.Acknowledge,
    ),
    LookalikeLink(
        Severity.NeedsTap,
        "A message has a link pretending to be a company you know. Please don't tap it.",
        "A message pretending to be a well-known company arrived on %s's phone. Aegida warned her.",
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
    MissedCheck(Severity.Info, "Aegida missed its daily check.", null, whatHappened = "Aegida missed its daily check"),
    ListsOld(
        Severity.Info,
        "Aegida couldn't update its scam lists for a few days.",
        null,
        whatHappened = "Aegida's scam lists were out of date",
    ),
    ;

    fun forGuardian(momName: String): String? = guardianTemplate?.format(momName)
}

/** How a problem is put right, so the screens do the same thing for it every time. */
enum class Fix {
    /** Nothing for Mom to do (an Info line). */
    None,

    /** Open the Android screen that fixes it; the next check sees it fixed. */
    Settings,

    /** Open that screen, and count it as seen once she has been there (website pop-ups never come back by themselves). */
    SettingsThenAcknowledge,

    /** Aegida's own step-by-step page for the website filter. */
    WebsiteFilterGuide,

    /** Open the phone dialer with the guardian's number, if she gave one. */
    CallGuardian,

    /** Say what it is and let her mark it seen ("Messages like this are scams. You can delete it."). */
    Acknowledge,
}
