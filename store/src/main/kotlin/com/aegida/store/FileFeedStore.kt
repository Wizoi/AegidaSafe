package com.aegida.store

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedFormatException
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.store.FeedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The feed cache: one file in app storage (9.3 MB, not worth SQL). Written to a temp file and renamed,
 * so a process killed mid-write leaves the last good feed in place (design section 6).
 */
internal class FileFeedStore(private val dir: File) : FeedStore {
    private val file get() = File(dir, "feed.bin")
    private val mutex = Mutex()
    private var cached: ThreatFeed? = null
    private var loaded = false

    override suspend fun current(): ThreatFeed? = mutex.withLock {
        if (!loaded) {
            cached = withContext(Dispatchers.IO) { readOrNull() }
            loaded = true
        }
        cached
    }

    override suspend fun save(verifiedFeed: ByteArray) = mutex.withLock {
        val feed = FeedCodec.decode(verifiedFeed)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val temp = File(dir, "feed.bin.tmp")
            temp.writeBytes(verifiedFeed)
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "could not replace feed file" }
            }
        }
        cached = feed
        loaded = true
    }

    /** A damaged file reads as "no feed" (HealthDetector then says the lists are old), never a crash. */
    private fun readOrNull(): ThreatFeed? = try {
        if (file.isFile) FeedCodec.decode(file.readBytes()) else null
    } catch (expectedDamagedFile: FeedFormatException) {
        null
    }
}
