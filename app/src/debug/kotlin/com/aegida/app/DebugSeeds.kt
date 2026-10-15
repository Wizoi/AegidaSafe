package com.aegida.app

import com.aegida.core.model.Snapshotter
import com.aegida.core.time.Clock
import java.time.Instant

/**
 * Debug builds only (finding #134). Times an emulator check sets through [DebugListenerReceiver], so the "can't see your notices"
 * step can be seen without waiting an hour after the emulator starts and Aegida is installed. Kept in memory for this process.
 * Null: the real value. Nothing reads time here; the real [Clock] does.
 */
object DebugSeeds {
    @Volatile var bootedAt: Instant? = null

    @Volatile var processStartedAt: Instant? = null

    @Volatile var updatedAt: Instant? = null

    fun clock(real: Clock): Clock = object : Clock {
        override fun now() = real.now()
        override fun bootedAt() = DebugSeeds.bootedAt ?: real.bootedAt()
        override fun processStartedAt() = DebugSeeds.processStartedAt ?: real.processStartedAt()
    }

    fun snapshotter(real: Snapshotter) = Snapshotter {
        val snapshot = real.take()
        updatedAt?.let { snapshot.copy(aegidaUpdatedAt = it) } ?: snapshot
    }
}
