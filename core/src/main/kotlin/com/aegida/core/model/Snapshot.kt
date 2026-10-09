package com.aegida.core.model

import com.aegida.core.oem.Reading
import java.time.Instant

/**
 * An immutable reading of the phone at one moment. Baseline and current are both made by the same
 * Snapshotter so there is no second reading path. Fields are added as detectors need them.
 */
data class Snapshot(
    val takenAt: Instant,
    val privateDns: PrivateDns,
    /** Package holding the default-browser role, or null if none. */
    val browser: String?,
    /** Aegida's notification listener is enabled (the owner granted notification access). */
    val listenerEnabled: Boolean,
    /** Aegida may post notifications (POST_NOTIFICATIONS granted and not blocked). */
    val canPostNotifications: Boolean,
    val installBlocker: Reading<Boolean>,
    val neverSleeping: Reading<Boolean>,
    /** Package holding the default text-message role, or null. */
    val smsApp: String? = null,
    /** Package holding the default phone-call role, or null. */
    val dialerApp: String? = null,
    /** Packages with notification access switched on, other than Aegida itself. */
    val notificationListeners: Set<String> = emptySet(),
    /** Packages with an accessibility service switched on (an app that can see and press things on the screen). */
    val accessibilityApps: Set<String> = emptySet(),
    /** Packages that are active device administrators. */
    val deviceAdmins: Set<String> = emptySet(),
    /** Every app Aegida may see. Empty when it could not list them. */
    val apps: List<InstalledApp> = emptyList(),
)

/** The Private DNS ("website filter") setting as read from the phone. */
sealed interface PrivateDns {
    /** "Automatic": the key is absent on a phone where it was never set (findings #14), or "opportunistic". */
    data object Automatic : PrivateDns
    data object Off : PrivateDns
    data class Hostname(val host: String) : PrivateDns

    companion object {
        /** From Settings.Global private_dns_mode / private_dns_specifier; null means the key is absent. */
        fun fromSettings(mode: String?, specifier: String?): PrivateDns = when (mode) {
            null, "opportunistic" -> Automatic
            "off" -> Off
            "hostname" -> specifier?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::Hostname) ?: Automatic
            else -> Automatic
        }
    }
}

/** What the owner told Aegida about Samsung Auto Blocker. Never a reading: Aegida cannot read it (findings #10). */
enum class AutoBlockerAnswer { NotAsked, UserSaidOn, NotNow }

/** What the owner chose in setup. Detectors compare the phone against this, never against a guess. */
data class Setup(
    /** The website filter host she set up, or null if that step is not done yet. */
    val websiteFilterHost: String?,
    /** Sites whose notifications she already had when Aegida took its baseline. */
    val allowedSites: Set<String>,
    /** Id of her safety picture (SafetyPicture.id), or null before she picks one. */
    val pictureId: String? = null,
    val autoBlocker: AutoBlockerAnswer = AutoBlockerAnswer.NotAsked,
    /** How many first-run steps she has finished; the next one to show is [SetupStep.entries] at this index. */
    val stepsDone: Int = 0,
) {
    val done: Boolean get() = stepsDone >= SetupStep.entries.size

    companion object {
        val NotDone = Setup(websiteFilterHost = null, allowedSites = emptySet())
    }
}

/** A notification another app posted, as the listener saw it. Text stays on the phone. */
data class NotificationEvent(
    val key: String,
    val packageName: String,
    val channelId: String?,
    val title: String,
    val text: String,
    val postedAt: Instant,
)
