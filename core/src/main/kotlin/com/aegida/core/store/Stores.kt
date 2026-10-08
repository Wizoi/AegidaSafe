package com.aegida.core.store

import com.aegida.core.feed.ThreatFeed
import com.aegida.core.model.Setup

/** What Mom chose in setup. Implemented by :store. */
interface SetupStore {
    suspend fun setup(): Setup
}

/** The last good, verified feed, or null before the first download. Implemented by :store. */
interface FeedStore {
    suspend fun current(): ThreatFeed?
}
