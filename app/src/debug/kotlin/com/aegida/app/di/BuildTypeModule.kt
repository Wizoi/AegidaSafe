package com.aegida.app.di

import com.aegida.app.DebugSeeds
import com.aegida.core.model.Snapshotter
import com.aegida.core.time.Clock
import com.aegida.platform.snapshot.AndroidSnapshotter
import com.aegida.platform.time.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug builds only (finding #134): the real clock and snapshot, through [DebugSeeds] so an emulator check can say when the phone
 * started, when the process started and when Aegida was installed. With nothing seeded they pass the real values through.
 */
@Module
@InstallIn(SingletonComponent::class)
object BuildTypeModule {
    @Provides fun clock(real: SystemClock): Clock = DebugSeeds.clock(real)

    @Provides fun snapshotter(real: AndroidSnapshotter): Snapshotter = DebugSeeds.snapshotter(real)
}
