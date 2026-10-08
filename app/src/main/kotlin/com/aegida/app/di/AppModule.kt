package com.aegida.app.di

import android.content.Context
import com.aegida.core.health.CheckLog
import com.aegida.core.net.HttpClient
import com.aegida.core.oem.OemSource
import com.aegida.core.time.Clock
import com.aegida.detectors.Detector
import com.aegida.platform.net.OkHttpHttpClient
import com.aegida.platform.notify.NotificationPoster
import com.aegida.platform.oem.AndroidOemSource
import com.aegida.platform.time.SystemClock
import com.aegida.store.AegidaStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/** The composition root: one real implementation per port. Detectors join the set as they are built. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds abstract fun clock(impl: SystemClock): Clock
    @Binds @Singleton abstract fun http(impl: OkHttpHttpClient): HttpClient
    @Binds abstract fun oem(impl: AndroidOemSource): OemSource
    @Multibinds abstract fun detectors(): Set<Detector>

    companion object {
        @Provides @Singleton
        fun store(@ApplicationContext context: Context): AegidaStore = AegidaStore(context)

        @Provides
        fun checkLog(store: AegidaStore): CheckLog = store.checkLog

        @Provides @Singleton
        fun notifications(@ApplicationContext context: Context): NotificationPoster = NotificationPoster(context)
    }
}
