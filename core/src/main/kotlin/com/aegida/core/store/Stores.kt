package com.aegida.core.store

import com.aegida.core.feed.ThreatFeed
import com.aegida.core.model.Setup

/** What the owner chose in setup. Implemented by :store. */
interface SetupStore {
    suspend fun setup(): Setup
    suspend fun save(setup: Setup)
}

/** The last good, verified feed, or null before the first download. Implemented by :store. */
interface FeedStore {
    suspend fun current(): ThreatFeed?

    /** Replaces the feed with bytes that already passed signature and sanity checks (FeedUpdater). */
    suspend fun save(verifiedFeed: ByteArray)
}
