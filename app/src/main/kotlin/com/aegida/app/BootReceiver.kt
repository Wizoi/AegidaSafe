package com.aegida.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart or an update, bring the "Aegida is protecting you" notice back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ProtectionService.start(context)
        }
    }
}
