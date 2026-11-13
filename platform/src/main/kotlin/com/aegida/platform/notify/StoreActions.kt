package com.aegida.platform.notify

import android.content.Intent
import androidx.core.net.toUri
import com.aegida.core.net.PublicPages
import com.aegida.core.text.Texts

/** "Tell a friend" and "Rate Aegida": fixed words and Aegida's own store page, never anything of hers. Shown only once Aegida is on Play. */
object StoreActions {
    /** The phone's share sheet with the fixed "Tell a friend" words: no link, no extras, nothing of hers (T10). */
    fun shareAegida(): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Texts.Settings.SHARE_TEXT),
        Texts.Settings.SHARE_CHOOSER,
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Aegida's page in the Play Store app ([inApp]), or the same page in the browser for a phone without it (T11). */
    fun playStore(inApp: Boolean): Intent {
        val view = Intent(Intent.ACTION_VIEW, (if (inApp) PublicPages.PLAY_MARKET else PublicPages.PLAY_STORE).toUri())
        return (if (inApp) view.setPackage("com.android.vending") else view).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
