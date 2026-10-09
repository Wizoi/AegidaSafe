package com.aegida.core.model

/** One app on the phone, as the Snapshotter read it. */
data class InstalledApp(
    val packageName: String,
    /** The name the owner sees under the icon. */
    val label: String,
    /** The store that installed it (its package name), or null when nothing recorded one (a file opened by hand, adb). */
    val installer: String?,
    /** Shipped with the phone (updated or not). Not a "new app". */
    val isSystem: Boolean,
    /** SHA-1 of each certificate the app is signed with, lower-case hex. Read for apps that did not ship with the phone. */
    val certSha1s: Set<String> = emptySet(),
) {
    val fromPlayStore: Boolean get() = installer == PLAY_STORE

    companion object {
        const val PLAY_STORE = "com.android.vending"
    }
}

/**
 * Apps whose purpose is to let someone else see and drive the phone from far away. Scammers walk people through
 * installing one. Matched by package name here; the signed feed will carry this list (and signing-certificate
 * hashes, which need a trustworthy source) later.
 */
object RemoteControlApps {
    val PACKAGES: Set<String> = setOf(
        "com.anydesk.anydeskandroid",
        "com.teamviewer.quicksupport.market",
        "com.teamviewer.teamviewer.market.mobile",
        "com.teamviewer.host.market",
        "com.carriez.flutter_hbb",
        "com.sand.airdroid",
        "com.google.chromeremotedesktop",
        "com.splashtop.sos",
    )
}
