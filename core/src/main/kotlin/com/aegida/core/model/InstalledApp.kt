package com.aegida.core.model

import java.time.Instant

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
    /** The developer's own category (`ApplicationInfo.category`: game 0, image 3, social 4, news 5, productivity 7 ...), -1 when none. */
    val category: Int = -1,
    /** The app that asked for the install (`InstallSourceInfo`, Android 11+), or null when unknown or older Android. */
    val initiatingInstaller: String? = null,
    /** When it was first installed (`PackageInfo.firstInstallTime`); read for apps that did not ship with the phone, else null. */
    val firstInstalled: Instant? = null,
) {
    val fromPlayStore: Boolean get() = installer == PLAY_STORE

    /** Installed by one of [stores] and also asked for by it, so a preloaded installer that only writes the store's name as installer does not count. */
    fun fromStoreIn(stores: Set<String>): Boolean = installer != null && installer in stores && initiatingInstaller == installer

    /** Where it came from, the one derivation used by NewAppDetector and by every app warning's evidence (#164). [makerStores] is Snapshot.makerStores. */
    fun sourceIn(makerStores: Set<String>): AppSource = when {
        isSystem -> AppSource.CameWithPhone
        fromPlayStore -> AppSource.PlayStore
        fromStoreIn(makerStores) -> AppSource.MakerStore
        else -> AppSource.Outside
    }

    /**
     * The facts about the app it cannot fake (#164): its package, where it came from and when it was first installed (absent
     * when not read). Shown to the owner next to the name the app gives itself.
     */
    fun facts(makerStores: Set<String>): Map<String, String> = buildMap {
        put(Evidence.PACKAGE, packageName)
        put(Evidence.APP_SOURCE, sourceIn(makerStores).evidence)
        firstInstalled?.let { put(Evidence.APP_ADDED, it.toEpochMilli().toString()) }
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"
    }
}

/** Where an app came from, as stored in [Evidence.APP_SOURCE]. The [evidence] values are stored in the database: never rename them. */
enum class AppSource(val evidence: String) {
    PlayStore("play"),

    /** The phone maker's own store (Samsung's Galaxy Store), installed and asked for by it. */
    MakerStore("maker_store"),

    /** Neither store: a file opened by hand, another store, adb. */
    Outside("outside"),
    CameWithPhone("system"),
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
