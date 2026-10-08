package com.aegida.platform.oem

import android.os.Build
import com.aegida.core.oem.OemSource
import com.aegida.core.oem.Reading
import javax.inject.Inject

/**
 * The only place that knows any phone maker's names (OemSingleOwnerTest). Nothing here is validated
 * on a Samsung yet (design section 8b), so on a Samsung every reading is Unreadable, never guessed.
 */
class AndroidOemSource @Inject constructor() : OemSource {

    private val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    override fun installBlocker(): Reading<Boolean> = if (isSamsung) Reading.Unreadable else Reading.NotPresent

    override fun neverSleeping(): Reading<Boolean> = if (isSamsung) Reading.Unreadable else Reading.NotPresent
}
