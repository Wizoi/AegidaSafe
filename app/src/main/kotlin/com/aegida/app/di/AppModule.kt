package com.aegida.app.di

import android.content.ComponentName
import android.content.Context
import com.aegida.app.AegidaListenerService
import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.alert.Outbox
import com.aegida.core.feed.FeedKey
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.health.CheckLog
import com.aegida.core.model.Snapshotter
import com.aegida.core.net.HttpClient
import com.aegida.core.oem.OemSource
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import com.aegida.core.time.Clock
import com.aegida.detectors.DeliveryDetector
import com.aegida.detectors.Detector
import com.aegida.detectors.DnsDetector
import com.aegida.detectors.HealthDetector
import com.aegida.detectors.LinkDetector
import com.aegida.detectors.PopupSpamDetector
import com.aegida.platform.net.OkHttpHttpClient
import com.aegida.platform.notify.NotificationPoster
import com.aegida.platform.oem.AndroidOemSource
import com.aegida.platform.snapshot.AndroidSnapshotter
import com.aegida.platform.time.SystemClock
import com.aegida.store.AegidaStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** The composition root: one real implementation per port, and the detectors that make up a check. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds abstract fun clock(impl: SystemClock): Clock
    @Binds @Singleton abstract fun http(impl: OkHttpHttpClient): HttpClient
    @Binds abstract fun oem(impl: AndroidOemSource): OemSource

    @Binds @IntoSet abstract fun linkDetector(impl: LinkDetector): Detector
    @Binds @IntoSet abstract fun popupSpamDetector(impl: PopupSpamDetector): Detector
    @Binds @IntoSet abstract fun dnsDetector(impl: DnsDetector): Detector
    @Binds @IntoSet abstract fun healthDetector(impl: HealthDetector): Detector
    @Binds @IntoSet abstract fun deliveryDetector(impl: DeliveryDetector): Detector
    @Binds @IntoSet abstract fun driftDetector(impl: com.aegida.detectors.DriftDetector): Detector
    @Binds @IntoSet abstract fun newAppDetector(impl: com.aegida.detectors.NewAppDetector): Detector
    @Binds @IntoSet abstract fun remoteControlDetector(impl: com.aegida.detectors.RemoteControlDetector): Detector
    @Binds @IntoSet abstract fun stalkerwareDetector(impl: com.aegida.detectors.StalkerwareDetector): Detector

    companion object {
        @Provides @Singleton
        fun store(@ApplicationContext context: Context): AegidaStore = AegidaStore(context)

        @Provides fun checkLog(store: AegidaStore): CheckLog = store.checkLog
        @Provides fun findings(store: AegidaStore): FindingStore = store.findings
        @Provides fun history(store: AegidaStore): HistoryLog = store.history
        @Provides fun outbox(store: AegidaStore): Outbox = store.outbox
        @Provides fun setup(store: AegidaStore): SetupStore = store.setup
        @Provides fun guardians(store: AegidaStore): GuardianSettings = store.guardians
        @Provides fun baselines(store: AegidaStore): com.aegida.core.store.BaselineStore = store.baselines
        @Provides fun feed(store: AegidaStore): FeedStore = store.feed

        /** One banner picture for the whole app: the ViewModel updates it, the poster reads it (R5). */
        @Provides @Singleton
        fun pictureBanner(@ApplicationContext context: Context, store: AegidaStore): com.aegida.app.ui.PictureBanner =
            com.aegida.app.ui.PictureBanner(context, store.setup)

        @Provides @Singleton
        fun notifications(@ApplicationContext context: Context, banner: com.aegida.app.ui.PictureBanner): NotificationPoster =
            NotificationPoster(context, banner)

        @Provides fun ownerNotifier(poster: NotificationPoster): OwnerNotifier = poster

        /** One snapshotter, so there is one 5-minute app-list cache, not one per injection (R5). */
        @Provides @Singleton
        fun snapshotter(@ApplicationContext context: Context, clock: Clock, oem: OemSource): Snapshotter =
            AndroidSnapshotter(context, clock, oem, ComponentName(context, AegidaListenerService::class.java))

        @Provides
        fun feedUpdater(http: HttpClient, store: FeedStore, clock: Clock): FeedUpdater =
            FeedUpdater(http, FeedKey.verifier(), store, clock)
    }
}
