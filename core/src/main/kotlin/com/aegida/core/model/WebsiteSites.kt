package com.aegida.core.model

/**
 * Chrome gives each website that may notify its own notification channel, "web:<site>;<number>" (findings #18). The one place
 * that reads a site out of a channel id, so the detector and the phone snapshot cannot disagree about what a site is.
 */
object WebsiteSites {
    private const val PREFIX = "web:"

    /** The site in [channelId], or null when it is not a website channel. */
    fun siteOf(channelId: String?): String? =
        channelId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.substringBefore(';')?.takeIf { it.isNotEmpty() }
}
