package com.aegida.core.net

/**
 * Web pages the owner opens in her own browser (a plain "open this page" tap). Aegida never fetches these itself: it
 * contacts only the hosts in [Endpoint]. The pages are published from docs/site by the feed workflow.
 */
object PublicPages {
    /** What Aegida does and how, in a simple view and a detailed one (docs/site/how-it-works.html). */
    const val HOW_IT_WORKS = "https://wizoi.github.io/Aegida-Feed/how-it-works.html"

    private const val PLAY_ID = "com.aegida.app"

    /** Aegida's own page in the Play Store; the one owner of that address (the "Rate" button's fallback). */
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=$PLAY_ID"

    /** The same page in the Play Store app. */
    const val PLAY_MARKET = "market://details?id=$PLAY_ID"

    /**
     * Whether Aegida is public on Play. While it was only in closed testing a friend who was not a tester would get "not found", so My
     * settings showed neither "Tell a friend" nor "Rate". On since Aegida went to production (0.4.4, Nov 24; finding #200).
     */
    const val LISTED_ON_PLAY = true
}
