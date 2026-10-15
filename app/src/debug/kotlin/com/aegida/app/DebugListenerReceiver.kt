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
 * Each extra is optional. A check that runs while not bound does not move "last seen bound" on, so the seed stays. `--el bootedAt`,
 * `--el processStartedAt` and `--el updatedAt` (epoch ms) set [DebugSeeds] for this process, so HealthDetector's hour after a
 * restart or an install, and the two minutes after the process started, can be passed without waiting (finding #134); -1 clears one.
 */
@AndroidEntryPoint
class DebugListenerReceiver : BroadcastReceiver() {
    @Inject lateinit var binding: ListenerBinding

    @Inject lateinit var times: ListenerTimesStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(BOUND)) binding.markBound(intent.getBooleanExtra(BOUND, true))
        seed(intent, BOOTED_AT) { DebugSeeds.bootedAt = it }
        seed(intent, PROCESS_STARTED_AT) { DebugSeeds.processStartedAt = it }
        seed(intent, UPDATED_AT) { DebugSeeds.updatedAt = it }
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

    private fun seed(intent: Intent, extra: String, set: (Instant?) -> Unit) {
        if (!intent.hasExtra(extra)) return
        val at = intent.getLongExtra(extra, -1)
        set(if (at < 0) null else Instant.ofEpochMilli(at))
    }

    private companion object {
        const val BOOTED_AT = "bootedAt"
        const val PROCESS_STARTED_AT = "processStartedAt"
        const val UPDATED_AT = "updatedAt"
        const val BOUND = "bound"
        const val LAST_SEEN_BOUND = "lastSeenBound"
    }
}
