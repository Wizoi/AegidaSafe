package com.aegida.platform.oem

import android.content.Context
import android.os.Build
import com.aegida.core.oem.OemSource
import com.aegida.core.oem.Reading

/**
 * The only place that knows any phone maker's names (OemSingleOwnerTest). Nothing here is validated
 * on a Samsung yet (design section 8b), so on a Samsung every reading is Unreadable, never guessed.
 *
 * Auto Blocker came with One UI 6.0, so a Samsung from before that (a Galaxy S20+ stops at One UI 5.1) has none and must not be
 * asked about it (findings #91). Its own package is the sign that it exists, and a disabled one is not in Settings (findings #11).
 */
class AndroidOemSource(private val context: Context) : OemSource {

    private val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private val hasAutoBlocker: Boolean
        get() = try {
            context.packageManager.getApplicationInfo(AUTO_BLOCKER, 0).enabled
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }

    override fun installBlocker(): Reading<Boolean> = if (isSamsung && hasAutoBlocker) Reading.Unreadable else Reading.NotPresent

    override fun neverSleeping(): Reading<Boolean> = if (isSamsung) Reading.Unreadable else Reading.NotPresent

    private companion object {
        const val AUTO_BLOCKER = "com.samsung.android.rampart"
    }
}
