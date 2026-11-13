package com.aegida.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.aegida.core.model.WebsiteFilter
import com.aegida.platform.notify.FixActions
import com.aegida.platform.notify.StoreActions

/** Opens a screen of the phone's settings or an email app. False when the phone has nothing for it; nothing is shown to the owner then. */
internal fun Context.startSafely(intent: Intent?): Boolean {
    if (intent == null) return true
    return try {
        startActivity(intent)
        true
    } catch (ignored: ActivityNotFoundException) {
        // A phone without that screen: nothing to open, and the owner is not shown an error she cannot act on.
        false
    }
}

/** Aegida's Play Store page: the Play Store app, else the browser, else nothing (no error she cannot act on). */
internal fun Context.openPlayPage(): Boolean = startSafely(StoreActions.playStore(inApp = true)) || startSafely(StoreActions.playStore(inApp = false))

/** Copies the filter's host name and opens the network settings, where she pastes it in. */
internal fun Context.openNetworkSettingsWithHost() {
    FixActions.copyToClipboard(this, WebsiteFilter.HOST)
    startSafely(FixActions.networkSettings())
}

internal fun Context.openNotificationSettings() {
    startSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}
