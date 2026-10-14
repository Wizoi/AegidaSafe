package com.aegida.testkit

import com.aegida.core.model.InstalledApp
import com.aegida.core.model.NotificationEvent
import java.time.Instant

/**
 * Things a test needs, named in Mom's terms and shaped like the real ones. Shapes come from the dumps in build/phone-snapshot (Pixel
 * channels and keys) and build/samsung-rtl (Samsung installer values); the words are made up, never copied from a real message.
 */

/** The Galaxy Store, as `installerPackageName` and `initiatingPackageName` read on the Samsung phone (Temu, two PDF readers). */
const val GALAXY_STORE = "com.sec.android.app.samsungapps"

/** A text message from Google Messages with an address in it, in the shape the phone posts it (category msg). */
fun aScamText(
    at: Instant,
    address: String = "https://evil.example/pay",
    text: String = "USPS: your parcel is held. Pay the fee at $address",
    from: String = "+1 555 0100",
    id: Int = 7,
) = NotificationEvent(
    "0|com.google.android.apps.messaging|$id|null|10123", "com.google.android.apps.messaging", "msg", from, text, at, category = "msg",
)

/** A web page's notification as Chrome posts it: the `p#<site>#<n>` key and the `web:<site>;<id>` channel (emulator, findings #18). */
fun aChromePopup(
    at: Instant,
    site: String = "http://localhost:8765/",
    n: Int = 1,
    title: String = "VIRUS DETECTED! Your phone is infected",
    text: String = "Tap now to clean your device",
) = NotificationEvent(
    key = "0|com.android.chrome|-1|p#$site#$n|10150",
    packageName = "com.android.chrome",
    channelId = "web:${site.trimEnd('/')};13435952006713133",
    title = title,
    text = text,
    postedAt = at,
)

/** An app of the user's, whatever store [installer] and [initiating] name (null when nothing recorded one). */
fun anApp(pkg: String, installer: String? = InstalledApp.PLAY_STORE, initiating: String? = installer, system: Boolean = false) =
    InstalledApp(pkg, pkg.substringAfterLast('.'), installer, system, initiatingInstaller = initiating)

/** Installed from Google Play, as Play records it (installer and the app that asked are both Play). */
fun aPlayApp(pkg: String) = anApp(pkg)

/** Installed from the Galaxy Store, as the Samsung phone read it (installer and initiating are both the store). */
fun aGalaxyStoreApp(pkg: String, initiating: String? = GALAXY_STORE) = anApp(pkg, installer = GALAXY_STORE, initiating = initiating)

/** An app opened from a file or by hand: no installer and no initiator recorded (`installer=null` on the Samsung phone). */
fun anAppOpenedFromAFile(pkg: String) = anApp(pkg, installer = null, initiating = null)

/** Shipped with the phone: no store, marked as a system app. */
fun aSystemApp(pkg: String) = anApp(pkg, installer = null, initiating = null, system = true)
