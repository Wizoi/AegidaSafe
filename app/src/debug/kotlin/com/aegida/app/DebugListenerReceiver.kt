package com.aegida.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aegida.core.health.ListenerBinding
import com.aegida.core.health.ListenerTimesStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Debug builds only (finding #134). Android on the emulator rebinds the listener within seconds and has no command to unbind it
 * while access stays on, so an emulator check sets the in-process state instead:
 * `adb shell am broadcast -a com.aegida.app.debug.LISTENER_STATE -p com.aegida.app --ez bound false --el lastSeenBound <epoch ms>`.
 * Each extra is optional. A check that runs while not bound does not move "last seen bound" on, so the seed stays.
 */
@AndroidEntryPoint
class DebugListenerReceiver : BroadcastReceiver() {
    @Inject lateinit var binding: ListenerBinding

    @Inject lateinit var times: ListenerTimesStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(BOUND)) binding.markBound(intent.getBooleanExtra(BOUND, true))
        val seenAt = intent.getLongExtra(LAST_SEEN_BOUND, -1)
        if (seenAt < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                times.seenBound(Instant.ofEpochMilli(seenAt))
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BOUND = "bound"
        const val LAST_SEEN_BOUND = "lastSeenBound"
    }
}
