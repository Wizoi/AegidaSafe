package com.aegida.core.net

/**
 * Web pages the owner opens in her own browser (a plain "open this page" tap). Aegida never fetches these itself: it
 * contacts only the hosts in [Endpoint]. The pages are published from docs/site by the feed workflow.
 */
object PublicPages {
    /** What Aegida does and how, in a simple view and a detailed one (docs/site/how-it-works.html). */
    const val HOW_IT_WORKS = "https://wizoi.github.io/Aegida-Feed/how-it-works.html"
}
