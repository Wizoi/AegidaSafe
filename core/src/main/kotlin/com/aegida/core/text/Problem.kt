package com.aegida.core.text

import com.aegida.core.model.Severity

/**
 * Every kind of finding, with the one sentence Mom sees and the one a guardian gets (written in her name,
 * coarse, no links, no message text; null = guardians are not told). Severity is decided here once.
 */
enum class Problem(val severity: Severity, val forMom: String, private val guardianTemplate: String?) {
    ScamLink(
        Severity.NeedsTap,
        "A message has a link that looks like a scam. Please don't tap it.",
        "A message with a scam link arrived on %s's phone. Aegida warned her.",
    ),
    LookalikeLink(
        Severity.NeedsTap,
        "A message has a link pretending to be a company you know. Please don't tap it.",
        "A message pretending to be a well-known company arrived on %s's phone. Aegida warned her.",
    ),
    SiteNotifications(
        Severity.NeedsTap,
        "A website was sending you warnings. They are not real. Aegida hid them. Tap to stop that website.",
        "A website was sending fake warnings to %s's phone. Aegida hid them.",
    ),
    WebsiteFilterOff(
        Severity.NeedsTap,
        "Website protection is off. Tap to turn it back on.",
        "Website protection was turned off on %s's phone.",
    ),
    ListenerOff(
        Severity.NeedsTap,
        "Aegida can no longer see your notifications. Tap to fix it.",
        "Aegida can no longer check notifications on %s's phone.",
    ),
    NotificationsBlocked(
        Severity.NeedsTap,
        "Aegida can't show you warnings. Tap to fix it.",
        "Aegida can't show warnings on %s's phone.",
    ),
    MissedCheck(Severity.Info, "Aegida missed its daily check.", null),
    ListsOld(Severity.Info, "Aegida couldn't update its scam lists for a few days.", null),
    ;

    fun forGuardian(momName: String): String? = guardianTemplate?.format(momName)
}
