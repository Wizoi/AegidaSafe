package com.aegida.app.di

import com.aegida.core.model.Snapshotter
import com.aegida.core.time.Clock
import com.aegida.platform.snapshot.AndroidSnapshotter
import com.aegida.platform.time.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds: the real clock and the real snapshot, nothing in between. The debug build's copy can seed them (finding #134). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BuildTypeModule {
    @Binds abstract fun clock(impl: SystemClock): Clock

    @Binds abstract fun snapshotter(impl: AndroidSnapshotter): Snapshotter
}
