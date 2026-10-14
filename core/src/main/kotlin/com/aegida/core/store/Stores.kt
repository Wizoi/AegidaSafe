package com.aegida.core.store

import com.aegida.core.feed.ThreatFeed
import com.aegida.core.model.Baseline
import com.aegida.core.model.Setup

/** What the owner chose in setup. Implemented by :store. */
interface SetupStore {
    suspend fun setup(): Setup
    suspend fun save(setup: Setup)
}

/** The last good, verified feed, or null before the first download. Implemented by :store. */
interface FeedStore {
    suspend fun current(): ThreatFeed?

    /** Replaces the feed with bytes that already passed signature and sanity checks (FeedUpdater); [feed] is those bytes, decoded once. */
    suspend fun save(feed: ThreatFeed, verifiedBytes: ByteArray)
}

/** The baseline taken when setup finished, or null before then. Implemented by :store. */
interface BaselineStore {
    suspend fun baseline(): Baseline?
    suspend fun save(baseline: Baseline)
}
